package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Venta
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface VentaRepository {
    fun observeRecientes(): Flow<List<Venta>>
    suspend fun getAll(): List<Venta>
    suspend fun getById(id: String): Venta?
    suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<Venta>
    suspend fun crear(venta: Venta): Venta
    suspend fun countHoy(fecha: LocalDate): Long
    suspend fun sumTotalHoy(fecha: LocalDate): Double
    suspend fun countAll(): Long
    suspend fun siguienteNumeroBoleta(): String

    /** "Limpiar historial": elimina estas boletas con sus ítems (no repone el stock). */
    suspend fun eliminar(ids: List<String>)
}
