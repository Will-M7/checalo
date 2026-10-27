package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Notificacion
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.TipoNotificacion
import com.example.ecolacteoshuata.domain.repository.ComunicadoRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import com.example.ecolacteoshuata.domain.repository.VentaRepository
import kotlinx.datetime.LocalDateTime

class GetNotificacionesUseCase(
    private val resultadoLaboratorioRepository: ResultadoLaboratorioRepository,
    private val ventaRepository: VentaRepository,
    private val recojoRepository: RecojoRepository,
    private val proveedorRepository: ProveedorRepository,
    private val comunicadoRepository: ComunicadoRepository,
) {
    suspend operator fun invoke(rol: Rol, proveedorId: String? = null): List<Notificacion> {
        val notificaciones = mutableListOf<Notificacion>()
        val hoy = todayLocalDate()
        val ahora = nowLocalDateTime()

        when (rol) {
            Rol.ADMIN, Rol.PERSONAL_PLANTA, Rol.VENDEDOR, Rol.PRODUCCION -> {
                resultadoLaboratorioRepository.getAll()
                    .filter { !it.conforme && it.fecha == hoy }
                    .forEach {
                        notificaciones += Notificacion(
                            id = it.id,
                            tipo = TipoNotificacion.LABORATORIO_NO_CONFORME,
                            titulo = "Resultado no conforme",
                            mensaje = "Se registró una alteración en el laboratorio.",
                            fecha = ahora,
                        )
                    }
                proveedorRepository.getAll().filter { it.alteraciones > 0 && !it.bloqueado }.take(5).forEach {
                    notificaciones += Notificacion(
                        id = "pago-${it.id}",
                        tipo = TipoNotificacion.PAGO_PENDIENTE,
                        titulo = "Proveedor con alteraciones",
                        mensaje = "${it.nombreCompleto} acumula ${it.alteraciones} alteración(es).",
                        fecha = ahora,
                    )
                }
                val ventasHoy = ventaRepository.countHoy(hoy)
                if (ventasHoy > 0) {
                    notificaciones += Notificacion(
                        id = "ventas-hoy",
                        tipo = TipoNotificacion.VENTA_REGISTRADA,
                        titulo = "Ventas de hoy",
                        mensaje = "Se registraron $ventasHoy venta(s) hoy por ${formatMoney(ventaRepository.sumTotalHoy(hoy))}.",
                        fecha = ahora,
                    )
                }
            }
            Rol.ACOPIADOR -> {
                val proveedoresAsignados = proveedorRepository.getAll()
                val recojosHoy = recojoRepository.getEntreFechas(hoy, hoy).map { it.proveedorId }.toSet()
                proveedoresAsignados.filter { it.acopiadorId != null && it.id !in recojosHoy }.take(5).forEach {
                    notificaciones += Notificacion(
                        id = "sin-recojo-${it.id}",
                        tipo = TipoNotificacion.SIN_ENTREGA,
                        titulo = "Proveedor sin recojo",
                        mensaje = "${it.nombreCompleto} aún no tiene recojo registrado hoy.",
                        fecha = ahora,
                    )
                }
            }
            Rol.PROVEEDOR -> {
                if (proveedorId != null) {
                    resultadoLaboratorioRepository.getByProveedor(proveedorId).filter { it.fecha == hoy }.forEach {
                        val conforme = it.conforme
                        notificaciones += Notificacion(
                            id = it.id,
                            tipo = TipoNotificacion.LABORATORIO_NO_CONFORME,
                            titulo = if (conforme) "¡Leche conforme!" else "Alerta de calidad",
                            mensaje = if (conforme) "Tu entrega de hoy fue aprobada en laboratorio." else "Tu entrega de hoy presentó una alteración.",
                            fecha = ahora,
                        )
                    }
                }
                comunicadoRepository.getAll().take(3).forEach {
                    notificaciones += Notificacion(
                        id = "comunicado-${it.id}",
                        tipo = TipoNotificacion.COMUNICADO,
                        titulo = it.titulo,
                        mensaje = it.descripcion,
                        fecha = LocalDateTime(it.fecha, kotlinx.datetime.LocalTime(8, 0)),
                    )
                }
            }
        }
        return notificaciones.sortedByDescending { it.fecha }
    }
}
