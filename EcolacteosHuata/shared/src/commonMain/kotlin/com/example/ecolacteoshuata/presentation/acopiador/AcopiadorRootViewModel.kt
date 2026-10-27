package com.example.ecolacteoshuata.presentation.acopiador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.Notificacion
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.usecase.GetAcopiadorPorUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.GetNotificacionesUseCase
import com.example.ecolacteoshuata.domain.usecase.ObservarEstadoSincronizacionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AcopiadorRootUiState(
    val acopiadorId: String? = null,
    val cargado: Boolean = false,
    val estadoSync: EstadoSincronizacion = EstadoSincronizacion.SIN_SENAL,
    val pendientes: Int = 0,
    val notificaciones: List<Notificacion> = emptyList(),
)

class AcopiadorRootViewModel(
    private val usuarioId: String,
    private val getAcopiadorPorUsuarioUseCase: GetAcopiadorPorUsuarioUseCase,
    private val getNotificacionesUseCase: GetNotificacionesUseCase,
    observarSyncUseCase: ObservarEstadoSincronizacionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AcopiadorRootUiState())
    val state: StateFlow<AcopiadorRootUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val acopiador = getAcopiadorPorUsuarioUseCase(usuarioId)
            _state.value = _state.value.copy(acopiadorId = acopiador?.id, cargado = true)
        }
        viewModelScope.launch {
            combine(observarSyncUseCase.estado, observarSyncUseCase.pendientes) { estado, pendientes -> estado to pendientes }
                .collect { (estado, pendientes) ->
                    val perfil = if (estado == EstadoSincronizacion.SINCRONIZADO) getAcopiadorPorUsuarioUseCase(usuarioId) else null
                    _state.value = _state.value.copy(estadoSync = estado, pendientes = pendientes,
                        acopiadorId = if (estado == EstadoSincronizacion.SINCRONIZADO) perfil?.id else _state.value.acopiadorId)
                }
        }
        cargarNotificaciones()
    }

    fun cargarNotificaciones() {
        viewModelScope.launch {
            _state.value = _state.value.copy(notificaciones = getNotificacionesUseCase(Rol.ACOPIADOR))
        }
    }
}
