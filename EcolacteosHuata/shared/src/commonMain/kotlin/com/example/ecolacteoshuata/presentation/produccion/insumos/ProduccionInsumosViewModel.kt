package com.example.ecolacteoshuata.presentation.produccion.insumos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Compra
import com.example.ecolacteoshuata.domain.model.CompraDetalle
import com.example.ecolacteoshuata.domain.usecase.RegistrarCompraUseCase
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.model.UnidadMedida
import com.example.ecolacteoshuata.domain.usecase.EliminarInsumoUseCase
import com.example.ecolacteoshuata.domain.usecase.GetInventarioInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarInsumoUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveUnidadesMedidaUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ProduccionInsumosViewModel(
    observeInsumosUseCase: ObserveInsumosUseCase,
    observeUnidadesMedidaUseCase: ObserveUnidadesMedidaUseCase,
    private val guardarInsumoUseCase: GuardarInsumoUseCase,
    private val eliminarInsumoUseCase: EliminarInsumoUseCase,
    private val getInventarioInsumosUseCase: GetInventarioInsumosUseCase,
    private val registrarCompraUseCase: RegistrarCompraUseCase,
) : ViewModel() {
    /** `stockActual` es el stock real (lo suben las compras y lo bajan las producciones), como en Laravel. */
    val insumos: StateFlow<List<Insumo>> = observeInsumosUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unidades: StateFlow<List<UnidadMedida>> = observeUnidadesMedidaUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formVisible = MutableStateFlow<Insumo?>(null)
    val formVisible: StateFlow<Insumo?> = _formVisible

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun abrirNuevo() { _formVisible.value = Insumo("", "", "", "", 0.0) }
    fun abrirEditar(insumo: Insumo) { _formVisible.value = insumo }
    fun cerrarForm() { _formVisible.value = null; _error.value = null }

    fun guardar(insumo: Insumo) {
        if (insumo.stockActual < 0) {
            _error.value = "El stock actual no puede ser negativo."
            return
        }
        viewModelScope.launch {
            when (val resultado = guardarInsumoUseCase(insumo)) {
                is AppResult.Success -> cerrarForm()
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }

    fun eliminar(id: String) {
        viewModelScope.launch { eliminarInsumoUseCase(id) }
    }

    // ----- Registrar compra de insumo (como Laravel) -----
    private val _compraInsumo = MutableStateFlow<Insumo?>(null)
    val compraInsumo: StateFlow<Insumo?> = _compraInsumo

    fun abrirCompra(insumo: Insumo) { _compraInsumo.value = insumo; _error.value = null }
    fun cerrarCompra() { _compraInsumo.value = null; _error.value = null }

    /**
     * Suma la compra al stock y recalcula el costo unitario como promedio ponderado entre lo
     * que había y lo que entra, igual que `Insumo::registrarCompra()` en Laravel.
     */
    fun registrarCompra(cantidad: Double?, costoUnitario: Double?) {
        val insumo = _compraInsumo.value ?: return
        if (cantidad == null || cantidad <= 0) { _error.value = "Ingresa una cantidad mayor a 0."; return }
        if (costoUnitario == null || costoUnitario < 0) { _error.value = "Ingresa el costo unitario pagado."; return }
        viewModelScope.launch {
            val compra = Compra(
                id = "",
                fecha = todayLocalDate(),
                proveedorInsumo = "",
                descripcion = "Compra de ${insumo.nombre}",
                total = redondear2(cantidad * costoUnitario),
                detalle = listOf(
                    CompraDetalle(
                        id = IdGenerator.newId(), compraId = "", insumoId = insumo.id, insumoNombre = insumo.nombre,
                        cantidad = cantidad, costoUnitario = costoUnitario, subtotal = redondear2(cantidad * costoUnitario),
                    ),
                ),
            )
            when (val resultado = registrarCompraUseCase(compra)) {
                // El repositorio suma la compra al stock y recalcula el costo promedio.
                is AppResult.Success -> cerrarCompra()
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }
}
