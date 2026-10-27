package pe.willian.qrlab.yape

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.time.Instant

/**
 * Store SQLite del MODELO 2 (JDBC sencillo, sin ORM).
 *
 * Comparte el archivo DB con el resto del laboratorio (conexión propia,
 * misma ruta). Esquema idempotente, sin borrar datos.
 *
 * PRIVACIDAD (obligatoria):
 * - NUNCA se escribe raw text de la notificación (no hay columna para eso);
 * - NUNCA se escribe el código de seguridad en plano: solo su HMAC;
 * - tablas con prefijo yape_ para no chocar con incoming_notifications
 *   del bridge (paquete bridge intacto).
 *
 * Un reinicio conserva ventas y eventos ambiguos: la resolución posterior
 * sigue funcionando contra las mismas filas.
 */
class SqliteYapeLabStore(dbPath: Path) : YapeLabStore, AutoCloseable {

    private val connection: Connection

    init {
        dbPath.parent?.let { Files.createDirectories(it) }
        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS yape_waiting_payments (
                    id TEXT PRIMARY KEY,
                    reference TEXT,
                    amount_minor INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    status TEXT NOT NULL,
                    received_at TEXT,
                    source_package TEXT,
                    matched_notification_id TEXT
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS yape_incoming_notifications (
                    id TEXT PRIMARY KEY,
                    bridge_event_id TEXT UNIQUE,
                    amount_minor INTEGER NOT NULL,
                    payer_name TEXT,
                    security_code_hmac TEXT,
                    received_at TEXT NOT NULL,
                    source_package TEXT NOT NULL,
                    source_type TEXT NOT NULL,
                    match_status TEXT NOT NULL,
                    matched_payment_id TEXT
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_yape_waits_status ON yape_waiting_payments(status)",
            )
            val columns = buildSet {
                statement.executeQuery("PRAGMA table_info(yape_waiting_payments)").use { rows ->
                    while (rows.next()) add(rows.getString("name"))
                }
            }
            if ("expires_at" !in columns) {
                statement.executeUpdate("ALTER TABLE yape_waiting_payments ADD COLUMN expires_at TEXT")
                statement.executeUpdate("UPDATE yape_waiting_payments SET expires_at = strftime('%Y-%m-%dT%H:%M:%fZ', created_at, '+5 minutes') WHERE expires_at IS NULL")
            }
            val notificationColumns = buildSet {
                statement.executeQuery("PRAGMA table_info(yape_incoming_notifications)").use { rows ->
                    while (rows.next()) add(rows.getString("name"))
                }
            }
            if ("bridge_event_id" !in notificationColumns) {
                statement.executeUpdate("ALTER TABLE yape_incoming_notifications ADD COLUMN bridge_event_id TEXT")
                statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_yape_notification_bridge_event ON yape_incoming_notifications(bridge_event_id) WHERE bridge_event_id IS NOT NULL")
            }
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_yape_notif_status ON yape_incoming_notifications(match_status)",
            )
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_yape_notif_amount ON yape_incoming_notifications(amount_minor)",
            )
        }
    }

    @Synchronized
    override fun saveWait(wait: YapeWaitingPayment) {
        connection.prepareStatement(
            """
            INSERT INTO yape_waiting_payments
                (id, reference, amount_minor, created_at, expires_at, status, received_at, source_package, matched_notification_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                reference = excluded.reference,
                amount_minor = excluded.amount_minor,
                created_at = excluded.created_at,
                expires_at = excluded.expires_at,
                status = excluded.status,
                received_at = excluded.received_at,
                source_package = excluded.source_package,
                matched_notification_id = excluded.matched_notification_id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, wait.id)
            statement.setString(2, wait.reference)
            statement.setLong(3, wait.amountMinor)
            statement.setString(4, wait.createdAt.toString())
            statement.setString(5, wait.expiresAt.toString())
            statement.setString(6, wait.status.name)
            wait.receivedAt?.let { statement.setString(7, it.toString()) } ?: statement.setNull(7, Types.TIMESTAMP)
            statement.setString(8, wait.sourcePackage)
            statement.setString(9, wait.matchedNotificationId)
            statement.executeUpdate()
        }
    }

    @Synchronized
    override fun findAllWaits(): List<YapeWaitingPayment> {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM yape_waiting_payments ORDER BY id").use { rows ->
                return buildList {
                    while (rows.next()) {
                        val receivedAt = rows.getString("received_at")
                        add(
                            YapeWaitingPayment(
                                id = rows.getString("id"),
                                reference = rows.getString("reference"),
                                amountMinor = rows.getLong("amount_minor"),
                                createdAt = Instant.parse(rows.getString("created_at")),
                                expiresAt = rows.getString("expires_at")?.let { Instant.parse(it) }
                                    ?: Instant.parse(rows.getString("created_at")).plusSeconds(300),
                                status = YapeWaitStatus.valueOf(rows.getString("status")),
                                receivedAt = receivedAt?.let { Instant.parse(it) },
                                sourcePackage = rows.getString("source_package"),
                                matchedNotificationId = rows.getString("matched_notification_id"),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    override fun saveNotification(notification: IncomingPaymentNotification) {
        connection.prepareStatement(
            """
            INSERT INTO yape_incoming_notifications
                (id, bridge_event_id, amount_minor, payer_name, security_code_hmac, received_at,
                 source_package, source_type, match_status, matched_payment_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                amount_minor = excluded.amount_minor,
                payer_name = excluded.payer_name,
                security_code_hmac = excluded.security_code_hmac,
                received_at = excluded.received_at,
                source_package = excluded.source_package,
                source_type = excluded.source_type,
                match_status = excluded.match_status,
                matched_payment_id = excluded.matched_payment_id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, notification.id)
            statement.setString(2, notification.bridgeEventId)
            statement.setLong(3, notification.amountMinor)
            statement.setString(4, notification.payerName)
            // Solo HMAC: el código en plano NUNCA se escribe en SQLite.
            statement.setString(5, notification.securityCodeHmac)
            statement.setString(6, notification.receivedAt.toString())
            statement.setString(7, notification.sourcePackage)
            statement.setString(8, notification.sourceType.name)
            statement.setString(9, notification.matchStatus.name)
            statement.setString(10, notification.matchedPaymentId)
            statement.executeUpdate()
        }
    }

    @Synchronized
    override fun findAllNotifications(): List<IncomingPaymentNotification> {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM yape_incoming_notifications ORDER BY id").use { rows ->
                return buildList {
                    while (rows.next()) {
                        add(
                            IncomingPaymentNotification(
                                id = rows.getString("id"),
                                bridgeEventId = rows.getString("bridge_event_id"),
                                amountMinor = rows.getLong("amount_minor"),
                                payerName = rows.getString("payer_name"),
                                securityCodeHmac = rows.getString("security_code_hmac"),
                                receivedAt = Instant.parse(rows.getString("received_at")),
                                sourcePackage = rows.getString("source_package"),
                                sourceType = YapeSourceType.valueOf(rows.getString("source_type")),
                                matchStatus = YapeMatchStatus.valueOf(rows.getString("match_status")),
                                matchedPaymentId = rows.getString("matched_payment_id"),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        connection.close()
    }
}
