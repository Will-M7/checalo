package com.example.ecolacteoshuata.presentation.vendedor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.Notificacion
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.usecase.GetNotificacionesUseCase
import com.example.ecolacteoshuata.domain.usecase.ObservarEstadoSincronizacionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class VendedorRootUiState(
    val estadoSync: EstadoSincronizacion = EstadoSincronizacion.SIN_SENAL,
    val pendientes: Int = 0,
    val notificaciones: List<Notificacion> = emptyList(),
)

class VendedorRootViewModel(
    private val getNotificacionesUseCase: GetNotificacionesUseCase,
    observarSyncUseCase: ObservarEstadoSincronizacionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(VendedorRootUiState())
    val state: StateFlow<VendedorRootUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observarSyncUseCase.estado, observarSyncUseCase.pendientes) { estado, pendientes -> estado to pendientes }
                .collect { (estado, pendientes) -> _state.value = _state.value.copy(estadoSync = estado, pendientes = pendientes) }
        }
        cargarNotificaciones()
    }

    fun cargarNotificaciones() {
        viewModelScope.launch {
            _state.value = _state.value.copy(notificaciones = getNotificacionesUseCase(Rol.VENDEDOR))
        }
    }
}
