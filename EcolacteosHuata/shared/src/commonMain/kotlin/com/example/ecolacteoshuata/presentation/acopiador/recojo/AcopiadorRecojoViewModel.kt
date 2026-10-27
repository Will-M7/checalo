package com.example.ecolacteoshuata.presentation.acopiador.recojo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import com.example.ecolacteoshuata.domain.usecase.RegistrarRecojoInput
import com.example.ecolacteoshuata.domain.usecase.RegistrarRecojoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.ecolacteoshuata.domain.repository.RecojoRepository

data class AcopiadorRecojoUiState(
    val turnos: List<Turno> = emptyList(),
    val turnoSeleccionado: Turno? = null,
    val proveedores: List<Proveedor> = emptyList(),
    val recogidosHoy: Set<String> = emptySet(),
    val proveedorFormulario: Proveedor? = null,
    val turnoFormulario: Turno? = null,
    val litros: String = "",
    val estadoLeche: EstadoLeche = EstadoLeche.NORMAL,
    val estadoOtro: String = "",
    val pagado: Boolean = false,
    val metodoPago: MetodoPago? = null,
    val guardando: Boolean = false,
    val error: String? = null,
    val guardadoOk: Boolean = false,
    val precioLitro: Double = 0.0,
)

class AcopiadorRecojoViewModel(
    private val acopiadorId: String,
    private val turnoRepository: TurnoRepository,
    private val proveedorRepository: ProveedorRepository,
    private val recojoRepository: RecojoRepository,
    private val precioLecheRepository: PrecioLecheRepository,
    private val registrarRecojoUseCase: RegistrarRecojoUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AcopiadorRecojoUiState())
    val state: StateFlow<AcopiadorRecojoUiState> = _state.asStateFlow()

    init { cargar() }

    private fun cargar() {
        viewModelScope.launch {
            // Los turnos de ruta son generales, como en Laravel: todos los acopiadores ven todos.
            val turnos = turnoRepository.getRuta()
            val proveedores = proveedorRepository.getByAcopiador(acopiadorId)
            val turnoSeleccionado = turnos.firstOrNull()
            val precio = precioLecheRepository.getVigente()?.precioLitro ?: 0.0
            _state.value = _state.value.copy(turnos = turnos, proveedores = proveedores, turnoSeleccionado = turnoSeleccionado, precioLitro = precio)
            turnoSeleccionado?.let { cargarRecogidos(it) }
        }
    }

    private suspend fun cargarRecogidos(turno: Turno) {
        val recojos = recojoRepository.getByAcopiadorYFecha(acopiadorId, todayLocalDate()).filter { it.turnoId == turno.id }
        _state.value = _state.value.copy(recogidosHoy = recojos.map { it.proveedorId }.toSet())
    }

    fun seleccionarTurno(turno: Turno) {
        _state.value = _state.value.copy(turnoSeleccionado = turno)
        viewModelScope.launch { cargarRecogidos(turno) }
    }

    fun abrirFormulario(proveedor: Proveedor) {
        _state.value = _state.value.copy(
            proveedorFormulario = proveedor,
            turnoFormulario = _state.value.turnoSeleccionado,
            litros = "",
            estadoLeche = EstadoLeche.NORMAL,
            estadoOtro = "",
            pagado = false,
            metodoPago = null,
            error = null,
        )
    }

    fun cerrarFormulario() { _state.value = _state.value.copy(proveedorFormulario = null) }

    fun onTurnoFormularioChange(v: Turno) { _state.value = _state.value.copy(turnoFormulario = v) }
    fun onLitrosChange(v: String) { _state.value = _state.value.copy(litros = v) }
    fun onEstadoChange(v: EstadoLeche) { _state.value = _state.value.copy(estadoLeche = v) }
    fun onEstadoOtroChange(v: String) { _state.value = _state.value.copy(estadoOtro = v) }

    fun guardar() {
        val s = _state.value
        val proveedor = s.proveedorFormulario ?: return
        val turno = s.turnoFormulario ?: s.turnoSeleccionado ?: return
        viewModelScope.launch {
            _state.value = s.copy(guardando = true, error = null)
            val resultado = registrarRecojoUseCase(
                RegistrarRecojoInput(
                    proveedorId = proveedor.id,
                    acopiadorId = acopiadorId,
                    turnoId = turno.id,
                    litros = s.litros.toDoubleOrNull() ?: 0.0,
                    estadoLeche = s.estadoLeche,
                    estadoOtro = s.estadoOtro,
                    pagado = s.pagado,
                    metodoPago = s.metodoPago,
                )
            )
            when (resultado) {
                is AppResult.Success -> {
                    _state.value.turnoSeleccionado?.let { cargarRecogidos(it) }
                    _state.value = _state.value.copy(guardando = false, proveedorFormulario = null, guardadoOk = true)
                }
                is AppResult.Error -> _state.value = _state.value.copy(guardando = false, error = resultado.message)
            }
        }
    }

    fun confirmacionConsumida() { _state.value = _state.value.copy(guardadoOk = false) }
}
