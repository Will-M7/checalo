package com.example.ecolacteoshuata.presentation.planta.pagos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.endOfMonth
import com.example.ecolacteoshuata.core.util.endOfWeek
import com.example.ecolacteoshuata.core.util.endOfYear
import com.example.ecolacteoshuata.core.util.startOfMonth
import com.example.ecolacteoshuata.core.util.startOfWeek
import com.example.ecolacteoshuata.core.util.startOfYear
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Periodo
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.usecase.GetResumenPagoProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.PagarProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.ResumenPagoProveedor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilaPago(val proveedor: Proveedor, val resumen: ResumenPagoProveedor)

data class PlantaPagosUiState(
    val periodo: Periodo = Periodo.DIA,
    val cargando: Boolean = true,
    val filas: List<FilaPago> = emptyList(),
    val mensaje: String? = null,
)

class PlantaPagosViewModel(
    private val proveedorRepository: ProveedorRepository,
    private val getResumenPagoProveedorUseCase: GetResumenPagoProveedorUseCase,
    private val pagarProveedorUseCase: PagarProveedorUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(PlantaPagosUiState())
    val state: StateFlow<PlantaPagosUiState> = _state.asStateFlow()

    init { cargar() }

    private fun rango(periodo: Periodo): Pair<kotlinx.datetime.LocalDate, kotlinx.datetime.LocalDate> {
        val hoy = todayLocalDate()
        return when (periodo) {
            Periodo.TURNO, Periodo.DIA -> hoy to hoy
            Periodo.SEMANA -> hoy.startOfWeek() to hoy.endOfWeek()
            Periodo.MES -> hoy.startOfMonth() to hoy.endOfMonth()
            Periodo.ANIO -> hoy.startOfYear() to hoy.endOfYear()
        }
    }

    fun seleccionarPeriodo(periodo: Periodo) {
        _state.value = _state.value.copy(periodo = periodo)
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _state.value = _state.value.copy(cargando = true)
            val (desde, hasta) = rango(_state.value.periodo)
            val proveedores = proveedorRepository.getAll()
            val filas = proveedores.map { FilaPago(it, getResumenPagoProveedorUseCase(it.id, desde, hasta)) }
            _state.value = _state.value.copy(cargando = false, filas = filas)
        }
    }

    fun pagar(proveedor: Proveedor, metodoPago: MetodoPago) {
        viewModelScope.launch {
            val (desde, hasta) = rango(_state.value.periodo)
            val resultado = pagarProveedorUseCase(proveedor.id, desde, hasta, metodoPago, etiquetaPeriodoActual())
            if (resultado is com.example.ecolacteoshuata.core.util.AppResult.Success) {
                _state.value = _state.value.copy(mensaje = "Pago registrado a ${proveedor.nombreCompleto}")
                cargar()
            }
        }
    }

    private fun etiquetaPeriodoActual(): String = when (_state.value.periodo) {
        Periodo.TURNO -> "Turno"
        Periodo.DIA -> "Día"
        Periodo.SEMANA -> "Semana"
        Periodo.MES -> "Mes"
        Periodo.ANIO -> "Año"
    }

    fun mensajeConsumido() { _state.value = _state.value.copy(mensaje = null) }
}
