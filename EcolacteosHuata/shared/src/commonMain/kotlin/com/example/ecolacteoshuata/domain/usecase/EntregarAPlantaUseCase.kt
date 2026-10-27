package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.EntregaPlanta
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.repository.EntregaPlantaRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import kotlinx.datetime.LocalDate

data class ResumenEntrega(
    val litrosTotal: Double,
    val totalPagado: Double,
    val totalDeuda: Double,
    val proveedoresNoRecogidos: Int,
    /** Recojos de hoy que todavía no se entregaron a planta (los que entran en la entrega). */
    val pendientes: List<Recojo> = emptyList(),
)

/**
 * Igual que `acopiador/entrega` de Laravel: la entrega lleva todos los recojos de hoy del
 * acopiador que todavía no se entregaron (de cualquier turno).
 */
class GetResumenEntregaUseCase(
    private val recojoRepository: RecojoRepository,
    private val proveedorRepository: ProveedorRepository,
) {
    suspend operator fun invoke(acopiadorId: String, fecha: LocalDate = todayLocalDate()): ResumenEntrega {
        val pendientes = recojoRepository.getByAcopiadorYFecha(acopiadorId, fecha).filter { !it.entregado }
        val totalProveedores = proveedorRepository.getByAcopiador(acopiadorId).size
        val recogidos = pendientes.map { it.proveedorId }.distinct().size
        return ResumenEntrega(
            litrosTotal = redondear2(pendientes.sumOf { it.litros }),
            totalPagado = 0.0,
            totalDeuda = redondear2(pendientes.sumOf { it.total }),
            proveedoresNoRecogidos = (totalProveedores - recogidos).coerceAtLeast(0),
            pendientes = pendientes,
        )
    }
}

/**
 * Registra la entrega en planta y marca esos recojos como entregados. Se guarda en el teléfono
 * y sube a Laravel con la sincronización (planta la ve en "Entregas de acopiadores").
 */
class EntregarAPlantaUseCase(
    private val recojoRepository: RecojoRepository,
    private val entregaPlantaRepository: EntregaPlantaRepository,
    private val resumenEntregaUseCase: GetResumenEntregaUseCase,
) {
    suspend operator fun invoke(acopiadorId: String, turnoId: String, fecha: LocalDate = todayLocalDate()): AppResult<EntregaPlanta> {
        val resumen = resumenEntregaUseCase(acopiadorId, fecha)
        if (resumen.pendientes.isEmpty()) {
            return AppResult.Error("No hay recojos pendientes de entregar.")
        }
        val entrega = EntregaPlanta(
            id = IdGenerator.newId(),
            acopiadorId = acopiadorId,
            turnoId = turnoId,
            fecha = fecha,
            litrosTotal = resumen.litrosTotal,
            totalPagado = resumen.totalPagado,
            totalDeuda = resumen.totalDeuda,
            proveedoresNoRecogidos = resumen.proveedoresNoRecogidos,
            // Mismo formato que Laravel: G-AAAAMMDDhhmmss.
            guia = "G-" + nowLocalDateTime().toString().filter { it.isDigit() }.take(14).padEnd(14, '0'),
        )
        entregaPlantaRepository.crear(entrega)
        recojoRepository.marcarEntregados(resumen.pendientes.map { it.id })
        return AppResult.Success(entrega)
    }
}
