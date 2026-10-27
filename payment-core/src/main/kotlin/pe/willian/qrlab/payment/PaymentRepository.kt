package pe.willian.qrlab.payment

/**
 * Abstracción de almacenamiento de intenciones de pago, por id.
 * Sin base de datos en esta fase.
 */
interface PaymentRepository {

    /** Guarda (crea o reemplaza con transición validada) una intención. */
    fun save(intent: PaymentIntent): PaymentIntent

    /** Busca por id, o null si no existe. */
    fun findById(id: String): PaymentIntent?

    /** Busca por referencia comercial, o null si no existe. */
    fun findByReference(reference: String): PaymentIntent?

    /** Todas las intenciones (el matcher Yape filtra en memoria). */
    fun findAll(): List<PaymentIntent>
}
