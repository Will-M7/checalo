package com.example.ecolacteoshuata.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.NotificacionUsuario
import com.example.ecolacteoshuata.domain.platform.NotificadorSistema
import com.example.ecolacteoshuata.domain.repository.NotificacionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** La campana del encabezado, igual que "notification-bell" de Laravel. */
class NotificacionesViewModel(
    private val repository: NotificacionRepository,
    notificador: NotificadorSistema,
) : ViewModel() {
    val notificaciones: StateFlow<List<NotificacionUsuario>> = repository.observeTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noLeidas: StateFlow<Int> = repository.observeNoLeidas().map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Al entrar a la app se pide permiso para llevar las notificaciones al celular (iOS).
        notificador.pedirPermiso()
    }

    fun marcarLeida(notificacion: NotificacionUsuario) {
        if (notificacion.leida) return
        viewModelScope.launch { repository.marcarLeida(notificacion.id) }
    }

    fun marcarTodasLeidas() {
        viewModelScope.launch { repository.marcarTodasLeidas() }
    }
}
