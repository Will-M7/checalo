package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Descuento
import kotlinx.coroutines.flow.Flow

interface DescuentoRepository {
    fun observeAll(): Flow<List<Descuento>>
    fun observeDisponibles(): Flow<List<Descuento>>
    suspend fun guardar(descuento: Descuento)
    suspend fun setActivo(id: String, activo: Boolean)
    suspend fun eliminar(id: String)
}
