package com.example.ecolacteoshuata.presentation.planta.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.DashboardPlanta
import com.example.ecolacteoshuata.domain.usecase.GetDashboardPlantaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlantaDashboardUiState(val cargando: Boolean = true, val dashboard: DashboardPlanta? = null)

class PlantaDashboardViewModel(private val getDashboardPlantaUseCase: GetDashboardPlantaUseCase) : ViewModel() {
    private val _state = MutableStateFlow(PlantaDashboardUiState())
    val state: StateFlow<PlantaDashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val dashboard = try {
                getDashboardPlantaUseCase()
            } catch (e: Exception) {
                println("PlantaDashboardViewModel: error al cargar dashboard: ${e.message}")
                null
            }
            _state.value = PlantaDashboardUiState(cargando = false, dashboard = dashboard)
        }
    }
}
