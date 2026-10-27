package pe.willian.yapebridge.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.bridge.CanonicalProtocol
import pe.willian.yapebridge.bridge.DeviceCredentials
import org.json.JSONObject

class OutboxDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "yape_bridge_outbox.db", null, 3
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE bridge_outbox (
              event_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, package_name TEXT NOT NULL,
              posted_at INTEGER NOT NULL, title TEXT, text TEXT, big_text TEXT, sub_text TEXT,
              summary_text TEXT, attempt_count INTEGER NOT NULL DEFAULT 0, last_attempt_at INTEGER,
              status TEXT NOT NULL DEFAULT 'PENDING', security_code TEXT, receiver_id TEXT NOT NULL DEFAULT '', encrypted INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX outbox_pending_fifo ON bridge_outbox(status, created_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE bridge_outbox ADD COLUMN security_code TEXT")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE bridge_outbox ADD COLUMN receiver_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE bridge_outbox ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0")
            // Quarantine and encrypt historical LAB payloads without assigning a commercial receiver.
            db.rawQuery("SELECT * FROM bridge_outbox",null).use { cursor ->
                while(cursor.moveToNext()) {
                    val payload=JSONObject()
                    for(field in listOf("title","text","big_text","sub_text","summary_text","security_code")) {
                        val value=cursor.getString(cursor.getColumnIndexOrThrow(field))
                        val key=when(field) { "big_text" -> "bigText";"sub_text" -> "subText";"summary_text" -> "summaryText";"security_code" -> "securityCode";else -> field }
                        payload.put(key,value ?: JSONObject.NULL)
                    }
                    db.execSQL("UPDATE bridge_outbox SET status='LEGACY_REVIEW',text=?,title=NULL,big_text=NULL,sub_text=NULL,summary_text=NULL,security_code=NULL,encrypted=1 WHERE event_id=?",
                        arrayOf(DeviceCredentials.seal(payload.toString()),cursor.getString(cursor.getColumnIndexOrThrow("event_id"))))
                }
            }
        }
    }

    @Synchronized fun insert(notification: CapturedNotification): BridgeOutboxEvent {
        val receiver=DeviceCredentials.receiverId
        check(receiver.isNotBlank())
        val event = BridgeOutboxEvent(
            eventId = CanonicalProtocol.eventId(receiver, notification), createdAt = System.currentTimeMillis(),
            packageName = notification.packageName, postedAt = notification.postedAt,
            title = notification.title, text = notification.text, bigText = notification.bigText,
            subText = notification.subText, summaryText = notification.summaryText,
            receiverId = receiver,
            securityCode = notification.securityCode,
        )
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO bridge_outbox(event_id,created_at,package_name,posted_at,title,text,big_text,sub_text,summary_text,status,security_code,receiver_id,encrypted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,1)",
            arrayOf(event.eventId, event.createdAt, event.packageName, event.postedAt, null, DeviceCredentials.seal(CanonicalProtocol.json(event)), null, null, null, event.status.name, null, event.receiverId),
        )
        return query("SELECT * FROM bridge_outbox WHERE event_id='${event.eventId}'").single()
    }

    @Synchronized fun recentSummaries():List<CapturedNotification> {
        readableDatabase.rawQuery("SELECT event_id,posted_at,package_name,status FROM bridge_outbox WHERE receiver_id=? AND package_name=? ORDER BY posted_at DESC LIMIT 30",arrayOf(DeviceCredentials.receiverId,CanonicalProtocol.EXPECTED_PACKAGE)).use { c ->
            return buildList { while(c.moveToNext())add(CapturedNotification(c.getString(2),c.getString(0),c.getLong(1),"Notificación Yape conservada",c.getString(3),null,null,null)) }
        }
    }
    @Synchronized fun counts(): Pair<Int, Int> {
        readableDatabase.rawQuery("SELECT SUM(CASE WHEN status='PENDING' AND receiver_id=? THEN 1 ELSE 0 END), SUM(CASE WHEN status IN ('LEGACY_REVIEW','REMOTE_REVIEW') OR (status='PENDING' AND receiver_id!=?) THEN 1 ELSE 0 END) FROM bridge_outbox", arrayOf(DeviceCredentials.receiverId, DeviceCredentials.receiverId)).use { c ->
            c.moveToFirst()
            return c.getInt(0) to c.getInt(1)
        }
    }

    @Synchronized fun pending(): List<BridgeOutboxEvent> = query("SELECT * FROM bridge_outbox WHERE status='PENDING' ORDER BY created_at, event_id").filter { it.receiverId == DeviceCredentials.receiverId }

    @Synchronized fun markAttempt(eventId: String, at: Long = System.currentTimeMillis()) {
        writableDatabase.execSQL("UPDATE bridge_outbox SET attempt_count=attempt_count+1,last_attempt_at=? WHERE event_id=?", arrayOf(at, eventId))
    }

    @Synchronized fun markReview(eventId:String) {
        writableDatabase.execSQL("UPDATE bridge_outbox SET status='REMOTE_REVIEW' WHERE event_id=?",arrayOf(eventId))
    }
    @Synchronized fun markSent(eventId: String) {
        writableDatabase.execSQL("UPDATE bridge_outbox SET status='SENT',text=NULL,title=NULL,big_text=NULL,sub_text=NULL,summary_text=NULL,security_code=NULL WHERE event_id=?", arrayOf(eventId))
    }

    private fun query(sql: String): List<BridgeOutboxEvent> {
        readableDatabase.rawQuery(sql, null).use { c ->
            val result = mutableListOf<BridgeOutboxEvent>()
            while (c.moveToNext()) {
                val encrypted = c.getInt(c.getColumnIndexOrThrow("encrypted")) == 1
                val body = c.getString(c.getColumnIndexOrThrow("text"))
                val payload = if (encrypted && body != null) JSONObject(DeviceCredentials.open(body)) else JSONObject()
                fun field(name:String):String? = if (payload.isNull(name)) null else payload.optString(name).takeIf { it.isNotEmpty() }
                result += BridgeOutboxEvent(
                receiverId = c.getString(c.getColumnIndexOrThrow("receiver_id")),
                eventId = c.getString(c.getColumnIndexOrThrow("event_id")),
                createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                packageName = c.getString(c.getColumnIndexOrThrow("package_name")),
                postedAt = c.getLong(c.getColumnIndexOrThrow("posted_at")),
                title = field("title"), text = field("text"),
                bigText = field("bigText"), subText = field("subText"),
                summaryText = field("summaryText"), attemptCount = c.getInt(c.getColumnIndexOrThrow("attempt_count")),
                securityCode = field("securityCode"),
                lastAttemptAt = c.getLong(c.getColumnIndexOrThrow("last_attempt_at")).takeUnless { c.isNull(c.getColumnIndexOrThrow("last_attempt_at")) },
                status = BridgeOutboxEvent.Status.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
            )
            }
            return result
        }
    }

    companion object {
        @Volatile private var instance: OutboxDatabase? = null
        fun get(context: Context): OutboxDatabase = instance ?: synchronized(this) {
            instance ?: OutboxDatabase(context).also { instance = it }
        }
    }
}
