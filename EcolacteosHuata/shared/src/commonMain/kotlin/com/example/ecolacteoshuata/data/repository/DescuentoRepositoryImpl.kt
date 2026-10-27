package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Descuento
import com.example.ecolacteoshuata.domain.repository.DescuentoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.ecolacteoshuata.db.Descuento as DescuentoEntity

private fun DescuentoEntity.toDomain() = Descuento(id = id, nombre = nombre, porcentaje = porcentaje, activo = activo == 1L)

class DescuentoRepositoryImpl(private val db: AppDatabase) : DescuentoRepository {
    private val queries = db.descuentoQueries

    override fun observeAll(): Flow<List<Descuento>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    override fun observeDisponibles(): Flow<List<Descuento>> =
        queries.selectDisponibles().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    override suspend fun guardar(descuento: Descuento) {
        val activo = if (descuento.activo) 1L else 0L
        if (descuento.id.isBlank()) {
            queries.insert(IdGenerator.newId(), descuento.nombre, descuento.porcentaje, activo)
        } else {
            queries.update(descuento.nombre, descuento.porcentaje, activo, descuento.id)
        }
    }

    override suspend fun setActivo(id: String, activo: Boolean) {
        queries.setActivo(if (activo) 1L else 0L, id)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}
