package pe.willian.qrlab.synthetic

import pe.willian.qrlab.crc.Crc16CcittFalse
import pe.willian.qrlab.emv.TlvEncoder
import pe.willian.qrlab.money.MoneyFormatter

/**
 * Constructor de payloads sintéticos del laboratorio.
 *
 * Solo tags genéricos: 00, 01, 52, 53, 54, 58, 59, 60 y 63 (CRC).
 * NO crea 26/39/80/90/91/92 porque en las muestras observadas
 * son provider-specific.
 *
 * Convención del laboratorio (NO universal): el tag 54 lleva
 * [SyntheticPaymentQr.amountMinorUnits] como decimal y el tag 59
 * combina el nombre ficticio con la representación amigable del monto.
 * Opera solo con datos sintéticos de laboratorio, sin leer muestras reales.
 */
object SyntheticPaymentQrBuilder {

    /** MCC reservado de laboratorio, sin comercio real asociado. */
    const val LAB_MCC = "9999"

    /** Construye el payload completo con CRC para [qr]. */
    fun build(qr: SyntheticPaymentQr): String {
        val merchant59 = "${qr.merchantName} ${MoneyFormatter.format(qr.amountMinorUnits)}"
        val stem = buildString {
            append(TlvEncoder.encode("00", "01"))
            append(TlvEncoder.encode("01", "12"))
            append(TlvEncoder.encode("52", LAB_MCC))
            append(TlvEncoder.encode("53", qr.currency))
            append(TlvEncoder.encode("54", qr.amountMinorUnits.toString()))
            append(TlvEncoder.encode("58", qr.countryCode))
            append(TlvEncoder.encode("59", merchant59))
            append(TlvEncoder.encode("60", qr.merchantCity))
            append("6304")
        }
        return stem + Crc16CcittFalse.computeHex(stem)
    }

    /** Atajo con los valores ficticios del laboratorio para un monto dado. */
    fun buildWithLabDefaults(amountMinorUnits: Long): String =
        build(SyntheticPaymentQr(amountMinorUnits = amountMinorUnits))
}
