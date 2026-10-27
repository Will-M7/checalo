package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.codificarTrazo
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.ecolacteoshuata.db.Ruta as RutaEntity

class RutaRepositoryImpl(private val db: AppDatabase) : RutaRepository {
    private val queries = db.rutaQueries

    private fun RutaEntity.mapConProveedores(): Ruta {
        val ids = queries.selectProveedoresDeRuta(id).executeAsList()
        return toDomain(ids)
    }

    override fun observeAll(): Flow<List<Ruta>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default)
            .map { list -> list.map { it.mapConProveedores() } }

    override fun observeByAcopiador(acopiadorId: String): Flow<List<Ruta>> =
        queries.selectByAcopiador(acopiadorId).asFlow().mapToList(Dispatchers.Default)
            .map { list -> list.map { it.mapConProveedores() } }

    override suspend fun getAll(): List<Ruta> = queries.selectAll().executeAsList().map { it.mapConProveedores() }

    override suspend fun getByAcopiador(acopiadorId: String): List<Ruta> =
        queries.selectByAcopiador(acopiadorId).executeAsList().map { it.mapConProveedores() }

    override suspend fun getById(id: String): Ruta? = queries.selectById(id).executeAsOneOrNull()?.mapConProveedores()

    override suspend fun crear(ruta: Ruta) {
        queries.insert(
            id = ruta.id.ifBlank { IdGenerator.newId() },
            acopiador_id = ruta.acopiadorId,
            nombre = ruta.nombre,
            activa = if (ruta.activa) 1L else 0L,
        )
    }

    override suspend fun actualizar(ruta: Ruta) {
        queries.update(nombre = ruta.nombre, acopiador_id = ruta.acopiadorId, activa = if (ruta.activa) 1L else 0L, id = ruta.id)
    }

    override suspend fun eliminar(id: String) {
        queries.transaction {
            queries.limpiarProveedoresDeRuta(id)
            queries.deleteById(id)
        }
    }

    override suspend fun asignarProveedores(rutaId: String, proveedorIds: List<String>) {
        queries.transaction {
            queries.limpiarProveedoresDeRuta(rutaId)
            proveedorIds.forEachIndexed { indice, proveedorId -> queries.asignarProveedor(rutaId, proveedorId, indice + 1L) }
        }
    }

    override suspend fun reordenarProveedores(rutaId: String, proveedorIdsEnOrden: List<String>) {
        queries.transaction {
            proveedorIdsEnOrden.forEachIndexed { indice, proveedorId -> queries.actualizarOrden(indice + 1L, rutaId, proveedorId) }
        }
    }

    override suspend fun actualizarTrazo(rutaId: String, trazo: List<PuntoGeo>) {
        queries.actualizarTrazo(codificarTrazo(trazo), rutaId)
    }
}
