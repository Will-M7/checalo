package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Producto
import kotlinx.coroutines.flow.Flow

/** Producciones publicadas vistas como productos a la venta (solo lectura). */
interface ProductoRepository {
    /** Publicadas con stock disponible. */
    fun observeActivos(): Flow<List<Producto>>
    fun observeAll(): Flow<List<Producto>>
    suspend fun getAll(): List<Producto>
    suspend fun getActivos(): List<Producto>
    suspend fun getById(id: String): Producto?
    suspend fun countActivos(): Long
}
