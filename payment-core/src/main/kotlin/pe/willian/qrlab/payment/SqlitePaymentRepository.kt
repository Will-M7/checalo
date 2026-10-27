package pe.willian.qrlab.payment

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * Repositorio SQLite local del laboratorio (JDBC sencillo, sin ORM).
 *
 * Archivo típico: data/qr-lab.db (solo pagos sintéticos QR LAB).
 * - Esquema idempotente (CREATE TABLE/INDEX IF NOT EXISTS), sin borrar datos.
 * - Una referencia = una operación: índice UNIQUE sobre reference cuando
 *   la DB existente lo permite (sin duplicados previos); si ya hubiera
 *   duplicados históricos se conserva el índice simple y se avisa.
 * - Instant en ISO-8601, status por name, dinero como INTEGER (nunca Double).
 * - Todo reemplazo con estado distinto se valida con [PaymentStateMachine],
 *   dentro de una transacción explícita (sin find+save desprotegidos).
 */
class SqlitePaymentRepository(dbPath: Path) : PaymentRepository, AutoCloseable {

    private val connection: Connection
    private var externalTransaction = false

    init {
        dbPath.parent?.let { Files.createDirectories(it) }
        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA busy_timeout=5000")
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS payment_intents (
                    id TEXT PRIMARY KEY,
                    reference TEXT NOT NULL,
                    amount_minor INTEGER NOT NULL,
                    currency TEXT NOT NULL,
                    status TEXT NOT NULL,
                    qr_payload TEXT,
                    created_at TEXT NOT NULL,
                    expires_at TEXT,
                    paid_at TEXT,
                    provider_operation_reference TEXT,
                    payment_method TEXT NOT NULL DEFAULT 'SYNTHETIC_QR'
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_payment_intents_reference ON payment_intents(reference)",
            )
        }
        ensurePaymentMethodColumn()
        ensureReferenceUniqueness()
    }

    /**
     * Migración sin borrar datos: DBs creadas antes del campo payment_method
     * no lo tienen. Se agrega con default y los registros existentes quedan
     * como SYNTHETIC_QR (único método que existía).
     */
    private fun ensurePaymentMethodColumn() {
        val columns = connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(payment_intents)").use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString("name"))
                }
            }
        }
        if ("payment_method" !in columns) {
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "ALTER TABLE payment_intents ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'SYNTHETIC_QR'",
                )
                statement.executeUpdate(
                    "UPDATE payment_intents SET payment_method = 'SYNTHETIC_QR' " +
                        "WHERE payment_method IS NULL OR payment_method = ''",
                )
            }
        } else {
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "UPDATE payment_intents SET payment_method = 'SYNTHETIC_QR' " +
                        "WHERE payment_method IS NULL OR payment_method = ''",
                )
            }
        }
    }

    /**
     * UNIQUE sobre reference solo si no hay duplicados históricos.
     * Nunca borra datos: si los hubiera, se queda el índice simple.
     */
    private fun ensureReferenceUniqueness() {
        val duplicates = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT COUNT(*) FROM (SELECT reference FROM payment_intents GROUP BY reference HAVING COUNT(*) > 1)",
            ).use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }
        if (duplicates == 0L) {
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_intents_reference ON payment_intents(reference)",
                )
            }
        } else {
            System.err.println(
                "Aviso: $duplicates referencia(s) duplicada(s) en DB existente; " +
                    "sin constraint UNIQUE (se mantiene índice simple).",
            )
        }
    }

    @Synchronized
    override fun save(intent: PaymentIntent): PaymentIntent {
        val ownsTransaction = !externalTransaction
        val autoCommit = connection.autoCommit
        if (ownsTransaction) connection.autoCommit = false
        try {
            findById(intent.id)?.let { existing ->
                if (existing.status != intent.status) {
                    PaymentStateMachine.validate(existing.status, intent.status)
                }
            }
            connection.prepareStatement(
                """
                INSERT INTO payment_intents
                    (id, reference, amount_minor, currency, status, qr_payload,
                     created_at, expires_at, paid_at, provider_operation_reference, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    reference = excluded.reference,
                    amount_minor = excluded.amount_minor,
                    currency = excluded.currency,
                    status = excluded.status,
                    qr_payload = excluded.qr_payload,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at,
                    paid_at = excluded.paid_at,
                    provider_operation_reference = excluded.provider_operation_reference,
                    payment_method = excluded.payment_method
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, intent.id)
                statement.setString(2, intent.reference)
                statement.setLong(3, intent.amountMinor)
                statement.setString(4, intent.currency)
                statement.setString(5, intent.status.name)
                statement.setString(6, intent.qrPayload)
                statement.setString(7, intent.createdAt.toString())
                statement.setString(8, intent.expiresAt?.toString())
                statement.setString(9, intent.paidAt?.toString())
                statement.setString(10, intent.providerOperationReference)
                statement.setString(11, intent.paymentMethod.name)
                statement.executeUpdate()
            }
            if (ownsTransaction) connection.commit()
        } catch (e: Exception) {
            if (ownsTransaction) connection.rollback()
            throw e
        } finally {
            if (ownsTransaction) connection.autoCommit = autoCommit
        }
        return intent
    }

    /** Serializes the entire evidence/payment decision, including across SQLite connections. */
    @Synchronized
    fun <T> atomic(block: (Connection) -> T): T {
        check(!externalTransaction)
        connection.createStatement().use { it.execute("BEGIN IMMEDIATE") }
        externalTransaction = true
        try {
            val result = block(connection)
            connection.createStatement().use { it.execute("COMMIT") }
            return result
        } catch (e: Throwable) {
            connection.createStatement().use { it.execute("ROLLBACK") }
            throw e
        } finally { externalTransaction = false }
    }

    fun requireLaboratoryStorage() = atomic { db ->
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='canonical_bindings'").use { rows ->
                check(!rows.next()) { "Commercial storage cannot be mounted by laboratory routes" }
            }
        }
    }

    @Synchronized
    override fun findById(id: String): PaymentIntent? {
        connection.prepareStatement("SELECT * FROM payment_intents WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                return rowToIntent(rows)
            }
        }
    }

    @Synchronized
    override fun findByReference(reference: String): PaymentIntent? {
        connection.prepareStatement(
            "SELECT * FROM payment_intents WHERE reference = ? ORDER BY rowid LIMIT 1",
        ).use { statement ->
            statement.setString(1, reference)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                return rowToIntent(rows)
            }
        }
    }

    @Synchronized
    override fun findAll(): List<PaymentIntent> {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM payment_intents").use { rows ->
                return buildList {
                    while (rows.next()) add(rowToIntent(rows))
                }
            }
        }
    }

    override fun close() {
        connection.close()
    }

    /** Número total de filas (útil en tests de concurrencia). */
    @Synchronized
    fun count(): Long {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM payment_intents").use { rows ->
                rows.next()
                return rows.getLong(1)
            }
        }
    }

    private fun rowToIntent(rows: java.sql.ResultSet): PaymentIntent = PaymentIntent(
        id = rows.getString("id"),
        reference = rows.getString("reference"),
        amountMinor = rows.getLong("amount_minor"),
        currency = rows.getString("currency"),
        status = PaymentStatus.valueOf(rows.getString("status")),
        qrPayload = rows.getString("qr_payload"),
        createdAt = Instant.parse(rows.getString("created_at")),
        expiresAt = rows.getString("expires_at")?.let(Instant::parse),
        paidAt = rows.getString("paid_at")?.let(Instant::parse),
        providerOperationReference = rows.getString("provider_operation_reference"),
        paymentMethod = readPaymentMethod(rows),
    )

    private fun readPaymentMethod(rows: java.sql.ResultSet): PaymentMethod {
        return try {
            rows.getString("payment_method")?.takeIf { it.isNotBlank() }?.let(PaymentMethod::valueOf)
                ?: PaymentMethod.SYNTHETIC_QR
        } catch (_: Exception) {
            PaymentMethod.SYNTHETIC_QR
        }
    }
}
