package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.endOfMonth
import com.example.ecolacteoshuata.core.util.endOfWeek
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.startOfMonth
import com.example.ecolacteoshuata.core.util.startOfWeek
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.DashboardAcopiador
import com.example.ecolacteoshuata.domain.model.DashboardAdmin
import com.example.ecolacteoshuata.domain.model.DashboardPlanta
import com.example.ecolacteoshuata.domain.model.DashboardProveedor
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import com.example.ecolacteoshuata.domain.repository.PagoProveedorRepository
import com.example.ecolacteoshuata.domain.repository.ProductoRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.domain.repository.VentaRepository

class GetDashboardAdminUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val recojoRepository: RecojoRepository,
    private val ventaRepository: VentaRepository,
) {
    suspend operator fun invoke(): DashboardAdmin {
        val hoy = todayLocalDate()
        return DashboardAdmin(
            totalUsuarios = usuarioRepository.countAll(),
            usuariosActivos = usuarioRepository.countActivos(),
            litrosHoy = redondear2(recojoRepository.sumLitrosEnFecha(hoy)),
            ventasHoyCantidad = ventaRepository.countHoy(hoy),
            ventasHoyTotal = redondear2(ventaRepository.sumTotalHoy(hoy)),
        )
    }
}

class GetDashboardPlantaUseCase(
    private val recojoRepository: RecojoRepository,
    private val acopiadorRepository: AcopiadorRepository,
    private val proveedorRepository: ProveedorRepository,
    private val productoRepository: ProductoRepository,
) {
    suspend operator fun invoke(): DashboardPlanta {
        val hoy = todayLocalDate()
        return DashboardPlanta(
            litrosRecibidosHoy = redondear2(recojoRepository.sumLitrosEnFecha(hoy)),
            cantidadAcopiadores = acopiadorRepository.getAll().size,
            cantidadProveedores = proveedorRepository.getAll().size,
            cantidadProductos = productoRepository.countActivos(),
        )
    }
}

class GetDashboardAcopiadorUseCase(
    private val recojoRepository: RecojoRepository,
    private val proveedorRepository: ProveedorRepository,
) {
    suspend operator fun invoke(acopiadorId: String): DashboardAcopiador {
        val hoy = todayLocalDate()
        val asignados = proveedorRepository.getByAcopiador(acopiadorId)
        val recojosHoy = recojoRepository.getByAcopiadorYFecha(acopiadorId, hoy)
        val recogidosIds = recojosHoy.map { it.proveedorId }.toSet()
        val deuda = redondear2(recojosHoy.filter { !it.pagado }.sumOf { it.total })
        return DashboardAcopiador(
            litrosRecogidosHoy = redondear2(recojosHoy.sumOf { it.litros }),
            proveedoresAsignados = asignados.size,
            proveedoresFaltantes = asignados.count { it.id !in recogidosIds },
            deudaTotal = deuda,
        )
    }
}

class GetDashboardProveedorUseCase(
    private val recojoRepository: RecojoRepository,
    private val acopiadorRepository: AcopiadorRepository,
    private val turnoRepository: TurnoRepository,
) {
    suspend operator fun invoke(proveedorId: String, acopiadorId: String?): DashboardProveedor {
        val hoy = todayLocalDate()
        val semana = hoy.startOfWeek() to hoy.endOfWeek()
        val mes = hoy.startOfMonth() to hoy.endOfMonth()

        val recojosHoy = recojoRepository.getByProveedorEntreFechas(proveedorId, hoy, hoy)
        val recojosSemana = recojoRepository.getByProveedorEntreFechas(proveedorId, semana.first, semana.second)
        val recojosMes = recojoRepository.getByProveedorEntreFechas(proveedorId, mes.first, mes.second)
        val pendiente = recojosMes.filter { !it.pagado }.sumOf { it.total }

        val acopiador = acopiadorId?.let { acopiadorRepository.getById(it) }
        val turno = acopiadorId?.let { turnoRepository.getAll().firstOrNull() }

        return DashboardProveedor(
            acopiadorNombre = acopiador?.nombreCompleto ?: "Sin asignar",
            acopiadorRuta = acopiador?.rutaNombre.orEmpty(),
            proximoTurno = turno?.let { "${it.nombre}: ${it.horaInicio} - ${it.horaFin}" } ?: "Sin turno asignado",
            entregadoHoyLitros = redondear2(recojosHoy.sumOf { it.litros }),
            entregadoSemanaLitros = redondear2(recojosSemana.sumOf { it.litros }),
            entregadoMesLitros = redondear2(recojosMes.sumOf { it.litros }),
            porCobrar = redondear2(pendiente),
        )
    }
}
