package com.example.ecolacteoshuata.presentation.planta.comunicados

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Comunicado
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.TipoComunicado
import com.example.ecolacteoshuata.domain.usecase.EliminarComunicadoUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveComunicadosUseCase
import com.example.ecolacteoshuata.domain.usecase.PublicarComunicadoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlantaComunicadosViewModel(
    private val autorId: String,
    observeComunicadosUseCase: ObserveComunicadosUseCase,
    private val publicarComunicadoUseCase: PublicarComunicadoUseCase,
    private val eliminarComunicadoUseCase: EliminarComunicadoUseCase,
) : ViewModel() {
    val comunicados: StateFlow<List<Comunicado>> = observeComunicadosUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mostrarForm = MutableStateFlow(false)
    val mostrarForm: StateFlow<Boolean> = _mostrarForm

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun abrirForm() { _mostrarForm.value = true }
    fun cerrarForm() { _mostrarForm.value = false; _error.value = null }

    /** [roles] `null` = "Todos los roles". */
    fun publicar(tipo: TipoComunicado, titulo: String, descripcion: String, roles: Set<Rol>?) {
        viewModelScope.launch {
            when (val resultado = publicarComunicadoUseCase(tipo, titulo, descripcion, autorId, roles)) {
                is AppResult.Success -> cerrarForm()
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }

    fun eliminar(id: String) {
        viewModelScope.launch { eliminarComunicadoUseCase(id) }
    }
}
