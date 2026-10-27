package com.example.ecolacteoshuata.presentation.acopiador.entrega

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.EntregaPlanta
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.repository.EntregaPlantaRepository
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.domain.usecase.EntregarAPlantaUseCase
import com.example.ecolacteoshuata.domain.usecase.GetResumenEntregaUseCase
import com.example.ecolacteoshuata.domain.usecase.ResumenEntrega
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Una fila de "Proveedores de la ruta" de Laravel. */
data class ProveedorDeRuta(
    val proveedor: Proveedor,
    val nombres: String,
    val apellidos: String,
    val documento: String,
    val celular: String,
    /** Litros recogidos hoy y todavía no entregados a planta. */
    val litrosHoy: Double,
    val recogidoHoy: Boolean,
)

data class AcopiadorEntregaUiState(
    val turnos: List<Turno> = emptyList(),
    val turnoSeleccionado: Turno? = null,
    val resumen: ResumenEntrega? = null,
    /** Recojos de hoy pendientes de entregar. */
    val detalle: List<Recojo> = emptyList(),
    /** Proveedores del acopiador con su estado de hoy. */
    val proveedoresRuta: List<ProveedorDeRuta> = emptyList(),
    /** Entregas a planta ya registradas hoy por este acopiador. */
    val entregasHoy: List<EntregaPlanta> = emptyList(),
    val precioLitro: Double = 0.0,
    val confirmando: Boolean = false,
    val entregando: Boolean = false,
    val error: String? = null,
    val entregaCompletada: EntregaPlanta? = null,
)

class AcopiadorEntregaViewModel(
    private val acopiadorId: String,
    private val turnoRepository: TurnoRepository,
    private val entregaPlantaRepository: EntregaPlantaRepository,
    private val precioLecheRepository: PrecioLecheRepository,
    private val getResumenEntregaUseCase: GetResumenEntregaUseCase,
    private val entregarAPlantaUseCase: EntregarAPlantaUseCase,
    private val proveedorRepository: ProveedorRepository,
    private val usuarioRepository: UsuarioRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AcopiadorEntregaUiState())
    val state: StateFlow<AcopiadorEntregaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Los turnos de ruta son generales, como en Laravel: todos los acopiadores ven todos.
            val turnos = turnoRepository.getRuta()
            val precio = precioLecheRepository.getVigente()?.precioLitro ?: 0.0
            _state.value = _state.value.copy(turnos = turnos, turnoSeleccionado = turnos.firstOrNull(), precioLitro = precio)
            cargarResumen()
        }
    }

    private suspend fun cargarResumen() {
        val resumen = getResumenEntregaUseCase(acopiadorId)
        val hoy = todayLocalDate()
        val entregasHoy = entregaPlantaRepository.getByAcopiador(acopiadorId).filter { it.fecha == hoy }
        // Como en Laravel: una vez entregado a planta ya no cuenta como "recogido hoy".
        val porProveedor = resumen.pendientes.groupBy { it.proveedorId }
        val proveedoresRuta = proveedorRepository.getByAcopiador(acopiadorId)
            .sortedBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }
            .map { p ->
                val usuario = usuarioRepository.getById(p.usuarioId)
                val recojos = porProveedor[p.id].orEmpty()
                ProveedorDeRuta(
                    proveedor = p,
                    nombres = usuario?.nombres ?: p.nombreCompleto,
                    apellidos = usuario?.apellidos.orEmpty(),
                    documento = usuario?.documento.orEmpty(),
                    celular = usuario?.celular.orEmpty(),
                    litrosHoy = recojos.sumOf { it.litros },
                    recogidoHoy = recojos.isNotEmpty(),
                )
            }
        _state.value = _state.value.copy(resumen = resumen, detalle = resumen.pendientes, entregasHoy = entregasHoy, proveedoresRuta = proveedoresRuta)
    }

    fun seleccionarTurno(turno: Turno) {
        _state.value = _state.value.copy(turnoSeleccionado = turno)
    }

    /** Como el modal "confirmar-entrega" de Laravel: se pide confirmar antes de entregar. */
    fun pedirConfirmacion(mostrar: Boolean) {
        _state.value = _state.value.copy(confirmando = mostrar, error = null)
    }

    fun entregar() {
        val turno = _state.value.turnoSeleccionado
        if (turno == null) {
            _state.value = _state.value.copy(confirmando = false, error = "Selecciona el turno de ruta de la entrega.")
            return
        }
        if (_state.value.entregando) return
        viewModelScope.launch {
            _state.value = _state.value.copy(entregando = true, error = null)
            when (val resultado = entregarAPlantaUseCase(acopiadorId, turno.id)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(confirmando = false, entregando = false, entregaCompletada = resultado.data)
                    cargarResumen()
                }
                is AppResult.Error -> _state.value = _state.value.copy(confirmando = false, entregando = false, error = resultado.message)
            }
        }
    }

    fun confirmacionConsumida() {
        _state.value = _state.value.copy(entregaCompletada = null)
    }
}
