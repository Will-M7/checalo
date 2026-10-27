package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Cliente
import kotlinx.coroutines.flow.Flow

interface ClienteRepository {
    fun observeAll(): Flow<List<Cliente>>
    suspend fun getAll(): List<Cliente>
    suspend fun getById(id: String): Cliente?
    suspend fun getMasFrecuente(): Cliente?
    suspend fun crear(cliente: Cliente)
    suspend fun actualizar(cliente: Cliente)
    suspend fun setDescuentos(id: String, favorito: Boolean, descuentoFavorito: Double, mayorista: Boolean, descuentoMayorista: Double)
    suspend fun incrementarCompras(id: String)
    suspend fun eliminar(id: String)
    suspend fun countAll(): Long
}
