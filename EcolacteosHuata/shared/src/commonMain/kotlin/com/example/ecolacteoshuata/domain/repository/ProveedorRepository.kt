package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Proveedor
import kotlinx.coroutines.flow.Flow

interface ProveedorRepository {
    fun observeAll(): Flow<List<Proveedor>>
    suspend fun getAll(): List<Proveedor>
    suspend fun getById(id: String): Proveedor?
    suspend fun getByUsuarioId(usuarioId: String): Proveedor?
    suspend fun getByAcopiador(acopiadorId: String): List<Proveedor>
    suspend fun crear(proveedor: Proveedor)
    suspend fun actualizarFinca(id: String, ubicacion: String, referencia: String, lat: Double?, lon: Double?)
    suspend fun asignarAcopiador(id: String, acopiadorId: String?)
    suspend fun incrementarAlteraciones(id: String): Int
    suspend fun resetAlteraciones(id: String)
    suspend fun bloquear(id: String, bloqueado: Boolean)
    suspend fun eliminar(id: String)
}
