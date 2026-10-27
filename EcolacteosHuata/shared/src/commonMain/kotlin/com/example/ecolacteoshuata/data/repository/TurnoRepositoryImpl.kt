package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TurnoRepositoryImpl(private val db: AppDatabase) : TurnoRepository {
    private val queries = db.turnoQueries

    override fun observeAll(): Flow<List<Turno>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Turno> = queries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getRuta(): List<Turno> = queries.selectRuta().executeAsList().map { it.toDomain() }

    override fun observeByAcopiador(acopiadorId: String): Flow<List<Turno>> =
        queries.selectByAcopiador(acopiadorId).asFlow().mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getByAcopiador(acopiadorId: String): List<Turno> =
        queries.selectByAcopiador(acopiadorId).executeAsList().map { it.toDomain() }

    override suspend fun getById(id: String): Turno? = queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun crear(turno: Turno) {
        queries.insert(
            id = turno.id.ifBlank { IdGenerator.newId() },
            acopiador_id = turno.acopiadorId,
            nombre = turno.nombre,
            hora_inicio = turno.horaInicio,
            hora_fin = turno.horaFin,
        )
    }

    override suspend fun actualizar(turno: Turno) {
        queries.update(nombre = turno.nombre, hora_inicio = turno.horaInicio, hora_fin = turno.horaFin, id = turno.id)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}
