package pe.willian.qrlab.synthetic

/**
 * Modelo genérico de QR de pago SINTÉTICO del laboratorio.
 *
 * No representa un QR real de Yape, Entel ni ningún proveedor.
 * El dinero viaja en minor units ([amountMinorUnits]) como Long, nunca Double.
 */
data class SyntheticPaymentQr(
    val amountMinorUnits: Long,
    val currency: String = "604",
    val merchantName: String = "QR LAB",
    val merchantCity: String = "Lab",
    val countryCode: String = "PE",
) {
    init {
        require(amountMinorUnits >= 0) { "amountMinorUnits debe ser >= 0" }
        require(Regex("^[0-9]{3}$").matches(currency)) { "currency debe ser 3 dígitos" }
        require(merchantName.isNotBlank()) { "merchantName no debe estar vacío" }
        require(merchantCity.isNotBlank()) { "merchantCity no debe estar vacía" }
        require(Regex("^[A-Za-z]{2}$").matches(countryCode)) { "countryCode debe ser 2 letras" }
    }
}
