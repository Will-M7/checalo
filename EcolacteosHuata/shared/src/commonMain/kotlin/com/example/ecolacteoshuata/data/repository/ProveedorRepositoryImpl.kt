package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.ecolacteoshuata.db.Proveedor as ProveedorEntity

class ProveedorRepositoryImpl(private val db: AppDatabase) : ProveedorRepository {
    private val queries = db.proveedorQueries

    private fun ProveedorEntity.mapConNombre(): Proveedor {
        val usuario = db.usuarioQueries.selectById(usuario_id).executeAsOneOrNull()
        val nombre = usuario?.let { "${it.nombres} ${it.apellidos}" } ?: ""
        return toDomain(nombre)
    }

    override fun observeAll(): Flow<List<Proveedor>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.mapConNombre() } }

    override suspend fun getAll(): List<Proveedor> = queries.selectAll().executeAsList().map { it.mapConNombre() }

    override suspend fun getById(id: String): Proveedor? = queries.selectById(id).executeAsOneOrNull()?.mapConNombre()

    override suspend fun getByUsuarioId(usuarioId: String): Proveedor? =
        queries.selectByUsuarioId(usuarioId).executeAsOneOrNull()?.mapConNombre()

    override suspend fun getByAcopiador(acopiadorId: String): List<Proveedor> =
        queries.selectByAcopiador(acopiadorId).executeAsList().map { it.mapConNombre() }

    override suspend fun crear(proveedor: Proveedor) {
        queries.insert(
            id = proveedor.id.ifBlank { IdGenerator.newId() },
            usuario_id = proveedor.usuarioId,
            finca_ubicacion = proveedor.fincaUbicacion,
            finca_referencia = proveedor.fincaReferencia,
            latitud = proveedor.latitud,
            longitud = proveedor.longitud,
            acopiador_id = proveedor.acopiadorId,
            bloqueado = if (proveedor.bloqueado) 1L else 0L,
            alteraciones = proveedor.alteraciones.toLong(),
        )
    }

    override suspend fun actualizarFinca(id: String, ubicacion: String, referencia: String, lat: Double?, lon: Double?) {
        queries.updateFinca(finca_ubicacion = ubicacion, finca_referencia = referencia, latitud = lat, longitud = lon, id = id)
    }

    override suspend fun asignarAcopiador(id: String, acopiadorId: String?) {
        queries.updateAcopiador(acopiador_id = acopiadorId, id = id)
    }

    override suspend fun incrementarAlteraciones(id: String): Int {
        queries.incrementarAlteraciones(id)
        return queries.selectById(id).executeAsOneOrNull()?.alteraciones?.toInt() ?: 0
    }

    override suspend fun resetAlteraciones(id: String) {
        queries.resetAlteraciones(id)
    }

    override suspend fun bloquear(id: String, bloqueado: Boolean) {
        queries.setBloqueado(if (bloqueado) 1L else 0L, id)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}
