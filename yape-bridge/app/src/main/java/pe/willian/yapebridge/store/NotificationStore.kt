package pe.willian.yapebridge.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.willian.yapebridge.model.CapturedNotification

/**
 * Historial en memoria con límite fijo. Puro Kotlin, testeable en JVM.
 *
 * Mantiene como máximo [maxSize] elementos (los más recientes primero).
 */
class NotificationHistory(private val maxSize: Int = MAX_SIZE) {

    private val lock = Any()
    private val items = ArrayDeque<CapturedNotification>()

    fun add(notification: CapturedNotification) {
        synchronized(lock) {
            items.addFirst(notification)
            while (items.size > maxSize) {
                items.removeLast()
            }
        }
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }

    fun snapshot(): List<CapturedNotification> = synchronized(lock) { items.toList() }

    fun size(): Int = synchronized(lock) { items.size }

    companion object {
        const val MAX_SIZE = 30
    }
}

/**
 * Singleton compartido entre el [pe.willian.yapebridge.listener.LabNotificationListener]
 * (proceso de UI en esta fase de laboratorio) y la UI Compose.
 */
object NotificationStore {

    private val history = NotificationHistory()

    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    /** true mientras el listener está conectado al sistema. */
    val listenerConnected = MutableStateFlow(false)

    fun push(notification: CapturedNotification) {
        history.add(notification)
        _notifications.value = history.snapshot()
    }

    fun clear() {
        history.clear()
        _notifications.value = emptyList()
    }
}
