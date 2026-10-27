package pe.willian.qrlab.money

/**
 * Formato monetario del laboratorio a partir de minor units (Long).
 *
 * Convención del laboratorio (inspirada en observaciones, NO universal):
 * el entero representa céntimos; 500 -> "S/ 5.00".
 * No usa Double en ningún punto.
 */
object MoneyFormatter {

    /** Formatea [minorUnits] como "S/ soles.cc". Requiere valor >= 0. */
    fun format(minorUnits: Long): String {
        require(minorUnits >= 0) { "minorUnits debe ser >= 0, fue $minorUnits" }
        val soles = minorUnits / 100
        val cents = (minorUnits % 100).toInt()
        return "S/ $soles.${"%02d".format(cents)}"
    }
}
