package com.example.ecolacteoshuata.presentation.produccion.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.InventarioInsumoItem
import com.example.ecolacteoshuata.domain.usecase.GetInventarioInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveComprasUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProduccionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Umbral de "stock bajo" cuando el insumo no tiene stock mínimo propio (igual que Laravel). */
const val UMBRAL_STOCK_BAJO = 10.0

fun InventarioInsumoItem.stockBajo(): Boolean = stock <= (insumo.stockMinimo.takeIf { it > 0 } ?: UMBRAL_STOCK_BAJO)

data class ProduccionDashboardUiState(
    val totalInsumos: Int = 0,
    val produccionesHoy: Int = 0,
    val porPublicar: Int = 0,
    val listosParaVender: Int = 0,
    val comprasDelMesTotal: Double = 0.0,
    /** Insumos con stock bajo, del más escaso al menos escaso. */
    val stockBajo: List<InventarioInsumoItem> = emptyList(),
)

class ProduccionDashboardViewModel(
    observeInsumosUseCase: ObserveInsumosUseCase,
    observeProduccionUseCase: ObserveProduccionUseCase,
    observeComprasUseCase: ObserveComprasUseCase,
    private val getInventarioInsumosUseCase: GetInventarioInsumosUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ProduccionDashboardUiState())
    val state: StateFlow<ProduccionDashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val hoy = todayLocalDate()
            combine(observeInsumosUseCase(), observeProduccionUseCase(), observeComprasUseCase()) { insumos, producciones, compras ->
                ProduccionDashboardUiState(
                    totalInsumos = insumos.size,
                    produccionesHoy = producciones.count { it.fecha == hoy },
                    porPublicar = producciones.count { !it.publicada },
                    listosParaVender = producciones.count { it.publicada },
                    comprasDelMesTotal = compras.filter { it.fecha.year == hoy.year && it.fecha.month == hoy.month }.sumOf { it.total },
                    stockBajo = getInventarioInsumosUseCase().filter { it.stockBajo() }.sortedBy { it.stock },
                )
            }.catch { e ->
                println("ProduccionDashboardViewModel: error al cargar dashboard: ${e.message}")
            }.collect { _state.value = it }
        }
    }
}
