package pe.willian.yapebridge.bridge

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configuración local de la URL del backend.
 *
 * - Persistencia: SharedPreferences del propio dispositivo. Sin nube,
 *   sin cuentas, sin compartir datos con otras apps.
 * - Lectores: [BackendBridge] toma [baseUrl] en cada envío/health check.
 * - Se inicializa desde [pe.willian.yapebridge.YapeBridgeApp]; si alguien
 *   leyera antes, se usa el default de [BridgeConfig].
 */
object BackendSettings {

    private const val PREFS_NAME = "yape_bridge_backend"
    private const val KEY_BASE_URL = "base_url"

    private val lock = Any()
    private var prefs: SharedPreferences? = null
    private var loaded = false

    private val _baseUrl = MutableStateFlow(BridgeConfig.DEFAULT_BASE_URL)

    /** Base URL vigente (sin la ruta del endpoint). Observable por la UI. */
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    /** Carga el valor guardado. Idempotente y seguro desde cualquier hilo. */
    fun init(context: Context) {
        val p = prefsOf(context)
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val stored = p.getString(KEY_BASE_URL, null)
            // Valor corrupto o de una versión anterior: se ignora y queda el default.
            val normalized = stored?.let { CanonicalProtocol.secureUrl(it, pe.willian.yapebridge.BuildConfig.DEBUG) }
            if (normalized != null) {
                _baseUrl.value = normalized
            }
            loaded = true
        }
    }

    /**
     * Guarda la base URL y la deja vigente de inmediato.
     *
     * Devuelve la URL normalizada, o null si es inválida
     * (en ese caso NO se escribe nada en disco).
     */
    fun save(context: Context, raw: String): String? {
        val normalized = CanonicalProtocol.secureUrl(raw, pe.willian.yapebridge.BuildConfig.DEBUG) ?: return null
        prefsOf(context).edit().putString(KEY_BASE_URL, normalized).apply()
        _baseUrl.value = normalized
        return normalized
    }

    private fun prefsOf(context: Context): SharedPreferences {
        prefs?.let { return it }
        synchronized(lock) {
            prefs?.let { return it }
            val p = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            return p
        }
    }
}
