package com.example.ecolacteoshuata.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.usecase.LoginUseCase
import com.example.ecolacteoshuata.domain.usecase.ObservarEstadoSincronizacionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    observarSyncUseCase: ObservarEstadoSincronizacionUseCase,
    private val conexionServidorRepository: ConexionServidorRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observarSyncUseCase.estado, observarSyncUseCase.pendientes) { estado, pendientes -> estado to pendientes }
                .collect { (estado, pendientes) ->
                    _state.value = _state.value.copy(estadoSync = estado, pendientes = pendientes)
                }
        }
        viewModelScope.launch {
            conexionServidorRepository.observe().collect { conexion ->
                _state.value = _state.value.copy(conexion = conexion)
            }
        }
    }

    fun onUsuarioChange(valor: String) {
        _state.value = _state.value.copy(usuario = valor, error = null)
    }

    fun onPasswordChange(valor: String) {
        _state.value = _state.value.copy(password = valor, error = null)
    }

    fun ingresar() {
        val estado = _state.value
        if (estado.cargando) return
        viewModelScope.launch {
            _state.value = _state.value.copy(cargando = true, error = null)
            when (val resultado = loginUseCase(estado.usuario, estado.password)) {
                is AppResult.Success -> _state.value = _state.value.copy(cargando = false, usuarioLogueado = resultado.data)
                is AppResult.Error -> _state.value = _state.value.copy(cargando = false, error = resultado.message)
            }
        }
    }

    fun navegacionConsumida() {
        _state.value = _state.value.copy(usuarioLogueado = null)
    }
}
