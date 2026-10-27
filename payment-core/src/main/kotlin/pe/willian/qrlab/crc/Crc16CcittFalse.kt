package pe.willian.qrlab.crc

/**
 * CRC-16/CCITT-FALSE (también llamado CRC-16/GENIBUS en forma no reflejada):
 * polynomial = 0x1021, init = 0xFFFF, refin = false, refout = false, xorOut = 0x0000.
 *
 * Vector de prueba canónico: ASCII "123456789" -> 0x29B1.
 */
object Crc16CcittFalse {

    private const val POLY = 0x1021
    private const val INIT = 0xFFFF

    /** Calcula el CRC sobre los bytes dados. Resultado en 0..0xFFFF. */
    fun compute(data: ByteArray): Int {
        var crc = INIT
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor POLY) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }

    /** Calcula el CRC sobre texto ASCII y lo expresa como 4 hexadecimales en mayúsculas. */
    fun computeHex(text: String): String = "%04X".format(compute(text.toByteArray(Charsets.US_ASCII)))
}
