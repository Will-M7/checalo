package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Compra
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.model.ProduccionRegistro
import com.example.ecolacteoshuata.domain.model.Receta
import com.example.ecolacteoshuata.domain.model.TurnoProduccion
import com.example.ecolacteoshuata.domain.model.UnidadMedida
import kotlinx.coroutines.flow.Flow

interface UnidadMedidaRepository {
    fun observeAll(): Flow<List<UnidadMedida>>
    suspend fun getAll(): List<UnidadMedida>
    suspend fun crear(unidad: UnidadMedida)
    suspend fun actualizar(unidad: UnidadMedida)
    suspend fun eliminar(id: String)
}

interface InsumoRepository {
    fun observeAll(): Flow<List<Insumo>>
    suspend fun getAll(): List<Insumo>
    suspend fun getById(id: String): Insumo?
    suspend fun crear(insumo: Insumo)
    suspend fun actualizar(insumo: Insumo)
    suspend fun eliminar(id: String)
}

interface RecetaRepository {
    fun observeAll(): Flow<List<Receta>>
    suspend fun getAll(): List<Receta>
    suspend fun getById(id: String): Receta?
    suspend fun crear(receta: Receta)
    suspend fun actualizar(receta: Receta)
    suspend fun eliminar(id: String)
}

interface CompraRepository {
    fun observeAll(): Flow<List<Compra>>
    suspend fun getAll(): List<Compra>
    suspend fun crear(compra: Compra)
}

interface ProduccionRepository {
    fun observeAll(): Flow<List<ProduccionRegistro>>
    suspend fun getAll(): List<ProduccionRegistro>
    suspend fun getById(id: String): ProduccionRegistro?
    suspend fun crear(registro: ProduccionRegistro)
    suspend fun actualizar(registro: ProduccionRegistro)
    suspend fun publicar(id: String)
    suspend fun eliminar(id: String)
}

interface TurnoProduccionRepository {
    fun observeAll(): Flow<List<TurnoProduccion>>
    suspend fun crear(turno: TurnoProduccion)
    suspend fun eliminar(id: String)
}
