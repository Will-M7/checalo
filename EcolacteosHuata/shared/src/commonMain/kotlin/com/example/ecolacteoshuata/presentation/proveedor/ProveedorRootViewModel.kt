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
import kotlinx.coroutines.launch

data class ProveedorRootUiState(
    val proveedor: Proveedor? = null,
    /** Ya se buscó el perfil de proveedor (puede no existir todavía si el rol es nuevo). */
    val cargado: Boolean = false,
    val estadoSync: EstadoSincronizacion = EstadoSincronizacion.SIN_SENAL,
    val pendientes: Int = 0,
    val notificaciones: List<Notificacion> = emptyList(),
)

class ProveedorRootViewModel(
    private val usuarioId: String,
    private val getProveedorPorUsuarioUseCase: GetProveedorPorUsuarioUseCase,
    private val getNotificacionesUseCase: GetNotificacionesUseCase,
    observarSyncUseCase: ObservarEstadoSincronizacionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ProveedorRootUiState())
    val state: StateFlow<ProveedorRootUiState> = _state.asStateFlow()

    init {
        cargarProveedor()
        viewModelScope.launch {
            combine(observarSyncUseCase.estado, observarSyncUseCase.pendientes) { estado, pendientes -> estado to pendientes }
                .collect { (estado, pendientes) ->
                    _state.value = _state.value.copy(estadoSync = estado, pendientes = pendientes)
                    if (estado == EstadoSincronizacion.SINCRONIZADO) cargarProveedor()
                }
        }
    }

    fun cargarProveedor() {
        viewModelScope.launch {
            val proveedor = getProveedorPorUsuarioUseCase(usuarioId)
            _state.value = _state.value.copy(proveedor = proveedor, cargado = true)
            _state.value = _state.value.copy(notificaciones = getNotificacionesUseCase(Rol.PROVEEDOR, proveedor?.id))
        }
    }
}
