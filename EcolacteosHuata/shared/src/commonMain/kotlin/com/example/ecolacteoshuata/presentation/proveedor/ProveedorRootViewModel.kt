package com.example.ecolacteoshuata.presentation.proveedor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.Notificacion
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.usecase.GetNotificacionesUseCase
import com.example.ecolacteoshuata.domain.usecase.GetProveedorPorUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.ObservarEstadoSincronizacionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ProveedorRootUiState(
    val proveedor: Proveedor? = null,
    /** Ya se buscó el perfil de proveedor (puede no existir todavía si el rol es nuevo). */
    val cargado: Boolean = false,
    val estadoSync: EstadoSincronizacion = EstadoSincronizacion.SIN_SENAL,
    val pendientes: Int = 0,
    val notificaciones: List<Notificacion> = emptyList(),
    val error: String? = null,
    val descargado: Boolean = false,
    val requiereSesion: Boolean = false,
)

class ProveedorRootViewModel(
    private val usuarioId: String,
    private val getProveedorPorUsuarioUseCase: GetProveedorPorUsuarioUseCase,
    private val getNotificacionesUseCase: GetNotificacionesUseCase,
    observarSyncUseCase: ObservarEstadoSincronizacionUseCase,
    private val syncService: com.example.ecolacteoshuata.data.sync.SyncService,
    private val conexion: com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProveedorRootUiState())
    val state: StateFlow<ProveedorRootUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getProveedorPorUsuarioUseCase.observe(usuarioId).catch {
                _state.value = _state.value.copy(cargado = true, error = "No se pudo leer el perfil del teléfono. Reintenta la sincronización.")
            }.collect { proveedor ->
                _state.value = _state.value.copy(proveedor = proveedor, cargado = true)
                cargarAvisos(proveedor)
            }
        }
        viewModelScope.launch {
            combine(syncService.ultimoError, conexion.observe()) { error, c -> error to c }.collect { (error, c) ->
                _state.value = _state.value.copy(error = error, descargado = c.ultimaSincronizacion != null,
                    requiereSesion = c.activa && c.token.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            combine(observarSyncUseCase.estado, observarSyncUseCase.pendientes) { estado, pendientes -> estado to pendientes }
                .collect { (estado, pendientes) ->
                    _state.value = _state.value.copy(estadoSync = estado, pendientes = pendientes)
                }
        }
    }

    fun cargarProveedor() {
        viewModelScope.launch {
            syncService.sincronizarAhora()
            try {
                val proveedor = getProveedorPorUsuarioUseCase(usuarioId)
                _state.value = _state.value.copy(proveedor = proveedor, cargado = true)
                cargarAvisos(proveedor)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(cargado = true, error = "No se pudo leer el perfil local. Vuelve a intentar la sincronización.")
            }
        }
    }

    private suspend fun cargarAvisos(proveedor: Proveedor?) {
        try {
            _state.value = _state.value.copy(notificaciones = getNotificacionesUseCase(Rol.PROVEEDOR, proveedor?.id))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* La campana no impide abrir el perfil. */ }
    }
}
