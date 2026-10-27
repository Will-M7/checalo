package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.EntregaPlanta
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Recojo
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface RecojoRepository {
    fun observePendientesSincronizar(): Flow<List<Recojo>>
    fun observeByProveedor(proveedorId: String): Flow<List<Recojo>>
    suspend fun getAll(): List<Recojo>
    suspend fun getById(id: String): Recojo?
    suspend fun getByProveedor(proveedorId: String): List<Recojo>
    suspend fun getByAcopiadorYFecha(acopiadorId: String, fecha: LocalDate): List<Recojo>
    suspend fun getByAcopiadorTurnoFecha(acopiadorId: String, turnoId: String, fecha: LocalDate): List<Recojo>
    suspend fun getByAcopiadorEntreFechas(acopiadorId: String, desde: LocalDate, hasta: LocalDate): List<Recojo>
    suspend fun getByProveedorEntreFechas(proveedorId: String, desde: LocalDate, hasta: LocalDate): List<Recojo>
    suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<Recojo>
    suspend fun crear(recojo: Recojo)
    suspend fun marcarPagado(id: String, metodoPago: MetodoPago)
    suspend fun actualizarLitros(id: String, litros: Double, total: Double)
    suspend fun marcarEntregados(ids: List<String>)
    suspend fun sumLitrosEnFecha(fecha: LocalDate): Double
    suspend fun countPendientesSincronizar(): Long
    suspend fun marcarSincronizado(id: String)
}

interface EntregaPlantaRepository {
    suspend fun getAll(): List<EntregaPlanta>
    suspend fun getByAcopiador(acopiadorId: String): List<EntregaPlanta>
    suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<EntregaPlanta>
    suspend fun crear(entrega: EntregaPlanta)
}
