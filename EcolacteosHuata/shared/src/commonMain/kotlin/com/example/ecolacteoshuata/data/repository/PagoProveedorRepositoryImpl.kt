package com.example.ecolacteoshuata.data.repository

import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.PagoProveedor
import com.example.ecolacteoshuata.domain.repository.PagoProveedorRepository
import kotlinx.datetime.LocalDate

class PagoProveedorRepositoryImpl(private val db: AppDatabase) : PagoProveedorRepository {
    private val queries = db.pagoProveedorQueries

    override suspend fun getAll(): List<PagoProveedor> = queries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun getByProveedor(proveedorId: String): List<PagoProveedor> =
        queries.selectByProveedor(proveedorId).executeAsList().map { it.toDomain() }

    override suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<PagoProveedor> =
        queries.selectEntreFechas(desde.toString(), hasta.toString()).executeAsList().map { it.toDomain() }

    override suspend fun crear(pago: PagoProveedor) {
        queries.insert(
            id = pago.id.ifBlank { IdGenerator.newId() },
            proveedor_id = pago.proveedorId,
            recojo_id = pago.recojoId,
            fecha = pago.fecha.toString(),
            monto = pago.monto,
            metodo_pago = pago.metodoPago.name,
            periodo = pago.periodo,
        )
    }
}
