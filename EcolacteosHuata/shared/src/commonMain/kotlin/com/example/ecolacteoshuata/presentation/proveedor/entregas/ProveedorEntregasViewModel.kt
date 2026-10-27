package com.example.ecolacteoshuata.presentation.proveedor.entregas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Periodo
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import com.example.ecolacteoshuata.presentation.proveedor.rangoPeriodo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** Una fila de "Mis entregas" de Laravel. */
data class FilaEntrega(val recojo: Recojo, val turno: String?) {
    /** Lo que entregó el proveedor; si el acopiador no lo corrigió, es lo mismo que llegó. */
    val litrosEntregados: Double get() = recojo.litrosEntregados ?: recojo.litros
}

data class ProveedorEntregasUiState(
    val periodo: Periodo = Periodo.SEMANA,
    val fecha: LocalDate,
    val entregas: List<FilaEntrega> = emptyList(),
    val seleccionada: FilaEntrega? = null,
) {
    val totalLitros: Double get() = redondear2(entregas.sumOf { it.recojo.litros })
    val totalMonto: Double get() = redondear2(entregas.sumOf { it.recojo.total })
    val porCobrar: Double get() = redondear2(entregas.filter { !it.recojo.pagado }.sumOf { it.recojo.total })
}

private data class Filtro(val periodo: Periodo, val fecha: LocalDate, val seleccionadaId: String?)

/** "Mis entregas" de Laravel: historial de leche entregada y estado de pago, por período. */
class ProveedorEntregasViewModel(
    proveedorId: String,
    recojoRepository: RecojoRepository,
    turnoRepository: TurnoRepository,
) : ViewModel() {
    private val hoy = todayLocalDate()
    private val filtro = MutableStateFlow(Filtro(Periodo.SEMANA, hoy, null))

    val state: StateFlow<ProveedorEntregasUiState> = combine(
        filtro,
        recojoRepository.observeByProveedor(proveedorId),
        turnoRepository.observeAll(),
    ) { f, recojos, turnos ->
        val (desde, hasta) = rangoPeriodo(f.periodo, f.fecha)
        val nombres = turnos.associate { it.id to it.nombre }
        val entregas = recojos
            .filter { it.fecha in desde..hasta }
            .sortedByDescending { it.fecha }
            .map { FilaEntrega(it, nombres[it.turnoId]) }
        ProveedorEntregasUiState(f.periodo, f.fecha, entregas, entregas.firstOrNull { it.recojo.id == f.seleccionadaId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProveedorEntregasUiState(fecha = hoy))

    fun onPeriodo(periodo: Periodo) = filtro.update { it.copy(periodo = periodo) }

    fun onFecha(fecha: LocalDate) = filtro.update { it.copy(fecha = fecha) }

    fun verDetalle(fila: FilaEntrega) = filtro.update { it.copy(seleccionadaId = fila.recojo.id) }

    fun cerrarDetalle() = filtro.update { it.copy(seleccionadaId = null) }
}
