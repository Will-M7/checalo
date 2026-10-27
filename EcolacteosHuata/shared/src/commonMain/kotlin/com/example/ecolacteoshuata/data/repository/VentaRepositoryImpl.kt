package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Venta
import com.example.ecolacteoshuata.domain.repository.VentaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class VentaRepositoryImpl(private val db: AppDatabase) : VentaRepository {
    private val queries = db.ventaQueries

    private fun com.example.ecolacteoshuata.db.Venta.mapConItems(): Venta {
        val items = queries.selectItemsByVenta(id).executeAsList().map { it.toDomain() }
        return toDomain(items)
    }

    override suspend fun eliminar(ids: List<String>) {
        db.transaction {
            ids.forEach { id ->
                queries.deleteItemsByVenta(id)
                queries.deleteById(id)
            }
        }
    }

    override fun observeRecientes(): Flow<List<Venta>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.mapConItems() } }

    override suspend fun getAll(): List<Venta> = queries.selectAll().executeAsList().map { it.mapConItems() }

    override suspend fun getById(id: String): Venta? = queries.selectById(id).executeAsOneOrNull()?.mapConItems()

    override suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<Venta> {
        val desdeStr = "${desde}T00:00:00"
        val hastaStr = "${hasta}T23:59:59"
        return queries.selectEntreFechas(desdeStr, hastaStr).executeAsList().map { it.mapConItems() }
    }

    override suspend fun crear(venta: Venta): Venta {
        val ventaId = venta.id.ifBlank { IdGenerator.newId() }
        queries.transaction {
            queries.insert(
                id = ventaId,
                numero_boleta = venta.numeroBoleta,
                cliente_id = venta.clienteId,
                cliente_nombre_libre = venta.clienteNombreLibre,
                cliente_doc_libre = venta.clienteDocLibre,
                fecha = venta.fecha.toString(),
                subtotal = venta.subtotal,
                descuento = venta.descuento,
                descuento_tipo = venta.descuentoTipo,
                total = venta.total,
                metodo_pago = venta.metodoPago.name,
                efectivo_recibido = venta.efectivoRecibido,
                vuelto = venta.vuelto,
                vendedor_id = venta.vendedorId,
            )
            venta.items.forEach { item ->
                queries.insertItem(
                    id = item.id.ifBlank { IdGenerator.newId() },
                    venta_id = ventaId,
                    producto_id = item.produccionId.orEmpty(),
                    nombre = item.nombre,
                    precio_unitario = item.precioUnitario,
                    cantidad = item.cantidad.toLong(),
                    subtotal = item.subtotal,
                    produccion_id = item.produccionId,
                )
                // Como el `VentaItem::created` de Laravel: lo vendido se descuenta de la producción.
                item.produccionId?.let { db.produccionQueries.sumarVendido(item.cantidad.toDouble(), it) }
            }
            // Clear the prior Yape draft only as part of a successfully committed local sale.
            venta.vendedorId?.let { db.borradorVentaYapeQueries.eliminar(it) }
        }
        return venta.copy(id = ventaId)
    }

    override suspend fun countHoy(fecha: LocalDate): Long {
        val desde = "${fecha}T00:00:00"
        val hasta = "${fecha}T23:59:59"
        return queries.selectEntreFechas(desde, hasta).executeAsList().size.toLong()
    }

    override suspend fun sumTotalHoy(fecha: LocalDate): Double {
        val desde = "${fecha}T00:00:00"
        val hasta = "${fecha}T23:59:59"
        return queries.selectEntreFechas(desde, hasta).executeAsList().sumOf { it.total }
    }

    override suspend fun countAll(): Long = queries.countAll().executeAsOne()

    /**
     * Mismo formato que Laravel (B001-000123). Es provisional: al subir la venta, Laravel le da el
     * siguiente correlativo del servidor y la boleta baja con ese número.
     */
    override suspend fun siguienteNumeroBoleta(): String {
        val siguiente = countAll() + 1
        return "B001-${siguiente.toString().padStart(6, '0')}"
    }
}
