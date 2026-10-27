package com.example.ecolacteoshuata.presentation.produccion.productos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.domain.model.ProduccionRegistro
import com.example.ecolacteoshuata.domain.usecase.ObserveInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveRecetasUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Una producción publicada, lista para vender. */
data class ProductoTerminado(
    val registro: ProduccionRegistro,
    /** Costo de la receta por unidad: Σ cantidad del insumo × su costo unitario (como `costoTotal()` en Laravel). */
    val precioUnidad: Double,
) {
    /** Valor de lo que queda por vender, como el inventario del vendedor en Laravel. */
    val precioTotal: Double get() = redondear2(precioUnidad * registro.disponible)
}

class ProduccionProductosViewModel(
    observeProduccionUseCase: ObserveProduccionUseCase,
    observeRecetasUseCase: ObserveRecetasUseCase,
    observeInsumosUseCase: ObserveInsumosUseCase,
) : ViewModel() {
    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda

    fun onBusquedaChange(v: String) { _busqueda.value = v }

    val productos: StateFlow<List<ProductoTerminado>> = combine(
        observeProduccionUseCase(),
        observeRecetasUseCase(),
        observeInsumosUseCase(),
        _busqueda,
    ) { registros, recetas, insumos, busqueda ->
        val costos = insumos.associate { it.id to it.costoUnitario }
        val recetasPorId = recetas.associateBy { it.id }
        registros
            .filter { it.publicada }
            .filter { busqueda.isBlank() || it.nombre.contains(busqueda.trim(), ignoreCase = true) || it.recetaNombre.contains(busqueda.trim(), ignoreCase = true) }
            .map { registro ->
                val receta = recetasPorId[registro.recetaId]
                val precio = receta?.detalle?.sumOf { it.cantidad * (costos[it.insumoId] ?: 0.0) } ?: 0.0
                ProductoTerminado(registro, redondear2(precio))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
