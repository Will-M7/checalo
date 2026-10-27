package com.example.ecolacteoshuata.presentation.common.conexion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.platform.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** Los avisos de conexión que ve el usuario (arriba de la pantalla). */
enum class TipoAvisoConexion(val mensaje: String, val ofreceReintentar: Boolean) {
    SIN_INTERNET("No hay conexión a Internet. Comprueba tu conexión.", true),
    PERDIDA("Se perdió la conexión.", false),
    RECONECTANDO("Reconectando", false),
    RESTABLECIDA("Conexión restablecida.", false),
    INESTABLE("Conexión inestable.", true),
}

/** Cuánto se queda visible "Se perdió la conexión." antes de pasar a "Reconectando". */
private const val MOSTRAR_PERDIDA_MS = 2_200L

/** "Conexión restablecida." desaparece sola. */
private const val MOSTRAR_RESTABLECIDA_MS = 2_800L

/** Tres cortes en este lapso = la conexión va y viene: "Conexión inestable.". */
private const val VENTANA_INESTABLE_MS = 60_000L
private const val CORTES_INESTABLE = 3

/**
 * Decide qué aviso de conexión mostrar a partir del monitor de red (ConnectivityManager /
 * NWPathMonitor) y del sync con Laravel:
 * - Sin red al abrir: "No hay conexión a Internet…" (con "Inténtalo de nuevo.").
 * - Se cae la red: "Se perdió la conexión." y luego "Reconectando" mientras no vuelva.
 * - Vuelve: "Conexión restablecida." y se oculta.
 * - La red va y viene, o hay red pero el servidor no responde: "Conexión inestable.".
 */
class AvisoConexionViewModel(
    private val networkMonitor: NetworkMonitor,
    private val syncService: SyncService,
) : ViewModel() {
    private val _aviso = MutableStateFlow<TipoAvisoConexion?>(null)
    val aviso: StateFlow<TipoAvisoConexion?> = _aviso.asStateFlow()

    private val reloj = TimeSource.Monotonic
    private val cortes = ArrayDeque<TimeSource.Monotonic.ValueTimeMark>()
    private var temporizador: Job? = null

    init {
        viewModelScope.launch {
            var anterior: Boolean? = null
            networkMonitor.enLinea.collect { enLinea ->
                when {
                    anterior == null && !enLinea -> mostrar(TipoAvisoConexion.SIN_INTERNET)
                    anterior == true && !enLinea -> alPerderse()
                    anterior == false && enLinea -> alVolver()
                }
                anterior = enLinea
            }
        }
        // Hay red pero el servidor no responde (o tarda): la conexión es inestable.
        viewModelScope.launch {
            var anterior: EstadoSincronizacion? = null
            syncService.estado.collect { estado ->
                // Solo cuando venía funcionando (no el "sin señal" con que arranca la app).
                val veniaBien = anterior == EstadoSincronizacion.SINCRONIZADO || anterior == EstadoSincronizacion.SINCRONIZANDO
                anterior = estado
                val sinRespuesta = veniaBien && estado == EstadoSincronizacion.SIN_SENAL && networkMonitor.hayConexion()
                if (sinRespuesta && _aviso.value == null) mostrar(TipoAvisoConexion.INESTABLE)
                if (estado == EstadoSincronizacion.SINCRONIZADO && _aviso.value == TipoAvisoConexion.INESTABLE) {
                    mostrar(TipoAvisoConexion.RESTABLECIDA, ocultarEn = MOSTRAR_RESTABLECIDA_MS)
                }
            }
        }
    }

    private fun alPerderse() {
        val ahora = reloj.markNow()
        cortes.addLast(ahora)
        while (cortes.isNotEmpty() && cortes.first().elapsedNow().inWholeMilliseconds > VENTANA_INESTABLE_MS) cortes.removeFirst()
        if (cortes.size >= CORTES_INESTABLE) {
            mostrar(TipoAvisoConexion.INESTABLE)
            return
        }
        mostrar(TipoAvisoConexion.PERDIDA)
        temporizador = viewModelScope.launch {
            delay(MOSTRAR_PERDIDA_MS)
            if (!networkMonitor.hayConexion()) _aviso.value = TipoAvisoConexion.RECONECTANDO
        }
    }

    private fun alVolver() {
        mostrar(TipoAvisoConexion.RESTABLECIDA, ocultarEn = MOSTRAR_RESTABLECIDA_MS)
    }

    private fun mostrar(tipo: TipoAvisoConexion, ocultarEn: Long? = null) {
        temporizador?.cancel()
        _aviso.value = tipo
        if (ocultarEn != null) {
            temporizador = viewModelScope.launch {
                delay(ocultarEn)
                if (_aviso.value == tipo) _aviso.value = null
            }
        }
    }

    /** "Inténtalo de nuevo.": vuelve a probar con el servidor. */
    fun reintentar() {
        if (!networkMonitor.hayConexion()) {
            mostrar(TipoAvisoConexion.RECONECTANDO)
            return
        }
        mostrar(TipoAvisoConexion.RECONECTANDO)
        viewModelScope.launch {
            syncService.sincronizarAhora()
            mostrar(
                if (syncService.estado.value == EstadoSincronizacion.SIN_SENAL) TipoAvisoConexion.INESTABLE else TipoAvisoConexion.RESTABLECIDA,
                ocultarEn = MOSTRAR_RESTABLECIDA_MS.takeIf { syncService.estado.value != EstadoSincronizacion.SIN_SENAL },
            )
        }
    }

    fun cerrar() {
        temporizador?.cancel()
        _aviso.value = null
    }
}
