package com.example.ecolacteoshuata.presentation.proveedor.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.DashboardProveedor
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.usecase.GetDashboardProveedorUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class ProveedorDashboardUiState(
    val cargando: Boolean = true,
    val dashboard: DashboardProveedor? = null,
    /** El proveedor tal como está ahora (cambia al editar la ubicación o con el sync). */
    val proveedor: Proveedor? = null,
)

/**
 * Panel del proveedor de Laravel. Se recalcula cuando cambian sus datos, sus recojos o el
 * acopiador (por ejemplo, al editar la ubicación de la finca o al sincronizar).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProveedorDashboardViewModel(
    private val proveedorId: String,
    proveedorRepository: ProveedorRepository,
    recojoRepository: RecojoRepository,
    acopiadorRepository: AcopiadorRepository,
    private val getDashboardProveedorUseCase: GetDashboardProveedorUseCase,
) : ViewModel() {
    val state: StateFlow<ProveedorDashboardUiState> = combine(
        proveedorRepository.observeAll(),
        recojoRepository.observeByProveedor(proveedorId),
        acopiadorRepository.observeAll(),
    ) { proveedores, _, _ -> proveedores.firstOrNull { it.id == proveedorId } }
        .mapLatest { proveedor ->
            val dashboard = try {
                getDashboardProveedorUseCase(proveedorId, proveedor?.acopiadorId)
            } catch (e: Exception) {
                println("ProveedorDashboardViewModel: error al cargar dashboard: ${e.message}")
                null
            }
            ProveedorDashboardUiState(cargando = false, dashboard = dashboard, proveedor = proveedor)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProveedorDashboardUiState())
}
