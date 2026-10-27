package com.example.ecolacteoshuata.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.data.sync.ConteoSync
import com.example.ecolacteoshuata.data.sync.ProgresoSync
import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class EtapaDescarga { INACTIVA, DESCARGANDO, LISTA, ERROR }

data class PrimeraDescargaUiState(
    val visible: Boolean = false,
    val etapa: EtapaDescarga = EtapaDescarga.INACTIVA,
    val servidor: String = "",
    val progreso: ProgresoSync? = null,
    val resumen: List<ConteoSync> = emptyList(),
    val error: String? = null,
)

/**
 * Primera instalación conectada a Laravel: apenas el teléfono tiene token (primer login con
 * conexión) y nunca sincronizó (`ultimaSincronizacion` vacía), descarga todo el sistema para que
 * la app quede lista para trabajar sin señal, con cualquier usuario.
 */
class PrimeraDescargaViewModel(
    private val conexionServidorRepository: ConexionServidorRepository,
    private val syncService: SyncService,
) : ViewModel() {
    private val _state = MutableStateFlow(PrimeraDescargaUiState())
    val state: StateFlow<PrimeraDescargaUiState> = _state.asStateFlow()

    private var omitida = false

    init {
        viewModelScope.launch {
            conexionServidorRepository.observe().collect { conexion ->
                val pendiente = conexion.activa && conexion.host.isNotBlank() && conexion.token != null &&
                    conexion.ultimaSincronizacion == null
                val servidor = "${conexion.host}:${conexion.puerto}"
                if (servidor != _state.value.servidor && _state.value.etapa != EtapaDescarga.DESCARGANDO) {
                    // Cambió el servidor (p. ej. tras "Cambiar servidor"): se vuelve a intentar.
                    omitida = false
                    _state.value = _state.value.copy(etapa = EtapaDescarga.INACTIVA)
                }
                _state.value = _state.value.copy(servidor = servidor)
                if (pendiente && !omitida && _state.value.etapa == EtapaDescarga.INACTIVA) descargar()
            }
        }
        viewModelScope.launch {
            combine(syncService.progreso, syncService.ultimoResumen) { p, r -> p to r }.collect { (p, r) ->
                _state.value = _state.value.copy(progreso = p, resumen = r)
            }
        }
    }

    private fun descargar() {
        _state.value = _state.value.copy(visible = true, etapa = EtapaDescarga.DESCARGANDO, error = null)
        viewModelScope.launch {
            syncService.sincronizarAhora()
            val error = syncService.ultimoError.value
            _state.value = _state.value.copy(
                etapa = if (error == null) EtapaDescarga.LISTA else EtapaDescarga.ERROR,
                error = error,
            )
        }
    }

    fun reintentar() = descargar()

    /** Cierra la pantalla tras una descarga exitosa. */
    fun continuar() {
        _state.value = _state.value.copy(visible = false)
    }

    /** Deja entrar sin descargar (se volverá a intentar al iniciar sesión). */
    fun omitir() {
        omitida = true
        _state.value = _state.value.copy(visible = false, etapa = EtapaDescarga.INACTIVA)
    }
}
