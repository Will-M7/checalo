package com.example.ecolacteoshuata.presentation.proveedor.laboratorio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Periodo
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import com.example.ecolacteoshuata.presentation.proveedor.rangoPeriodo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

data class FilaResultado(val resultado: ResultadoLaboratorio, val turno: String?)

data class ProveedorLaboratorioUiState(
    val periodo: Periodo = Periodo.TURNO,
    val fecha: LocalDate,
    val resultados: List<FilaResultado> = emptyList(),
) {
    val conformes: Int get() = resultados.count { it.resultado.conforme }
    val alteraciones: Int get() = resultados.size - conformes
}

/** "Resultados de laboratorio" de Laravel: calidad de la leche entregada, por período. */
class ProveedorLaboratorioViewModel(
    proveedorId: String,
    resultadoLaboratorioRepository: ResultadoLaboratorioRepository,
    turnoRepository: TurnoRepository,
) : ViewModel() {
    private val hoy = todayLocalDate()

    // Como en Laravel, se abre en "Turno" (lo de hoy).
    private val filtro = MutableStateFlow(Periodo.TURNO to hoy)

    val state: StateFlow<ProveedorLaboratorioUiState> = combine(
        filtro,
        resultadoLaboratorioRepository.observeByProveedor(proveedorId),
        turnoRepository.observeAll(),
    ) { (periodo, fecha), resultados, turnos ->
        val (desde, hasta) = rangoPeriodo(periodo, fecha)
        val nombres = turnos.associate { it.id to it.nombre }
        ProveedorLaboratorioUiState(
            periodo = periodo,
            fecha = fecha,
            resultados = resultados
                .filter { it.fecha in desde..hasta }
                .sortedByDescending { it.fecha }
                .map { FilaResultado(it, it.turnoId?.let { id -> nombres[id] }) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProveedorLaboratorioUiState(fecha = hoy))

    fun onPeriodo(periodo: Periodo) = filtro.update { periodo to it.second }

    fun onFecha(fecha: LocalDate) = filtro.update { it.first to fecha }
}
