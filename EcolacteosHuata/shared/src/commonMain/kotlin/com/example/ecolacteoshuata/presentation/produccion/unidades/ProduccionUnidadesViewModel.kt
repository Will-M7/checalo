package com.example.ecolacteoshuata.presentation.produccion.unidades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.UnidadMedida
import com.example.ecolacteoshuata.domain.usecase.EliminarUnidadMedidaUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarUnidadMedidaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveUnidadesMedidaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProduccionUnidadesViewModel(
    observeUnidadesMedidaUseCase: ObserveUnidadesMedidaUseCase,
    private val guardarUnidadMedidaUseCase: GuardarUnidadMedidaUseCase,
    private val eliminarUnidadMedidaUseCase: EliminarUnidadMedidaUseCase,
) : ViewModel() {
    val unidades: StateFlow<List<UnidadMedida>> = observeUnidadesMedidaUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formVisible = MutableStateFlow<UnidadMedida?>(null)
    val formVisible: StateFlow<UnidadMedida?> = _formVisible

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun abrirNuevo() { _formVisible.value = UnidadMedida("", "", "") }
    fun abrirEditar(unidad: UnidadMedida) { _formVisible.value = unidad }
    fun cerrarForm() { _formVisible.value = null; _error.value = null }

    fun guardar(unidad: UnidadMedida) {
        viewModelScope.launch {
            when (val resultado = guardarUnidadMedidaUseCase(unidad)) {
                is AppResult.Success -> cerrarForm()
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }

    /** Mensaje de error de una acción de la lista (por ejemplo, eliminar una unidad en uso). */
    private val _avisoLista = MutableStateFlow<String?>(null)
    val avisoLista: StateFlow<String?> = _avisoLista

    fun cerrarAviso() { _avisoLista.value = null }

    fun eliminar(id: String) {
        viewModelScope.launch {
            val resultado = eliminarUnidadMedidaUseCase(id)
            _avisoLista.value = (resultado as? AppResult.Error)?.message
        }
    }
}
