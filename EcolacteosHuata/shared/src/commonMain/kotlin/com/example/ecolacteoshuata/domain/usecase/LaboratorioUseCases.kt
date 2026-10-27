package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import kotlinx.datetime.LocalDate

data class NuevoResultadoLabInput(
    /** Con id se edita ese resultado (el botón de lápiz de Laravel); sin id se registra uno nuevo. */
    val resultadoId: String? = null,
    val proveedorId: String,
    val fecha: LocalDate = todayLocalDate(),
    val turnoId: String?,
    val resultado: ResultadoTipo,
    val densidad: Double?,
    val acidez: Double?,
    val temperatura: Double?,
    val observaciones: String,
    /** Litraje que llegó a planta: pasa a ser el definitivo del recojo. */
    val litros: Double? = null,
    val grasa: Double? = null,
    val sng: Double? = null,
    val proteina: Double? = null,
    val lactosa: Double? = null,
    val sales: Double? = null,
    val totalSolidos: Double? = null,
    val aguaAnadida: Double? = null,
    val puntoCongelacion: Double? = null,
    val ph: Double? = null,
    val sn: String? = null,
    val modo: String? = null,
    val horaAnalisis: String? = null,
    val fechaAnalisis: String? = null,
)

data class RegistroLabResultado(
    val resultado: ResultadoLaboratorio,
    val alteracionesActuales: Int,
    /** Llegó a [Proveedor.MAX_ALTERACIONES] alteraciones y quedó bloqueado (`Proveedor::registrarAlteracion`). */
    val bloqueadoAutomaticamente: Boolean,
    val mensaje: String,
)

/**
 * Registra un resultado de laboratorio como en Laravel. Con [recojoRepository] además exige un
 * recojo ya entregado a planta en esa fecha y ajusta su litraje (y su total) al que midió el
 * laboratorio, salvo los recojos que ya se pagaron.
 */
class RegistrarResultadoLaboratorioUseCase(
    private val resultadoRepository: ResultadoLaboratorioRepository,
    private val proveedorRepository: ProveedorRepository,
    private val recojoRepository: RecojoRepository? = null,
) {
    suspend operator fun invoke(input: NuevoResultadoLabInput): AppResult<RegistroLabResultado> {
        val recojosDelDia = recojoRepository?.getByProveedor(input.proveedorId)
            ?.filter { it.fecha == input.fecha && it.entregado }
        if (recojosDelDia != null) {
            if (recojosDelDia.isEmpty()) return AppResult.Error("Este proveedor no tiene un recojo entregado en esta fecha.")
            if (input.litros == null || input.litros <= 0.0) return AppResult.Error("Ingresa el litraje que llegó a planta.")
        }

        val existente = input.resultadoId?.let { resultadoRepository.getById(it) }
        if (input.resultadoId != null && existente == null) return AppResult.Error("El resultado ya no existe.")
        val esNuevo = existente == null

        val conforme = input.resultado == ResultadoTipo.CONFORME
        val resultado = ResultadoLaboratorio(
            id = existente?.id ?: IdGenerator.newId(),
            proveedorId = input.proveedorId,
            fecha = input.fecha,
            turnoId = input.turnoId,
            resultado = input.resultado,
            densidad = input.densidad,
            acidez = input.acidez,
            temperatura = input.temperatura,
            observaciones = input.observaciones,
            conforme = conforme,
            litros = input.litros,
            grasa = input.grasa,
            sng = input.sng,
            proteina = input.proteina,
            lactosa = input.lactosa,
            sales = input.sales,
            totalSolidos = input.totalSolidos,
            aguaAnadida = input.aguaAnadida,
            puntoCongelacion = input.puntoCongelacion,
            ph = input.ph,
            sn = input.sn,
            modo = input.modo,
            horaAnalisis = input.horaAnalisis,
            fechaAnalisis = input.fechaAnalisis,
            enviadoProduccion = existente?.enviadoProduccion ?: false,
        )
        if (esNuevo) resultadoRepository.crear(resultado) else resultadoRepository.actualizar(resultado)

        var alteraciones = 0
        var bloqueado = false
        var mensaje = if (esNuevo) "Resultado de laboratorio registrado." else "Resultado de laboratorio actualizado."
        // Como en Laravel, solo un resultado nuevo no conforme suma una alteración; al llegar a 3 se bloquea solo.
        if (esNuevo && !conforme) {
            alteraciones = proveedorRepository.incrementarAlteraciones(input.proveedorId)
            bloqueado = alteraciones >= Proveedor.MAX_ALTERACIONES
            if (bloqueado) proveedorRepository.bloquear(input.proveedorId, true)
            mensaje += if (bloqueado) {
                " El proveedor llegó a $alteraciones alteraciones y quedó bloqueado."
            } else {
                " Se registró una alteración ($alteraciones de ${Proveedor.MAX_ALTERACIONES})."
            }
        }

        if (recojosDelDia != null && input.litros != null) {
            mensaje += if (ajustarLitraje(recojosDelDia.filter { !it.pagado }, input.litros)) {
                " Se actualizó el litraje y el pago del recojo."
            } else {
                " El recojo ya estaba pagado, no se modificó su litraje."
            }
        }

        return AppResult.Success(
            RegistroLabResultado(
                resultado = resultado,
                alteracionesActuales = alteraciones,
                bloqueadoAutomaticamente = bloqueado,
                mensaje = mensaje,
            )
        )
    }

    /** Reparte el litraje proporcionalmente entre los recojos del día, igual que Laravel. */
    private suspend fun ajustarLitraje(recojos: List<com.example.ecolacteoshuata.domain.model.Recojo>, litros: Double): Boolean {
        val repo = recojoRepository ?: return false
        if (recojos.isEmpty()) return false
        val totalOriginal = recojos.sumOf { it.litros }
        recojos.forEach { recojo ->
            val proporcion = if (totalOriginal > 0) recojo.litros / totalOriginal else 1.0 / recojos.size
            val litrosRecojo = if (recojos.size == 1) litros else redondear2(litros * proporcion)
            repo.actualizarLitros(recojo.id, litrosRecojo, redondear2(litrosRecojo * recojo.precioLitro))
        }
        return true
    }
}

class BloquearProveedorUseCase(private val repository: ProveedorRepository) {
    suspend operator fun invoke(proveedorId: String, bloquear: Boolean): AppResult<Unit> {
        repository.bloquear(proveedorId, bloquear)
        if (!bloquear) repository.resetAlteraciones(proveedorId)
        return AppResult.Success(Unit)
    }
}
