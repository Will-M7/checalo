package com.example.ecolacteoshuata.data.repository

import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.BorradorVentaYape
import com.example.ecolacteoshuata.domain.repository.BorradorVentaYapeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BorradorVentaYapeRepositoryImpl(private val db:AppDatabase):BorradorVentaYapeRepository {
    override suspend fun guardar(vendedorId:String,borrador:BorradorVentaYape) {
        require(vendedorId.isNotBlank())
        withContext(Dispatchers.Default) {
            db.borradorVentaYapeQueries.guardar(vendedorId,Json.encodeToString(borrador))
        }
    }
    override suspend fun cargar(vendedorId:String):BorradorVentaYape? = withContext(Dispatchers.Default) {
        db.borradorVentaYapeQueries.cargar(vendedorId).executeAsOneOrNull()?.let { Json.decodeFromString<BorradorVentaYape>(it) }
    }
    override suspend fun eliminar(vendedorId:String) {
        withContext(Dispatchers.Default) { db.borradorVentaYapeQueries.eliminar(vendedorId) }
    }
}
