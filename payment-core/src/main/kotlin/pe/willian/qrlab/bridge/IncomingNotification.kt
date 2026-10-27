package pe.willian.qrlab.bridge

import java.time.Instant

/**
 * Estado de conciliación de una notificación entrante.
 *
 * - RECEIVED: guardada, aún sin procesar.
 * - UNPARSED: sin configuración o sin monto legible; se conserva, no se descarta.
 * - UNMATCHED: pago válido pero sin PaymentIntent compatible; no crea venta.
 * - AMBIGUOUS: dos o más candidatos; espera resolución por código.
 * - MATCHED: vinculada a un pago (automática o por código).
 * - IGNORED: paquete no permitido por las reglas externas.
 */
enum class MatchStatus {
    RECEIVED,
    UNPARSED,
    UNMATCHED,
    AMBIGUOUS,
    MATCHED,
    IGNORED,
}

/**
 * Registro persistido de una notificación entrante.
 *
 * Por defecto NO se persiste el contenido raw completo (title/text/bigText/
 * extras): esos datos solo viven en memoria en modo diagnóstico. Aquí se
 * guardan únicamente los campos necesarios para conciliar y auditar.
 */
data class IncomingNotificationRecord(
    val id: String,
    val bridgeEventId: String,
    val sourcePackage: String,
    val notificationKey: String?,
    val postedAt: Instant,
    val receivedAt: Instant,
    val amountMinor: Long?,
    val payerName: String?,
    /** HMAC-SHA256 del código de 3 dígitos. Nunca el código en plano. */
    val securityCodeHmac: String?,
    val matchStatus: MatchStatus,
    val matchedPaymentId: String?,
    val rawFingerprint: String,
)
