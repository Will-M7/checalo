package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.PrecioLeche
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PrecioLecheRepository {
    fun observeVigente(): Flow<PrecioLeche?>
    suspend fun getVigente(): PrecioLeche?
    suspend fun getAll(): List<PrecioLeche>
    /** Los 10 precios más recientes (incluye los que todavía no entran en vigencia). */
    fun observeHistorial(): Flow<List<PrecioLeche>>

    suspend fun setNuevoPrecio(precioLitro: Double, vigenteDesde: LocalDate)
}
