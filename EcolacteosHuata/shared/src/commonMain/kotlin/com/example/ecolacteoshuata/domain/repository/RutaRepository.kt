package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Ruta
import kotlinx.coroutines.flow.Flow

interface RutaRepository {
    fun observeAll(): Flow<List<Ruta>>
    fun observeByAcopiador(acopiadorId: String): Flow<List<Ruta>>
    suspend fun getAll(): List<Ruta>
    suspend fun getByAcopiador(acopiadorId: String): List<Ruta>
    suspend fun getById(id: String): Ruta?
    suspend fun crear(ruta: Ruta)
    suspend fun actualizar(ruta: Ruta)
    suspend fun eliminar(id: String)
    suspend fun asignarProveedores(rutaId: String, proveedorIds: List<String>)
    suspend fun reordenarProveedores(rutaId: String, proveedorIdsEnOrden: List<String>)
    suspend fun actualizarTrazo(rutaId: String, trazo: List<PuntoGeo>)
}
