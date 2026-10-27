package pe.willian.yapebridge.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estados posibles que muestra la UI en "Backend bridge".
 *
 * - [CONNECTED]: bridge armado, aún sin intentos de envío.
 * - [LAST_SEND_OK]: el backend acusó recibo del evento (no pago) en el último intento.
 * - [SEND_FAILED]: el último intento falló (backend caído, timeout, HTTP != 2xx).
 */
enum class BridgeStatus {
    CONNECTED,
    LAST_SEND_OK,
    SEND_FAILED
}

/**
 * Estados del health check `POST {baseUrl}/api/bridge/heartbeat` de la tarjeta BACKEND.
 *
 * - [NOT_TESTED]: sin pruebas desde el arranque (o tras cambiar la URL).
 * - [CONNECTING]: petición en curso.
 * - [CONNECTED]: el backend respondió ACK autenticado.
 * - [FAILED]: cualquier otro resultado (timeout, sin conexión, HTTP != 200).
 */
enum class HealthStatus {
    NOT_TESTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/**
 * Estado del envío LAB al backend, compartido entre el
 * [pe.willian.yapebridge.bridge.BackendBridge] (hilo de red) y la UI Compose.
 *
 * Privacidad: aquí solo se conserva el ÚLTIMO package name y el último
 * error breve. No se guarda el payload enviado ni un histórico de envíos.
 */
object BridgeStore {

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    private val _reviewCount = MutableStateFlow(0)
    val reviewCount: StateFlow<Int> = _reviewCount.asStateFlow()
    fun queueCounts(pending: Int, review: Int) { _pendingCount.value = pending; _reviewCount.value = review }

    private val lock = Any()

    private val _status = MutableStateFlow(BridgeStatus.CONNECTED)
    val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    private val _lastPackageSent = MutableStateFlow<String?>(null)
    val lastPackageSent: StateFlow<String?> = _lastPackageSent.asStateFlow()

    private val _lastSendAt = MutableStateFlow<Long?>(null)
    val lastSendAt: StateFlow<Long?> = _lastSendAt.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Último package name intentado (exitoso o no), solo para diagnóstico. */
    private val _lastAttemptPackage = MutableStateFlow<String?>(null)
    val lastAttemptPackage: StateFlow<String?> = _lastAttemptPackage.asStateFlow()

    fun markSendOk(packageName: String) {
        synchronized(lock) {
            _status.value = BridgeStatus.LAST_SEND_OK
            _lastPackageSent.value = packageName
            _lastAttemptPackage.value = packageName
            _lastSendAt.value = System.currentTimeMillis()
            _lastError.value = null
        }
    }

    fun markSendFailed(packageName: String?, reason: String) {
        synchronized(lock) {
            _status.value = BridgeStatus.SEND_FAILED
            if (packageName != null) {
                _lastAttemptPackage.value = packageName
            }
            _lastSendAt.value = System.currentTimeMillis()
            _lastError.value = reason
        }
    }

    /* ---------------- health check de la tarjeta BACKEND ---------------- */

    private val _healthStatus = MutableStateFlow(HealthStatus.NOT_TESTED)
    val healthStatus: StateFlow<HealthStatus> = _healthStatus.asStateFlow()

    private val _healthError = MutableStateFlow<String?>(null)
    val healthError: StateFlow<String?> = _healthError.asStateFlow()

    fun markHealthConnecting() {
        _healthStatus.value = HealthStatus.CONNECTING
        _healthError.value = null
    }

    fun markHealthOk() {
        _healthStatus.value = HealthStatus.CONNECTED
        _healthError.value = null
    }

    fun markHealthFailed(reason: String) {
        _healthStatus.value = HealthStatus.FAILED
        _healthError.value = reason
    }

    /** Vuelve a NOT_TESTED; se usa al guardar una URL distinta. */
    fun resetHealth() {
        _healthStatus.value = HealthStatus.NOT_TESTED
        _healthError.value = null
    }
}
