package com.example.ecolacteoshuata.presentation.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.DashboardAdmin
import com.example.ecolacteoshuata.domain.usecase.GetDashboardAdminUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val cargando: Boolean = true,
    val dashboard: DashboardAdmin? = null,
)

class AdminDashboardViewModel(private val getDashboardAdminUseCase: GetDashboardAdminUseCase) : ViewModel() {
    private val _state = MutableStateFlow(AdminDashboardUiState())
    val state: StateFlow<AdminDashboardUiState> = _state.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _state.value = _state.value.copy(cargando = true)
            val dashboard = try {
                getDashboardAdminUseCase()
            } catch (e: Exception) {
                println("AdminDashboardViewModel: error al cargar dashboard: ${e.message}")
                null
            }
            _state.value = AdminDashboardUiState(cargando = false, dashboard = dashboard)
        }
    }
}
