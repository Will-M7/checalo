package com.example.ecolacteoshuata.presentation.planta.laboratorio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import com.example.ecolacteoshuata.domain.repository.TurnoProduccionRepository
import com.example.ecolacteoshuata.domain.usecase.BloquearProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.GenerarResultadoLabPdfUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProveedoresUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una fila de "Resultados de laboratorio" de Laravel. */
data class FilaResultadoLab(val resultado: ResultadoLaboratorio, val proveedor: Proveedor?, val turno: String?) {
    val nombreProveedor: String get() = proveedor?.nombreCompleto ?: "Proveedor"

    /** "Enviar a producción" solo aparece en resultados conformes y todavía no enviados. */
    val puedeEnviarProduccion: Boolean get() = resultado.conforme && !resultado.enviadoProduccion && resultado.litros != null
}

data class PlantaLaboratorioUiState(
    val resultados: List<FilaResultadoLab> = emptyList(),
    /** Solo los proveedores cuyo acopiador ya entregó a planta (los que se pueden evaluar). */
    val proveedoresEntregados: List<Proveedor> = emptyList(),
    val eligiendoProveedor: Boolean = false,
    val detalle: FilaResultadoLab? = null,
    val confirmarEnviar: FilaResultadoLab? = null,
    val mensaje: String? = null,
)

private data class ControlLaboratorio(
    val eligiendoProveedor: Boolean = false,
    val detalleId: String? = null,
    val confirmarEnviarId: String? = null,
    val mensaje: String? = null,
)

/** Laboratorio de planta como en Laravel: los últimos 30 resultados y sus acciones. */
class PlantaLaboratorioViewModel(
    observeProveedoresUseCase: ObserveProveedoresUseCase,
    private val resultadoLaboratorioRepository: ResultadoLaboratorioRepository,
    private val recojoRepository: RecojoRepository,
    turnoProduccionRepository: TurnoProduccionRepository,
    private val bloquearProveedorUseCase: BloquearProveedorUseCase,
    private val generarResultadoLabPdfUseCase: GenerarResultadoLabPdfUseCase,
) : ViewModel() {
    private val control = MutableStateFlow(ControlLaboratorio())

    val state: StateFlow<PlantaLaboratorioUiState> = combine(
        control,
        resultadoLaboratorioRepository.observeRecientes(),
        observeProveedoresUseCase(),
        turnoProduccionRepository.observeAll(),
    ) { c, resultados, proveedores, turnos ->
        val porId = proveedores.associateBy { it.id }
        val nombresTurno = turnos.associate { it.id to it.nombre }
        val filas = resultados.map { FilaResultadoLab(it, porId[it.proveedorId], it.turnoId?.let { id -> nombresTurno[id] }) }
        val conEntrega = recojoRepository.getAll().filter { it.entregado }.map { it.proveedorId }.toSet()
        PlantaLaboratorioUiState(
            resultados = filas,
            proveedoresEntregados = proveedores.filter { it.id in conEntrega }.sortedBy { it.nombreCompleto.lowercase() },
            eligiendoProveedor = c.eligiendoProveedor,
            detalle = filas.firstOrNull { it.resultado.id == c.detalleId },
            confirmarEnviar = filas.firstOrNull { it.resultado.id == c.confirmarEnviarId },
            mensaje = c.mensaje,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlantaLaboratorioUiState())

    fun nuevoResultado() = control.update { it.copy(eligiendoProveedor = true) }

    fun cerrarEleccion() = control.update { it.copy(eligiendoProveedor = false) }

    fun verDetalle(fila: FilaResultadoLab) = control.update { it.copy(detalleId = fila.resultado.id) }

    fun cerrarDetalle() = control.update { it.copy(detalleId = null) }

    fun pedirEnviarProduccion(fila: FilaResultadoLab) = control.update { it.copy(confirmarEnviarId = fila.resultado.id) }

    fun cancelarEnviar() = control.update { it.copy(confirmarEnviarId = null) }

    /** Se marca en el teléfono; al sincronizar, Laravel suma la leche al stock del insumo. */
    fun enviarProduccion() {
        val id = control.value.confirmarEnviarId ?: return
        viewModelScope.launch {
            resultadoLaboratorioRepository.marcarEnviadoProduccion(id)
            control.update { it.copy(confirmarEnviarId = null, mensaje = "Leche enviada a producción: se sumará al stock de insumos al sincronizar.") }
        }
    }

    fun desbloquear(proveedor: Proveedor) {
        viewModelScope.launch {
            bloquearProveedorUseCase(proveedor.id, false)
            control.update { it.copy(mensaje = "Proveedor desbloqueado.") }
        }
    }

    fun descargarPdf(fila: FilaResultadoLab) {
        viewModelScope.launch {
            val ruta = generarResultadoLabPdfUseCase(fila.resultado, fila.nombreProveedor, fila.turno)
            control.update { it.copy(mensaje = "PDF guardado en $ruta") }
        }
    }

    fun consumirMensaje() = control.update { it.copy(mensaje = null) }
}
