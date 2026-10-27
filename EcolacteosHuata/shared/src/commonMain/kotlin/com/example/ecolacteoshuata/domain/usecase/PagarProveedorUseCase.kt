package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.PagoProveedor
import com.example.ecolacteoshuata.domain.repository.PagoProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import kotlinx.datetime.LocalDate

data class ResumenPagoProveedor(
    val proveedorId: String,
    val totalPagado: Double,
    val totalPendiente: Double,
    val recojosPendientesIds: List<String>,
)

class GetResumenPagoProveedorUseCase(private val recojoRepository: RecojoRepository) {
    suspend operator fun invoke(proveedorId: String, desde: LocalDate, hasta: LocalDate): ResumenPagoProveedor {
        val recojos = recojoRepository.getByProveedorEntreFechas(proveedorId, desde, hasta)
        val pagados = recojos.filter { it.pagado }
        val pendientes = recojos.filter { !it.pagado }
        return ResumenPagoProveedor(
            proveedorId = proveedorId,
            totalPagado = redondear2(pagados.sumOf { it.total }),
            totalPendiente = redondear2(pendientes.sumOf { it.total }),
            recojosPendientesIds = pendientes.map { it.id },
        )
    }
}

class PagarProveedorUseCase(
    private val recojoRepository: RecojoRepository,
    private val pagoProveedorRepository: PagoProveedorRepository,
    private val resumenUseCase: GetResumenPagoProveedorUseCase,
) {
    suspend operator fun invoke(
        proveedorId: String,
        desde: LocalDate,
        hasta: LocalDate,
        metodoPago: MetodoPago,
        periodoEtiqueta: String,
    ): AppResult<Double> {
        val resumen = resumenUseCase(proveedorId, desde, hasta)
        if (resumen.totalPendiente <= 0.0) {
            return AppResult.Error("Este proveedor no tiene pagos pendientes en el periodo seleccionado.")
        }
        resumen.recojosPendientesIds.forEach { recojoId -> recojoRepository.marcarPagado(recojoId, metodoPago) }
        pagoProveedorRepository.crear(
            PagoProveedor(
                id = IdGenerator.newId(),
                proveedorId = proveedorId,
                recojoId = null,
                fecha = todayLocalDate(),
                monto = resumen.totalPendiente,
                metodoPago = metodoPago,
                periodo = periodoEtiqueta,
            )
        )
        return AppResult.Success(resumen.totalPendiente)
    }
}
