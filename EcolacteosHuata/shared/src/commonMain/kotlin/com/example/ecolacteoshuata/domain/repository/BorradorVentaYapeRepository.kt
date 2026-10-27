package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.BorradorVentaYape

interface BorradorVentaYapeRepository {
    suspend fun guardar(vendedorId:String,borrador:BorradorVentaYape)
    suspend fun cargar(vendedorId:String):BorradorVentaYape?
    suspend fun eliminar(vendedorId:String)
}
