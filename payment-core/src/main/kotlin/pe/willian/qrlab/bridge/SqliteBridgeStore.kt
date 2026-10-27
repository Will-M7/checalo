package pe.willian.qrlab.bridge

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * Bridge store SQLite (JDBC sencillo, sin ORM).
 *
 * Comparte el archivo DB con [pe.willian.qrlab.payment.SqlitePaymentRepository]
 * (conexión propia, misma ruta). Esquema idempotente, sin borrar datos:
 * - bridge_event_id UNIQUE y raw_fingerprint UNIQUE garantizan que una
 *   notificación reenviada (incluso tras reinicios) se procese una sola vez;
 * - NUNCA se persiste el contenido raw completo ni el código en plano,
 *   solo amount, payerName auxiliar y el HMAC del código.
 */
class SqliteBridgeStore(dbPath: Path) : BridgeStore, AutoCloseable {

    private val connection: Connection

    init {
        dbPath.parent?.let { Files.createDirectories(it) }
        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS incoming_notifications (
                    id TEXT PRIMARY KEY,
                    bridge_event_id TEXT UNIQUE NOT NULL,
                    source_package TEXT NOT NULL,
                    notification_key TEXT,
                    posted_at TEXT NOT NULL,
                    received_at TEXT NOT NULL,
                    amount_minor INTEGER,
                    payer_name TEXT,
                    security_code_hmac TEXT,
                    match_status TEXT NOT NULL,
                    matched_payment_id TEXT,
                    raw_fingerprint TEXT UNIQUE NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_incoming_status ON incoming_notifications(match_status)",
            )
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_incoming_amount ON incoming_notifications(amount_minor)",
            )
        }
    }

    @Synchronized
    override fun save(record: IncomingNotificationRecord): IncomingNotificationRecord {
        connection.prepareStatement(
            """
            INSERT INTO incoming_notifications
                (id, bridge_event_id, source_package, notification_key, posted_at, received_at,
                 amount_minor, payer_name, security_code_hmac, match_status, matched_payment_id, raw_fingerprint)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                bridge_event_id = excluded.bridge_event_id,
                source_package = excluded.source_package,
                notification_key = excluded.notification_key,
                posted_at = excluded.posted_at,
                received_at = excluded.received_at,
                amount_minor = excluded.amount_minor,
                payer_name = excluded.payer_name,
                security_code_hmac = excluded.security_code_hmac,
                match_status = excluded.match_status,
                matched_payment_id = excluded.matched_payment_id,
                raw_fingerprint = excluded.raw_fingerprint
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, record.id)
            statement.setString(2, record.bridgeEventId)
            statement.setString(3, record.sourcePackage)
            statement.setString(4, record.notificationKey)
            statement.setString(5, record.postedAt.toString())
            statement.setString(6, record.receivedAt.toString())
            record.amountMinor?.let { statement.setLong(7, it) } ?: statement.setNull(7, java.sql.Types.BIGINT)
            statement.setString(8, record.payerName)
            statement.setString(9, record.securityCodeHmac)
            statement.setString(10, record.matchStatus.name)
            statement.setString(11, record.matchedPaymentId)
            statement.setString(12, record.rawFingerprint)
            statement.executeUpdate()
        }
        return record
    }

    @Synchronized
    override fun findById(id: String): IncomingNotificationRecord? =
        queryOne("SELECT * FROM incoming_notifications WHERE id = ?", id)

    @Synchronized
    override fun findByBridgeEventId(bridgeEventId: String): IncomingNotificationRecord? =
        queryOne("SELECT * FROM incoming_notifications WHERE bridge_event_id = ?", bridgeEventId)

    @Synchronized
    override fun findByFingerprint(fingerprint: String): IncomingNotificationRecord? =
        queryOne("SELECT * FROM incoming_notifications WHERE raw_fingerprint = ?", fingerprint)

    @Synchronized
    override fun findByStatus(status: MatchStatus): List<IncomingNotificationRecord> {
        connection.prepareStatement("SELECT * FROM incoming_notifications WHERE match_status = ?").use { statement ->
            statement.setString(1, status.name)
            statement.executeQuery().use { rows ->
                return buildList {
                    while (rows.next()) add(rowToRecord(rows))
                }
            }
        }
    }

    @Synchronized
    override fun findAll(): List<IncomingNotificationRecord> {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM incoming_notifications").use { rows ->
                return buildList {
                    while (rows.next()) add(rowToRecord(rows))
                }
            }
        }
    }

    @Synchronized
    override fun count(): Long {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM incoming_notifications").use { rows ->
                rows.next()
                return rows.getLong(1)
            }
        }
    }

    override fun close() {
        connection.close()
    }

    private fun queryOne(sql: String, param: String): IncomingNotificationRecord? {
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, param)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                return rowToRecord(rows)
            }
        }
    }

    private fun rowToRecord(rows: java.sql.ResultSet): IncomingNotificationRecord {
        val amount = rows.getLong("amount_minor")
        return IncomingNotificationRecord(
            id = rows.getString("id"),
            bridgeEventId = rows.getString("bridge_event_id"),
            sourcePackage = rows.getString("source_package"),
            notificationKey = rows.getString("notification_key"),
            postedAt = Instant.parse(rows.getString("posted_at")),
            receivedAt = Instant.parse(rows.getString("received_at")),
            amountMinor = if (rows.wasNull()) null else amount,
            payerName = rows.getString("payer_name"),
            securityCodeHmac = rows.getString("security_code_hmac"),
            matchStatus = MatchStatus.valueOf(rows.getString("match_status")),
            matchedPaymentId = rows.getString("matched_payment_id"),
            rawFingerprint = rows.getString("raw_fingerprint"),
        )
    }
}
