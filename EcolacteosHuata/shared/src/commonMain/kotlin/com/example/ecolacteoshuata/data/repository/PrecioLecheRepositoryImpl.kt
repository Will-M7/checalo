package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.PrecioLeche
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class PrecioLecheRepositoryImpl(private val db: AppDatabase) : PrecioLecheRepository {
    private val queries = db.precioLecheQueries

    override fun observeVigente(): Flow<PrecioLeche?> =
        queries.selectVigente().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toDomain() }

    override suspend fun getVigente(): PrecioLeche? = queries.selectVigente().executeAsOneOrNull()?.toDomain()

    override suspend fun getAll(): List<PrecioLeche> = queries.selectAll().executeAsList().map { it.toDomain() }

    override fun observeHistorial(): Flow<List<PrecioLeche>> =
        queries.selectHistorial().asFlow().mapToList(Dispatchers.Default).map { lista -> lista.map { it.toDomain() } }

    override suspend fun setNuevoPrecio(precioLitro: Double, vigenteDesde: LocalDate) {
        queries.insert(IdGenerator.newId(), precioLitro, vigenteDesde.toString())
    }
}
