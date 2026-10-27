package pe.willian.qrlab.server

import kotlinx.serialization.Serializable
import pe.willian.qrlab.payment.SqlitePaymentRepository
import java.sql.Connection
import java.time.Instant

@Serializable
data class ReceiverCommand(
    val operationId: String, val expectedVersion: Long, val actor: String,
    val action: String, val name: String, val phone: String, val qrSha256: String,
    val physicalConfirmed: Boolean = false,
)
class ReceiverAdminDenied : RuntimeException()
class ReceiverAdminConflict(val reason: String) : RuntimeException()

/** Only the explicitly scoped server client may delegate a checked Laravel administrator.
 * Actor is an audit identity, never an authentication credential. Device clients cannot delegate.
 */
class ReceiverAdministration(private val repo: SqlitePaymentRepository, private val config: CommercialConfig,
                             private val clock: () -> Instant, private val registry: CommercialRegistry) {
    init {
        repo.atomic { db ->
            db.exec("CREATE TABLE IF NOT EXISTS canonical_receiver_state(receiver_id TEXT PRIMARY KEY, identity_hash TEXT NOT NULL, verified INTEGER NOT NULL, enabled INTEGER NOT NULL, version INTEGER NOT NULL, verified_by TEXT, verified_at TEXT)")
            db.exec("CREATE TABLE IF NOT EXISTS canonical_receiver_audit(operation_id TEXT PRIMARY KEY, receiver_id TEXT NOT NULL, client_id TEXT NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, physical_confirmed INTEGER NOT NULL, happened_at TEXT NOT NULL, before_version INTEGER NOT NULL, after_version INTEGER NOT NULL, before_verified INTEGER NOT NULL, after_verified INTEGER NOT NULL, before_enabled INTEGER NOT NULL, after_enabled INTEGER NOT NULL, request_hash TEXT NOT NULL, identity_hash TEXT NOT NULL, qr_sha256 TEXT NOT NULL)")
            registry.receivers(db).forEach { r ->
                val identity = identity(r)
                val state = state(db,r.id)
                if (state == null) {
                    // Preserve explicitly provisioned legacy installations; new config defaults stay closed.
                    db.exec("INSERT INTO canonical_receiver_state VALUES(?,?,?,?,0,NULL,NULL)",r.id,identity,if(r.verified) 1 else 0,if(r.verified && r.enabled) 1 else 0)
                } else if (state["identity_hash"] != identity) {
                    val version=state["version"]!!.toLong()
                    db.exec("UPDATE canonical_receiver_state SET identity_hash=?,verified=0,enabled=0,version=version+1,verified_by=NULL,verified_at=NULL WHERE receiver_id=?",identity,r.id)
                    audit(db,"config:${r.id}:${version+1}",r.id,"system","configuration-loader","IDENTITY_CHANGED",false,version,version+1,state["verified"]!!, "0",state["enabled"]!!,"0",identity)
                }
            }
        }
    }
    fun state(db: Connection,id: String) = db.rows("SELECT * FROM canonical_receiver_state WHERE receiver_id=?",id).singleOrNull()
    fun online(db: Connection,id: String): Boolean = registry.devices(db).filter { it.receiverId==id && registry.enabled(db,it.id) }.any { device ->
        db.rows("SELECT seen,listener,receiver_id FROM canonical_devices WHERE id=?",device.id).any {
            it["receiver_id"]==id && it["listener"]=="1" && clock().epochSecond-it["seen"]!!.toLong() in 0..120
        }
    }
    fun view(db: Connection,r: PaymentReceiver): ReceiverView {
        val s=state(db,r.id)!!
        return ReceiverView(r.id,r.name,r.phone,r.qrSha256,s["identity_hash"]==identity(r) && s["verified"]=="1",s["identity_hash"]==identity(r) && s["enabled"]=="1",online(db,r.id),s["version"]!!.toLong(),registry.devices(db).any { it.receiverId==r.id },s["verified_by"],s["verified_at"])
    }
    fun execute(client: PaymentClient,id: String,c: ReceiverCommand): ReceiverView {
        if (!client.receiverAdministration || config.client(client.key) != client) throw ReceiverAdminDenied()
        require(c.operationId.matches(Regex("[a-zA-Z0-9_-]{16,100}")) && c.actor.matches(Regex("staff_[1-9][0-9]{0,18}")))
        require(c.action in listOf("verify","enable","disable") && c.expectedVersion >= 0)
        val digest=CanonicalPayments.sha256(kotlinx.serialization.json.Json.encodeToString(c))
        return repo.atomic { db ->
            if(!registry.allowed(db,client,id)) throw ReceiverAdminDenied()
            val r=registry.receiver(db,id)
            val previous=db.rows("SELECT request_hash,client_id,receiver_id FROM canonical_receiver_audit WHERE operation_id=?",c.operationId).singleOrNull()
            if (previous!=null) {
                if(previous["request_hash"]!=digest || previous["client_id"]!=client.id || previous["receiver_id"]!=id) throw ReceiverAdminConflict("OPERATION_CONFLICT")
                // Return current state, never an obsolete enabled response after a later disable.
                return@atomic view(db,r)
            }
            val before=state(db,id)!!
            if(before["identity_hash"]!=identity(r)) throw ReceiverAdminConflict("IDENTITY_CONFIG_CHANGED")
            val version=before["version"]!!.toLong()
            if(version!=c.expectedVersion) throw ReceiverAdminConflict("VERSION_CONFLICT")
            // Disabling is always possible, including after a mistaken local QR edit.
            if(c.action!="disable") {
                if(c.name!=r.name) throw ReceiverAdminConflict("NAME_MISMATCH")
                if(normalizePhone(c.phone)!=normalizePhone(r.phone)) throw ReceiverAdminConflict("PHONE_MISMATCH")
                if(c.qrSha256!=r.qrSha256) throw ReceiverAdminConflict("QR_MISMATCH")
            }
            var verified=before["verified"]=="1"
            var enabled=before["enabled"]=="1"
            when(c.action) {
                "verify" -> {
                    if(!c.physicalConfirmed) throw ReceiverAdminConflict("PHYSICAL_CONFIRMATION_REQUIRED")
                    verified=true; enabled=false
                }
                "enable" -> {
                    if(!verified) throw ReceiverAdminConflict("PHYSICAL_CONFIRMATION_REQUIRED")
                    if(!registry.devices(db).any { it.receiverId==id }) throw ReceiverAdminConflict("DEVICE_NOT_LINKED")
                    if(!online(db,id)) throw ReceiverAdminConflict("BRIDGE_OFFLINE")
                    enabled=true
                }
                "disable" -> enabled=false
            }
            db.exec("UPDATE canonical_receiver_state SET verified=?,enabled=?,version=version+1,verified_by=CASE WHEN ? THEN ? ELSE verified_by END,verified_at=CASE WHEN ? THEN ? ELSE verified_at END WHERE receiver_id=?",
                if(verified) 1 else 0,if(enabled) 1 else 0,c.action=="verify","${client.id}:${c.actor}",c.action=="verify",clock().toString(),id)
            audit(db,c.operationId,id,client.id,c.actor,c.action,c.physicalConfirmed,version,version+1,before["verified"]!!,if(verified) "1" else "0",before["enabled"]!!,if(enabled) "1" else "0",digest)
            view(db,r)
        }
    }
    private fun audit(db: Connection,op:String,id:String,client:String,actor:String,action:String,physical:Boolean,before:Long,after:Long,bv:String,av:String,be:String,ae:String,digest:String) {
        db.exec("INSERT INTO canonical_receiver_audit VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",op,id,client,actor,action,if(physical) 1 else 0,clock().toString(),before,after,bv,av,be,ae,digest,identity(registry.receiver(db,id)),registry.receiver(db,id).qrSha256)
    }
    private fun identity(r: PaymentReceiver)=CanonicalPayments.sha256("${r.name}|${normalizePhone(r.phone)}|${r.qrSha256}")
    private fun normalizePhone(phone:String):String {
        val digits=phone.filter(Char::isDigit)
        require(digits.matches(Regex("(?:51)?9[0-9]{8}")))
        return "51"+digits.takeLast(9)
    }
}
