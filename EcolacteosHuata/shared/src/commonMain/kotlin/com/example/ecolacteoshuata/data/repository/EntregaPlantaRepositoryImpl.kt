package com.example.ecolacteoshuata.data.repository

import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.EntregaPlanta
import com.example.ecolacteoshuata.domain.repository.EntregaPlantaRepository
import kotlinx.datetime.LocalDate

class EntregaPlantaRepositoryImpl(private val db: AppDatabase) : EntregaPlantaRepository {
    private val queries = db.entregaPlantaQueries

    override suspend fun getAll(): List<EntregaPlanta> = queries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getByAcopiador(acopiadorId: String): List<EntregaPlanta> =
        queries.selectByAcopiador(acopiadorId).executeAsList().map { it.toDomain() }

    override suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<EntregaPlanta> =
        queries.selectEntreFechas(desde.toString(), hasta.toString()).executeAsList().map { it.toDomain() }

    override suspend fun crear(entrega: EntregaPlanta) {
        queries.insert(
            id = entrega.id.ifBlank { IdGenerator.newId() },
            acopiador_id = entrega.acopiadorId,
            turno_id = entrega.turnoId,
            fecha = entrega.fecha.toString(),
            litros_total = entrega.litrosTotal,
            total_pagado = entrega.totalPagado,
            total_deuda = entrega.totalDeuda,
            proveedores_no_recogidos = entrega.proveedoresNoRecogidos.toLong(),
            guia = entrega.guia,
        )
    }
}
