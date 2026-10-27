package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.PagoProveedor
import kotlinx.datetime.LocalDate

interface PagoProveedorRepository {
    suspend fun getAll(): List<PagoProveedor>
    suspend fun getByProveedor(proveedorId: String): List<PagoProveedor>
    suspend fun getEntreFechas(desde: LocalDate, hasta: LocalDate): List<PagoProveedor>
    suspend fun crear(pago: PagoProveedor)
}
