package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Cliente
import com.example.ecolacteoshuata.domain.repository.ClienteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ClienteRepositoryImpl(private val db: AppDatabase) : ClienteRepository {
    private val queries = db.clienteQueries

    override fun observeAll(): Flow<List<Cliente>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Cliente> = queries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getById(id: String): Cliente? = queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun getMasFrecuente(): Cliente? = queries.selectMasFrecuente().executeAsOneOrNull()?.toDomain()

    override suspend fun crear(cliente: Cliente) {
        queries.insert(
            id = cliente.id.ifBlank { IdGenerator.newId() },
            nombres = cliente.nombres,
            apellidos = cliente.apellidos,
            documento = cliente.documento,
            compras_count = cliente.comprasCount.toLong(),
            favorito = if (cliente.favorito) 1L else 0L,
            descuento_favorito = cliente.descuentoFavorito,
            mayorista = if (cliente.mayorista) 1L else 0L,
            descuento_mayorista = cliente.descuentoMayorista,
        )
    }

    override suspend fun actualizar(cliente: Cliente) {
        queries.update(nombres = cliente.nombres, apellidos = cliente.apellidos, documento = cliente.documento, id = cliente.id)
    }

    override suspend fun setDescuentos(id: String, favorito: Boolean, descuentoFavorito: Double, mayorista: Boolean, descuentoMayorista: Double) {
        queries.setDescuentos(
            favorito = if (favorito) 1L else 0L,
            descuento_favorito = descuentoFavorito,
            mayorista = if (mayorista) 1L else 0L,
            descuento_mayorista = descuentoMayorista,
            id = id,
        )
    }

    override suspend fun incrementarCompras(id: String) {
        queries.incrementarCompras(id)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }

    override suspend fun countAll(): Long = queries.countAll().executeAsOne()
}
