package com.example.ecolacteoshuata.presentation.acopiador.inventario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AcopiadorInventarioViewModel(
    private val acopiadorId: String,
    private val recojoRepository: RecojoRepository,
    private val turnoRepository: TurnoRepository,
) : ViewModel() {
    private val _pendientes = MutableStateFlow<List<Recojo>>(emptyList())
    val pendientes: StateFlow<List<Recojo>> = _pendientes.asStateFlow()

    /** Turnos por id, para mostrar en qué turno de ruta se hizo cada recojo. */
    private val _turnos = MutableStateFlow<Map<String, Turno>>(emptyMap())
    val turnos: StateFlow<Map<String, Turno>> = _turnos.asStateFlow()

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _turnos.value = turnoRepository.getAll().associateBy { it.id }
            _pendientes.value = recojoRepository.getAll()
                .filter { it.acopiadorId == acopiadorId && !it.entregado }
                .sortedByDescending { it.fecha }
        }
    }
}
