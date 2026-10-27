package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.Turno
import kotlinx.coroutines.flow.Flow

interface AcopiadorRepository {
    fun observeAll(): Flow<List<Acopiador>>
    suspend fun getAll(): List<Acopiador>
    suspend fun getById(id: String): Acopiador?
    suspend fun getByUsuarioId(usuarioId: String): Acopiador?
    suspend fun crear(acopiador: Acopiador)
    suspend fun actualizar(acopiador: Acopiador)
    suspend fun eliminar(id: String)
}

interface TurnoRepository {
    fun observeAll(): Flow<List<Turno>>
    suspend fun getAll(): List<Turno>
    /** Turnos de ruta (los que usa el acopiador en recojo y entrega a planta). */
    suspend fun getRuta(): List<Turno>
    fun observeByAcopiador(acopiadorId: String): Flow<List<Turno>>
    suspend fun getByAcopiador(acopiadorId: String): List<Turno>
    suspend fun getById(id: String): Turno?
    suspend fun crear(turno: Turno)
    suspend fun actualizar(turno: Turno)
    suspend fun eliminar(id: String)
}
