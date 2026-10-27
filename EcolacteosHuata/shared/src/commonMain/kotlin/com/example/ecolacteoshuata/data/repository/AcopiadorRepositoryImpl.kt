package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.ecolacteoshuata.db.Acopiador as AcopiadorEntity

class AcopiadorRepositoryImpl(private val db: AppDatabase) : AcopiadorRepository {
    private val queries = db.acopiadorQueries

    private fun AcopiadorEntity.mapConNombre(): Acopiador {
        val usuario = db.usuarioQueries.selectById(usuario_id).executeAsOneOrNull()
        val nombre = usuario?.let { "${it.nombres} ${it.apellidos}" } ?: ""
        return toDomain(nombre)
    }

    override fun observeAll(): Flow<List<Acopiador>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.mapConNombre() } }

    override suspend fun getAll(): List<Acopiador> = queries.selectAll().executeAsList().map { it.mapConNombre() }

    override suspend fun getById(id: String): Acopiador? = queries.selectById(id).executeAsOneOrNull()?.mapConNombre()

    override suspend fun getByUsuarioId(usuarioId: String): Acopiador? =
        queries.selectByUsuarioId(usuarioId).executeAsOneOrNull()?.mapConNombre()

    override suspend fun crear(acopiador: Acopiador) {
        queries.insert(
            id = acopiador.id.ifBlank { IdGenerator.newId() },
            usuario_id = acopiador.usuarioId,
            ruta_nombre = acopiador.rutaNombre,
        )
    }

    override suspend fun actualizar(acopiador: Acopiador) {
        queries.update(ruta_nombre = acopiador.rutaNombre, id = acopiador.id)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}
