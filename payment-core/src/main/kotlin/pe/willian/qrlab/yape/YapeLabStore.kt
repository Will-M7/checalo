package pe.willian.qrlab.yape

/**
 * Almacén del MODELO 2: ventas pendientes + notificaciones entrantes.
 *
 * Implementaciones:
 * - [InMemoryYapeLabStore]: tests y demos efímeras.
 * - [pe.willian.qrlab.yape.SqliteYapeLabStore]: persistencia local (misma
 *   DB que el resto del laboratorio) para que un reinicio conserve los
 *   eventos ambiguos.
 *
 * PRIVACIDAD: jamás se almacena raw text ni código en plano; como máximo
 * amount, payerName normalizado, HMAC del código, timestamps, package y
 * estado de match.
 */
interface YapeLabStore {

    /** Guarda/actualiza una venta pendiente por id. */
    fun saveWait(wait: YapeWaitingPayment)

    /** Todas las ventas (WAITING/PAID/CANCELLED), en orden de inserción. */
    fun findAllWaits(): List<YapeWaitingPayment>

    /** Guarda/actualiza una notificación entrante por id. */
    fun saveNotification(notification: IncomingPaymentNotification)

    /** Todas las notificaciones, en orden de inserción. */
    fun findAllNotifications(): List<IncomingPaymentNotification>

    fun findWait(id: String): YapeWaitingPayment? = findAllWaits().firstOrNull { it.id == id }

    fun findNotification(id: String): IncomingPaymentNotification? =
        findAllNotifications().firstOrNull { it.id == id }
}

/**
 * Store en memoria (tests rápidos y demos efímeras).
 * Sincronizado: los tests de concurrencia lo usan sin más preparación.
 */
class InMemoryYapeLabStore : YapeLabStore {

    private val lock = Any()
    private val waits = linkedMapOf<String, YapeWaitingPayment>()
    private val notifications = linkedMapOf<String, IncomingPaymentNotification>()

    override fun saveWait(wait: YapeWaitingPayment) = synchronized(lock) {
        waits[wait.id] = wait
    }

    override fun findAllWaits(): List<YapeWaitingPayment> = synchronized(lock) { waits.values.toList() }

    override fun saveNotification(notification: IncomingPaymentNotification) = synchronized(lock) {
        notifications[notification.id] = notification
    }

    override fun findAllNotifications(): List<IncomingPaymentNotification> =
        synchronized(lock) { notifications.values.toList() }
}
