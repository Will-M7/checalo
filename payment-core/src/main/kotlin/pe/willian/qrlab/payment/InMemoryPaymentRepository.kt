package pe.willian.qrlab.payment

import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio en memoria. Sin persistencia ni base de datos.
 *
 * Además de almacenar por id, impide retrocesos de estado:
 * todo reemplazo con estado distinto se valida con [PaymentStateMachine],
 * por lo que un pago confirmado nunca puede volver a PENDING.
 */
class InMemoryPaymentRepository : PaymentRepository {

    private val store = ConcurrentHashMap<String, PaymentIntent>()

    override fun save(intent: PaymentIntent): PaymentIntent {
        store.compute(intent.id) { _, existing ->
            if (existing != null && existing.status != intent.status) {
                PaymentStateMachine.validate(existing.status, intent.status)
            }
            intent
        }
        return intent
    }

    override fun findById(id: String): PaymentIntent? = store[id]

    override fun findByReference(reference: String): PaymentIntent? =
        store.values.firstOrNull { it.reference == reference }

    override fun findAll(): List<PaymentIntent> = store.values.toList()
}
