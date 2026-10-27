package com.example.ecolacteoshuata.presentation.admin.descuentos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Descuento
import com.example.ecolacteoshuata.domain.repository.DescuentoRepository
import com.example.ecolacteoshuata.domain.usecase.GuardarDescuentoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Formulario "Nuevo descuento" / "Editar descuento" (el modal de Laravel). */
data class DescuentoForm(
    val id: String? = null,
    val nombre: String = "",
    val porcentaje: String = "",
    val activo: Boolean = true,
    val error: String? = null,
)

class AdminDescuentosViewModel(
    private val repository: DescuentoRepository,
    private val guardarDescuentoUseCase: GuardarDescuentoUseCase,
) : ViewModel() {
    val descuentos: StateFlow<List<Descuento>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _form = MutableStateFlow<DescuentoForm?>(null)
    val form: StateFlow<DescuentoForm?> = _form.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun nuevo() { _form.value = DescuentoForm() }

    fun editar(d: Descuento) {
        _form.value = DescuentoForm(id = d.id, nombre = d.nombre, porcentaje = formatearPorcentaje(d.porcentaje), activo = d.activo)
    }

    fun cerrar() { _form.value = null }

    fun onNombre(v: String) { _form.value = _form.value?.copy(nombre = v, error = null) }
    fun onPorcentaje(v: String) { _form.value = _form.value?.copy(porcentaje = v, error = null) }
    fun onActivo(v: Boolean) { _form.value = _form.value?.copy(activo = v) }

    fun guardar() {
        val f = _form.value ?: return
        viewModelScope.launch {
            when (val r = guardarDescuentoUseCase(f.id, f.nombre, f.porcentaje, f.activo)) {
                is AppResult.Success -> {
                    _form.value = null
                    _mensaje.value = "Descuento guardado correctamente."
                }
                is AppResult.Error -> _form.value = f.copy(error = r.message)
            }
        }
    }

    /** Tocar el estado lo cambia entre Disponible y Oculto, como el badge de Laravel. */
    fun alternarActivo(d: Descuento) {
        viewModelScope.launch { repository.setActivo(d.id, !d.activo) }
    }

    fun eliminar(d: Descuento) {
        viewModelScope.launch {
            repository.eliminar(d.id)
            _mensaje.value = "Descuento eliminado."
        }
    }

    fun consumirMensaje() { _mensaje.value = null }
}

/** 5.0 se muestra como "5"; 12.5 se queda igual. */
fun formatearPorcentaje(p: Double): String = if (p % 1.0 == 0.0) p.toLong().toString() else p.toString()
