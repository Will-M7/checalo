package com.example.ecolacteoshuata.presentation.produccion.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.model.Producto
import com.example.ecolacteoshuata.domain.model.Receta
import com.example.ecolacteoshuata.domain.model.RecetaDetalle
import com.example.ecolacteoshuata.domain.usecase.EliminarRecetaUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarRecetaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProductosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveRecetasUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecetaFormState(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val productoId: String? = null,
    val detalle: List<RecetaDetalle> = emptyList(),
)

class ProduccionRecetasViewModel(
    observeRecetasUseCase: ObserveRecetasUseCase,
    observeInsumosUseCase: ObserveInsumosUseCase,
    observeProductosUseCase: ObserveProductosUseCase,
    private val guardarRecetaUseCase: GuardarRecetaUseCase,
    private val eliminarRecetaUseCase: EliminarRecetaUseCase,
) : ViewModel() {
    val recetas: StateFlow<List<Receta>> = observeRecetasUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val insumos: StateFlow<List<Insumo>> = observeInsumosUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productos: StateFlow<List<Producto>> = observeProductosUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formVisible = MutableStateFlow<RecetaFormState?>(null)
    val formVisible: StateFlow<RecetaFormState?> = _formVisible

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun abrirNuevo() { _formVisible.value = RecetaFormState() }
    fun abrirEditar(receta: Receta) {
        _formVisible.value = RecetaFormState(receta.id, receta.nombre, receta.descripcion, receta.productoId, receta.detalle)
    }
    fun cerrarForm() { _formVisible.value = null; _error.value = null }

    fun onNombreChange(v: String) { _formVisible.value = _formVisible.value?.copy(nombre = v) }
    fun onDescripcionChange(v: String) { _formVisible.value = _formVisible.value?.copy(descripcion = v) }
    fun onProductoChange(productoId: String?) { _formVisible.value = _formVisible.value?.copy(productoId = productoId) }

    fun agregarInsumo(insumoId: String, cantidad: Double) {
        val nombre = insumos.value.find { it.id == insumoId }?.nombre ?: ""
        val actual = _formVisible.value ?: return
        val nuevo = RecetaDetalle(id = IdGenerator.newId(), recetaId = actual.id, insumoId = insumoId, insumoNombre = nombre, cantidad = cantidad)
        _formVisible.value = actual.copy(detalle = actual.detalle + nuevo)
    }

    fun quitarInsumo(index: Int) {
        val actual = _formVisible.value ?: return
        _formVisible.value = actual.copy(detalle = actual.detalle.filterIndexed { i, _ -> i != index })
    }

    fun guardar() {
        val f = _formVisible.value ?: return
        viewModelScope.launch {
            val receta = Receta(id = f.id, nombre = f.nombre, descripcion = f.descripcion, productoId = f.productoId, detalle = f.detalle)
            when (val resultado = guardarRecetaUseCase(receta)) {
                is AppResult.Success -> cerrarForm()
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }

    fun eliminar(id: String) {
        viewModelScope.launch { eliminarRecetaUseCase(id) }
    }
}
