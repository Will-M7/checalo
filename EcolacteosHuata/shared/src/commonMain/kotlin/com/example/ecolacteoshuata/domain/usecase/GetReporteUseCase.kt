package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.core.util.formatLitros
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.domain.model.ReporteTabla
import com.example.ecolacteoshuata.domain.model.TipoReporte
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import com.example.ecolacteoshuata.domain.repository.ClienteRepository
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import com.example.ecolacteoshuata.domain.repository.ProductoRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.domain.repository.VentaRepository
import kotlinx.datetime.LocalDate

class GetReporteUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val clienteRepository: ClienteRepository,
    private val productoRepository: ProductoRepository,
    private val precioLecheRepository: PrecioLecheRepository,
    private val ventaRepository: VentaRepository,
    private val recojoRepository: RecojoRepository,
    private val resultadoLaboratorioRepository: ResultadoLaboratorioRepository,
    private val acopiadorRepository: AcopiadorRepository,
    private val proveedorRepository: ProveedorRepository,
    private val turnoRepository: TurnoRepository,
) {
    suspend operator fun invoke(
        tipo: TipoReporte,
        desde: LocalDate,
        hasta: LocalDate,
        acopiadorId: String? = null,
        proveedorId: String? = null,
    ): ReporteTabla {
        val subtitulo = "${desde.formatDdMmmYyyy()} — ${hasta.formatDdMmmYyyy()}"
        return when (tipo) {
            TipoReporte.LECHE_POR_ACOPIADOR -> {
                val recojos = recojoRepository.getEntreFechas(desde, hasta)
                val acopiadores = acopiadorRepository.getAll().associateBy { it.id }
                val filas = recojos.groupBy { it.acopiadorId }.map { (id, lista) ->
                    listOf(
                        acopiadores[id]?.nombreCompleto ?: id,
                        formatLitros(lista.sumOf { it.litros }),
                        formatMoney(lista.sumOf { it.total }),
                    )
                }
                ReporteTabla("Leche por acopiador", subtitulo, listOf("Acopiador", "Litros", "Total"), filas)
            }
            TipoReporte.USUARIOS -> {
                val filas = usuarioRepository.getAll().map {
                    listOf(it.nombreCompleto, it.autorizacion?.roles?.joinToString { r -> r.nombre } ?: it.rol?.etiqueta.orEmpty(), it.usuario, if (it.activo) "Activo" else "Inactivo")
                }
                ReporteTabla("Usuarios registrados", subtitulo, listOf("Nombre", "Rol", "Usuario", "Estado"), filas)
            }
            TipoReporte.CLIENTES_FRECUENTES -> {
                val filas = clienteRepository.getAll().sortedByDescending { it.comprasCount }.map {
                    listOf(it.nombreCompleto, it.documento, it.comprasCount.toString(), if (it.favorito) "Sí" else "No")
                }
                ReporteTabla("Clientes y compra frecuente", subtitulo, listOf("Cliente", "Documento", "Compras", "Favorito"), filas)
            }
            TipoReporte.PRODUCTOS -> {
                val filas = productoRepository.getAll().map {
                    listOf(it.nombre, formatMoney(it.precio), it.disponible.toString().removeSuffix(".0"))
                }
                ReporteTabla("Productos", subtitulo, listOf("Producto", "Precio", "Disponible"), filas)
            }
            TipoReporte.PRECIOS -> {
                val filas = precioLecheRepository.getAll().map { listOf(formatMoney(it.precioLitro), it.vigenteDesde.formatDdMmmYyyy()) }
                ReporteTabla("Historial de precios de leche", subtitulo, listOf("Precio por litro", "Vigente desde"), filas)
            }
            TipoReporte.VENTAS -> {
                val ventas = ventaRepository.getEntreFechas(desde, hasta)
                val filas = ventas.map {
                    listOf(it.numeroBoleta, it.nombreClienteMostrar, formatMoney(it.total), it.metodoPago.name, it.fecha.formatDdMmmYyyy())
                }
                ReporteTabla(
                    "Ventas", subtitulo, listOf("Boleta", "Cliente", "Total", "Pago", "Fecha"), filas,
                    totales = listOf("Total", "", formatMoney(ventas.sumOf { it.total }), "", ""),
                )
            }
            TipoReporte.RECEPCION_PLANTA -> {
                val recojos = recojoRepository.getEntreFechas(desde, hasta)
                val filas = recojos.groupBy { it.fecha }.toList().sortedBy { it.first }.map { (fecha, lista) ->
                    listOf(fecha.formatDdMmmYyyy(), formatLitros(lista.sumOf { it.litros }), lista.size.toString())
                }
                ReporteTabla("Recepción en planta", subtitulo, listOf("Fecha", "Litros", "Recojos"), filas)
            }
            TipoReporte.DESCUENTOS_APLICADOS -> {
                val ventas = ventaRepository.getEntreFechas(desde, hasta).filter { it.descuento > 0 }
                val filas = ventas.map {
                    listOf(it.numeroBoleta, it.nombreClienteMostrar, formatMoney(it.descuento), it.descuentoTipo.ifBlank { "—" })
                }
                ReporteTabla("Descuentos aplicados", subtitulo, listOf("Boleta", "Cliente", "Descuento", "Tipo"), filas)
            }
            TipoReporte.PROVEEDORES_PAGADOS_POR_PAGAR -> {
                val recojos = recojoRepository.getEntreFechas(desde, hasta)
                val proveedores = proveedorRepository.getAll().associateBy { it.id }
                val filas = recojos.groupBy { it.proveedorId }.map { (id, lista) ->
                    listOf(
                        proveedores[id]?.nombreCompleto ?: id,
                        formatMoney(lista.filter { it.pagado }.sumOf { it.total }),
                        formatMoney(lista.filter { !it.pagado }.sumOf { it.total }),
                    )
                }
                ReporteTabla("Proveedores pagados / por pagar", subtitulo, listOf("Proveedor", "Pagado", "Pendiente"), filas)
            }
            TipoReporte.RESULTADOS_LABORATORIO -> {
                val resultados = resultadoLaboratorioRepository.getAll().filter { it.fecha in desde..hasta }
                val proveedores = proveedorRepository.getAll().associateBy { it.id }
                val filas = resultados.map {
                    listOf(
                        proveedores[it.proveedorId]?.nombreCompleto ?: it.proveedorId,
                        it.fecha.formatDdMmmYyyy(),
                        it.resultado.name,
                        if (it.conforme) "Sí" else "No",
                    )
                }
                ReporteTabla("Resultados de laboratorio", subtitulo, listOf("Proveedor", "Fecha", "Resultado", "Conforme"), filas)
            }
            TipoReporte.LECHE_POR_PROVEEDOR -> {
                val recojos = if (acopiadorId != null) recojoRepository.getByAcopiadorEntreFechas(acopiadorId, desde, hasta)
                else recojoRepository.getEntreFechas(desde, hasta)
                val proveedores = proveedorRepository.getAll().associateBy { it.id }
                val filas = recojos.groupBy { it.proveedorId }.map { (id, lista) ->
                    listOf(
                        proveedores[id]?.nombreCompleto ?: id,
                        formatLitros(lista.sumOf { it.litros }),
                        formatMoney(lista.sumOf { it.total }),
                        if (lista.all { it.pagado }) "Pagado" else "Con deuda",
                    )
                }
                ReporteTabla("Leche por proveedor", subtitulo, listOf("Proveedor", "Litros", "Total", "Estado"), filas)
            }
            TipoReporte.LITROS_POR_TURNO -> {
                val recojos = if (acopiadorId != null) recojoRepository.getByAcopiadorEntreFechas(acopiadorId, desde, hasta)
                else recojoRepository.getEntreFechas(desde, hasta)
                val turnos = turnoRepository.getAll().associateBy { it.id }
                val filas = recojos.groupBy { it.turnoId }.map { (id, lista) ->
                    listOf(turnos[id]?.nombre ?: id, formatLitros(lista.sumOf { it.litros }))
                }
                ReporteTabla("Litros por turno", subtitulo, listOf("Turno", "Litros"), filas)
            }
            TipoReporte.ENTREGAS_PROVEEDOR -> {
                val recojos = if (proveedorId != null) recojoRepository.getByProveedorEntreFechas(proveedorId, desde, hasta) else emptyList()
                val filas = recojos.sortedBy { it.fecha }.map {
                    listOf(it.fecha.formatDdMmmYyyy(), formatLitros(it.litros), formatMoney(it.total), if (it.pagado) "Pagado" else "Deuda")
                }
                val ganancia = redondear2(recojos.sumOf { it.total })
                val deuda = redondear2(recojos.filter { !it.pagado }.sumOf { it.total })
                ReporteTabla(
                    "Mis entregas", subtitulo, listOf("Fecha", "Litros", "Total", "Estado"), filas,
                    totales = listOf("Ganancia: ${formatMoney(ganancia)}", "", "Deuda: ${formatMoney(deuda)}", ""),
                )
            }
        }
    }
}
