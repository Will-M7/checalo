package pe.willian.qrlab.bridge

/**
 * Pago interpretado desde una notificación entrante.
 *
 * Todo nullable: el parser solo informa lo que pudo extraer con confianza.
 * Todavía NO existe un parser Yape específico rígido; la interpretación
 * vive en [ConfigurableNotificationParser] con reglas externas.
 */
data class ParsedIncomingPayment(
    val amountMinor: Long?,
    val payerName: String?,
    val securityCode: String?,
)

/**
 * Abstracción del parser de notificaciones entrantes.
 *
 * Diseñada para reemplazarse en minutos cuando se capture la notificación
 * real: basta una nueva implementación o nuevas [YapeNotificationRules],
 * sin tocar almacenamiento, matcher ni rutas.
 */
interface IncomingNotificationParser {

    /** false si el paquete no está permitido por las reglas externas. */
    fun isPackageAllowed(packageName: String): Boolean

    /**
     * Interpreta la notificación o null si el paquete está filtrado.
     * Si el contenido no es legible, devuelve [ParsedIncomingPayment] con
     * campos null (el evento se guarda como UNPARSED, nunca se descarta).
     */
    fun parse(raw: RawBridgeNotification): ParsedIncomingPayment?
}
