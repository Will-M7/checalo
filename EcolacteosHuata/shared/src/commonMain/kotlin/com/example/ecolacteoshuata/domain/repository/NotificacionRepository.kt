package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.NotificacionUsuario
import kotlinx.coroutines.flow.Flow

/** La campana de notificaciones, igual que en Laravel (se sincroniza con su tabla `notifications`). */
interface NotificacionRepository {
    fun observeTodas(): Flow<List<NotificacionUsuario>>
    fun observeNoLeidas(): Flow<Long>
    suspend fun marcarLeida(id: String)
    suspend fun marcarTodasLeidas()
}
