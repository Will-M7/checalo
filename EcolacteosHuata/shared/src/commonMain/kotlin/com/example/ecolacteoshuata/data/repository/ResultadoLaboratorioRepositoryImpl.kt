package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ResultadoLaboratorioRepositoryImpl(private val db: AppDatabase) : ResultadoLaboratorioRepository {
    private val queries = db.resultadoLaboratorioQueries

    override fun observeByProveedor(proveedorId: String): Flow<List<ResultadoLaboratorio>> =
        queries.selectByProveedor(proveedorId).asFlow().mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<ResultadoLaboratorio> = queries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getByProveedor(proveedorId: String): List<ResultadoLaboratorio> =
        queries.selectByProveedor(proveedorId).executeAsList().map { it.toDomain() }

    override suspend fun crear(resultado: ResultadoLaboratorio) {
        queries.insert(
            id = resultado.id.ifBlank { IdGenerator.newId() },
            proveedor_id = resultado.proveedorId,
            fecha = resultado.fecha.toString(),
            turno_id = resultado.turnoId,
            resultado = resultado.resultado.name,
            densidad = resultado.densidad,
            acidez = resultado.acidez,
            temperatura = resultado.temperatura,
            observaciones = resultado.observaciones,
            conforme = if (resultado.conforme) 1L else 0L,
            litros = resultado.litros,
            grasa = resultado.grasa,
            sng = resultado.sng,
            proteina = resultado.proteina,
            lactosa = resultado.lactosa,
            sales = resultado.sales,
            total_solidos = resultado.totalSolidos,
            agua_anadida = resultado.aguaAnadida,
            punto_congelacion = resultado.puntoCongelacion,
            ph = resultado.ph,
            sn = resultado.sn,
            modo = resultado.modo,
            hora_analisis = resultado.horaAnalisis,
            fecha_analisis = resultado.fechaAnalisis,
        )
    }

    override fun observeRecientes(): Flow<List<ResultadoLaboratorio>> =
        queries.selectRecientes().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): ResultadoLaboratorio? = queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun actualizar(resultado: ResultadoLaboratorio) {
        queries.update(
            proveedor_id = resultado.proveedorId,
            fecha = resultado.fecha.toString(),
            turno_id = resultado.turnoId,
            resultado = resultado.resultado.name,
            densidad = resultado.densidad,
            temperatura = resultado.temperatura,
            observaciones = resultado.observaciones,
            conforme = if (resultado.conforme) 1L else 0L,
            litros = resultado.litros,
            grasa = resultado.grasa,
            sng = resultado.sng,
            proteina = resultado.proteina,
            lactosa = resultado.lactosa,
            sales = resultado.sales,
            total_solidos = resultado.totalSolidos,
            agua_anadida = resultado.aguaAnadida,
            punto_congelacion = resultado.puntoCongelacion,
            ph = resultado.ph,
            sn = resultado.sn,
            modo = resultado.modo,
            hora_analisis = resultado.horaAnalisis,
            fecha_analisis = resultado.fechaAnalisis,
            id = resultado.id,
        )
    }

    override suspend fun marcarEnviadoProduccion(id: String) {
        queries.marcarEnviadoProduccion(id)
    }

    override suspend fun countNoConformes(proveedorId: String): Long = queries.countNoConformes(proveedorId).executeAsOne()
}
