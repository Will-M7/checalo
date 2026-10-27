package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import kotlinx.coroutines.flow.StateFlow

data class EstadoSync(val estado: EstadoSincronizacion, val pendientes: Int)

class ObservarEstadoSincronizacionUseCase(private val syncService: SyncService) {
    val estado: StateFlow<EstadoSincronizacion> get() = syncService.estado
    val pendientes: StateFlow<Int> get() = syncService.pendientes
}
