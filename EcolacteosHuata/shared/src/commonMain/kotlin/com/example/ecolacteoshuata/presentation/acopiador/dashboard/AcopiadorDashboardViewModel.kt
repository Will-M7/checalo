package com.example.ecolacteoshuata.presentation.acopiador.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.DashboardAcopiador
import com.example.ecolacteoshuata.domain.usecase.GetDashboardAcopiadorUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AcopiadorDashboardUiState(val cargando: Boolean = true, val dashboard: DashboardAcopiador? = null)

class AcopiadorDashboardViewModel(
    private val acopiadorId: String,
    private val getDashboardAcopiadorUseCase: GetDashboardAcopiadorUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AcopiadorDashboardUiState())
    val state: StateFlow<AcopiadorDashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val dashboard = try {
                getDashboardAcopiadorUseCase(acopiadorId)
            } catch (e: Exception) {
                println("AcopiadorDashboardViewModel: error al cargar dashboard: ${e.message}")
                null
            }
            _state.value = AcopiadorDashboardUiState(cargando = false, dashboard = dashboard)
        }
    }
}
