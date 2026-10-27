package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import kotlinx.coroutines.flow.Flow

interface ResultadoLaboratorioRepository {
    fun observeByProveedor(proveedorId: String): Flow<List<ResultadoLaboratorio>>
    suspend fun getAll(): List<ResultadoLaboratorio>
    suspend fun getByProveedor(proveedorId: String): List<ResultadoLaboratorio>
    suspend fun crear(resultado: ResultadoLaboratorio)
    /** Los 30 más recientes de todos los proveedores ("Resultados de laboratorio" de planta). */
    fun observeRecientes(): Flow<List<ResultadoLaboratorio>>
    suspend fun getById(id: String): ResultadoLaboratorio?
    suspend fun actualizar(resultado: ResultadoLaboratorio)

    /** "Enviar a producción": solo resultados conformes que aún no se enviaron. */
    suspend fun marcarEnviadoProduccion(id: String)
    suspend fun countNoConformes(proveedorId: String): Long
}
