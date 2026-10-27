package com.example.ecolacteoshuata.presentation.admin.precios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.PrecioLeche
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import com.example.ecolacteoshuata.domain.usecase.ObservePrecioLecheUseCase
import com.example.ecolacteoshuata.domain.usecase.SetPrecioLecheUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Formulario "Nuevo precio por litro" + "Vigente desde" de Laravel. */
data class PrecioForm(
    val precio: String = "",
    val vigenteDesde: LocalDate = todayLocalDate(),
    val error: String? = null,
    val guardando: Boolean = false,
)

/** "Precios" del administrador en Laravel: precio vigente, registrar uno nuevo e historial. */
class AdminPreciosViewModel(
    observePrecioLecheUseCase: ObservePrecioLecheUseCase,
    precioLecheRepository: PrecioLecheRepository,
    private val setPrecioLecheUseCase: SetPrecioLecheUseCase,
) : ViewModel() {
    val precioVigente: StateFlow<PrecioLeche?> = observePrecioLecheUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val historial: StateFlow<List<PrecioLeche>> = precioLecheRepository.observeHistorial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _form = MutableStateFlow(PrecioForm())
    val form: StateFlow<PrecioForm> = _form.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun onPrecio(texto: String) = _form.update { it.copy(precio = texto, error = null) }

    fun onVigenteDesde(fecha: LocalDate) = _form.update { it.copy(vigenteDesde = fecha, error = null) }

    fun registrarPrecio() {
        val f = _form.value
        if (f.guardando) return
        _form.value = f.copy(guardando = true, error = null)
        viewModelScope.launch {
            when (val r = setPrecioLecheUseCase(f.precio.replace(',', '.').toDoubleOrNull(), f.vigenteDesde)) {
                // Como en Laravel: se limpia el precio y la fecha vuelve a hoy.
                is AppResult.Success -> {
                    _form.value = PrecioForm()
                    _mensaje.value = "Precio de leche actualizado."
                }
                is AppResult.Error -> _form.value = f.copy(guardando = false, error = r.message)
            }
        }
    }

    fun consumirMensaje() { _mensaje.value = null }
}
