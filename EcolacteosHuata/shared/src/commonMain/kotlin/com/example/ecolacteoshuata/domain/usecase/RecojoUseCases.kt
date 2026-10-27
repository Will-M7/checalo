package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.calcularTotalRecojo
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import kotlinx.datetime.LocalDate

data class RegistrarRecojoInput(
    val proveedorId: String,
    val acopiadorId: String,
    val turnoId: String,
    val fecha: LocalDate = todayLocalDate(),
    val litros: Double,
    val estadoLeche: EstadoLeche,
    val estadoOtro: String = "",
    val pagado: Boolean,
    val metodoPago: MetodoPago?,
)

class RegistrarRecojoUseCase(
    private val recojoRepository: RecojoRepository,
    private val precioLecheRepository: PrecioLecheRepository,
    private val syncService: SyncService,
) {
    suspend operator fun invoke(input: RegistrarRecojoInput): AppResult<Recojo> {
        if (input.litros <= 0.0) return AppResult.Error("Ingrese una cantidad de litros válida.")
        if (input.estadoLeche == EstadoLeche.OTRO && input.estadoOtro.isBlank()) {
            return AppResult.Error("Describa el estado de la leche.")
        }
        if (input.pagado && input.metodoPago == null) {
            return AppResult.Error("Seleccione un método de pago.")
        }
        val precioLitro = precioLecheRepository.getVigente()?.precioLitro ?: 0.0
        val total = calcularTotalRecojo(input.litros, precioLitro)
        val recojo = Recojo(
            id = IdGenerator.newId(),
            proveedorId = input.proveedorId,
            acopiadorId = input.acopiadorId,
            turnoId = input.turnoId,
            fecha = input.fecha,
            litros = input.litros,
            estadoLeche = input.estadoLeche,
            estadoOtro = input.estadoOtro,
            precioLitro = precioLitro,
            total = total,
            pagado = input.pagado,
            metodoPago = if (input.pagado) input.metodoPago else null,
            entregado = false,
            sincronizado = false,
        )
        recojoRepository.crear(recojo)
        syncService.actualizarPendientes(recojoRepository.countPendientesSincronizar().toInt())
        return AppResult.Success(recojo)
    }
}

class SincronizarPendientesUseCase(
    private val recojoRepository: RecojoRepository,
    private val syncService: SyncService,
) {
    suspend operator fun invoke() {
        syncService.sincronizarAhora()
    }
}
