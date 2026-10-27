package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Comunicado
import kotlinx.coroutines.flow.Flow

interface ComunicadoRepository {
    fun observeAll(): Flow<List<Comunicado>>
    suspend fun getAll(): List<Comunicado>
    suspend fun crear(comunicado: Comunicado)
    suspend fun eliminar(id: String)
}

interface EmpresaRepository {
    fun observe(): Flow<com.example.ecolacteoshuata.domain.model.Empresa?>
    suspend fun get(): com.example.ecolacteoshuata.domain.model.Empresa?
    suspend fun actualizar(empresa: com.example.ecolacteoshuata.domain.model.Empresa)
}
