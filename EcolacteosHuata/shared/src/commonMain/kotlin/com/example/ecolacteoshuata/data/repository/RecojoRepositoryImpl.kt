package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import com.example.ecolacteoshuata.db.Recojo as RecojoEntity

class RecojoRepositoryImpl(private val db: AppDatabase) : RecojoRepository {
    private val queries = db.recojoQueries

    private fun RecojoEntity.mapConNombre(): Recojo {
        val proveedor = db.proveedorQueries.selectById(proveedor_id).executeAsOneOrNull()
        val usuario = proveedor?.let { db.usuarioQueries.selectById(it.usuario_id).executeAsOneOrNull() }
        val nombre = usuario?.let { "${it.nombres} ${it.apellidos}" } ?: ""
        return toDomain(nombre)
    }

    override fun observePendientesSincronizar(): Flow<List<Recojo>> =
        queries.selectPendientesSincronizar().asFlow().mapToList(Dispatchers.Default)
            .map { list -> list.map { it.mapConNombre() } }

    override suspend fun getAll(): List<Recojo> = queries.selectAll().executeAsList().map { it.mapConNombre() }

    override suspend fun getById(id: String): Recojo? = queries.selectById(id).executeAsOneOrNull()?.mapConNombre()

    override fun observeByProveedor(proveedorId: String): Flow<List<Recojo>> =
        queries.selectByProveedor(proveedorId).asFlow().mapToList(Dispatchers.Default)
            .map { list -> list.map { it.mapConNombre() } }

    override suspend fun getByProveedor(proveedorId: String): List<Recojo> =
        queries.selectByProveedor(proveedorId).executeAsList().map { it.mapConNombre() }

    override suspend fun getByAcopiadorYFecha(acopiadorId: String, fecha: LocalDate): List<Recojo> =
        queries.selectByAcopiadorYFecha(acopiadorId, fecha.toString()).executeAsList().map { it.mapConNombre() }

    override suspend fun getByAcopiadorTurnoFecha(acopiadorId: String, turnoId: String, fecha: LocalDate): List<Recojo> =
        queries.selectByAcopiadorTurnoFecha(acopiadorId, turnoId, fecha.toString()).executeAsList().map { it.mapConNombre() }

    override suspend fun getByAcopiadorEntreFechas(acopiadorId: String, desde: LocalDate, hasta: LocalDate): List<Recojo> =
        queries.selectByAcopiadorEntreFechas(acopiadorId, desde.toString(), hasta.toString()).executeAsList().map { it.mapConNombre() }

    override suspend fun getByProveedorEntreFechas(proveedorId: String, desde: LocalDate, hasta: LocalDate): List<Recojo> =
        queries.selectByProveedorEntreFechas(proveedorId, desde.toString(), hasta.toString()).executeAsList().map { it.mapConNombre() }

    override suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<Recojo> =
        queries.selectEntreFechas(desde.toString(), hasta.toString()).executeAsList().map { it.mapConNombre() }

    override suspend fun crear(recojo: Recojo) {
        queries.insert(
            id = recojo.id.ifBlank { IdGenerator.newId() },
            proveedor_id = recojo.proveedorId,
            acopiador_id = recojo.acopiadorId,
            turno_id = recojo.turnoId,
            fecha = recojo.fecha.toString(),
            litros = recojo.litros,
            estado_leche = recojo.estadoLeche.name,
            estado_otro = recojo.estadoOtro,
            precio_litro = recojo.precioLitro,
            total = recojo.total,
            pagado = if (recojo.pagado) 1L else 0L,
            metodo_pago = recojo.metodoPago?.name,
            entregado = if (recojo.entregado) 1L else 0L,
            sincronizado = if (recojo.sincronizado) 1L else 0L,
        )
    }

    override suspend fun marcarPagado(id: String, metodoPago: MetodoPago) {
        queries.marcarPagado(metodoPago.name, id)
    }

    override suspend fun actualizarLitros(id: String, litros: Double, total: Double) {
        queries.actualizarLitros(litros, total, id)
    }

    override suspend fun marcarEntregados(ids: List<String>) {
        queries.transaction { ids.forEach { queries.marcarEntregado(it) } }
    }

    override suspend fun sumLitrosEnFecha(fecha: LocalDate): Double = queries.sumLitrosHoy(fecha.toString()).executeAsOne()

    override suspend fun countPendientesSincronizar(): Long = queries.countPendientesSincronizar().executeAsOne()

    override suspend fun marcarSincronizado(id: String) {
        queries.marcarSincronizado(id)
    }
}
