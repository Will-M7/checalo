package com.example.ecolacteoshuata.presentation.vendedor.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.repository.VentaRepository
import com.example.ecolacteoshuata.domain.usecase.ObserveClientesUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveRecetasUseCase
import com.example.ecolacteoshuata.presentation.vendedor.historial.FilaVenta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Producto con poco stock (≤ 5 unidades), para el aviso "Stock bajo — reponer pronto". */
data class ProductoStockBajo(val nombre: String, val disponible: Double)

data class VendedorDashboardUiState(
    val ventasHoy: Long = 0,
    val totalHoy: Double = 0.0,
    val produccionHoy: Double = 0.0,
    val valorInventario: Double = 0.0,
    val stockBajo: List<ProductoStockBajo> = emptyList(),
    val ultimasVentas: List<FilaVenta> = emptyList(),
)

/** "Panel de control" del vendedor en Laravel. Se actualiza solo con cada venta o sincronización. */
class VendedorDashboardViewModel(
    ventaRepository: VentaRepository,
    observeProduccionUseCase: ObserveProduccionUseCase,
    observeRecetasUseCase: ObserveRecetasUseCase,
    observeInsumosUseCase: ObserveInsumosUseCase,
    observeClientesUseCase: ObserveClientesUseCase,
) : ViewModel() {
    val state: StateFlow<VendedorDashboardUiState> = combine(
        ventaRepository.observeRecientes(),
        observeProduccionUseCase(),
        observeRecetasUseCase(),
        observeInsumosUseCase(),
        observeClientesUseCase(),
    ) { ventas, registros, recetas, insumos, clientes ->
        val hoy = todayLocalDate()
        val ventasHoy = ventas.filter { it.fecha.date == hoy }
        val costos = insumos.associate { it.id to it.costoUnitario }
        val recetasPorId = recetas.associateBy { it.id }
        val publicadas = registros.filter { it.publicada }
        // Precio por unidad = costo de la receta (`Receta::costoTotal()` en Laravel).
        fun precioUnidad(recetaId: String) =
            recetasPorId[recetaId]?.detalle?.sumOf { it.cantidad * (costos[it.insumoId] ?: 0.0) } ?: 0.0
        val clientesPorId = clientes.associateBy { it.id }

        VendedorDashboardUiState(
            ventasHoy = ventasHoy.size.toLong(),
            totalHoy = redondear2(ventasHoy.sumOf { it.total }),
            produccionHoy = registros.filter { it.fecha == hoy }.sumOf { it.cantidadProducida },
            valorInventario = redondear2(publicadas.sumOf { it.disponible * precioUnidad(it.recetaId) }),
            stockBajo = publicadas.filter { it.disponible <= 5 }
                .sortedBy { it.disponible }
                .map { ProductoStockBajo(it.nombre.ifBlank { it.recetaNombre }, it.disponible) },
            ultimasVentas = ventas.take(5).map { venta ->
                val cliente = venta.clienteId?.let { clientesPorId[it] }?.nombreCompleto
                    ?: venta.clienteNombreLibre?.takeIf { it.isNotBlank() }
                    ?: "Venta anónima"
                FilaVenta(venta, cliente, null)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VendedorDashboardUiState())
}
