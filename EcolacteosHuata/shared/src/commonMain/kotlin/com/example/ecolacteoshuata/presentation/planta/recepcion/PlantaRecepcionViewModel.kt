package com.example.ecolacteoshuata.presentation.planta.recepcion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GrupoRecepcion(val acopiador: Acopiador, val recojos: List<Recojo>) {
    val totalLitros: Double get() = recojos.sumOf { it.litros }
}

data class PlantaRecepcionUiState(val cargando: Boolean = true, val grupos: List<GrupoRecepcion> = emptyList())

class PlantaRecepcionViewModel(
    private val acopiadorRepository: AcopiadorRepository,
    private val recojoRepository: RecojoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlantaRecepcionUiState())
    val state: StateFlow<PlantaRecepcionUiState> = _state.asStateFlow()

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _state.value = _state.value.copy(cargando = true)
            val hoy = todayLocalDate()
            val acopiadores = acopiadorRepository.getAll()
            val grupos = acopiadores.map { acopiador ->
                GrupoRecepcion(acopiador, recojoRepository.getByAcopiadorYFecha(acopiador.id, hoy))
            }
            _state.value = PlantaRecepcionUiState(cargando = false, grupos = grupos)
        }
    }
}
