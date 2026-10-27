package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class YapeOfflineGuardTest {
    @Test fun unverifiedYapeNeverWritesReceiptOrInventory() = runTest {
        val ventas = object : VentaRepository {
            override fun observeRecientes(): Flow<List<Venta>> = error("Unexpected access")
            override suspend fun getAll(): List<Venta> = error("Unexpected access")
            override suspend fun getById(id: String): Venta? = error("Unexpected access")
            override suspend fun getEntreFechas(desde: LocalDate,hasta: LocalDate): List<Venta> = error("Unexpected access")
            override suspend fun crear(venta: Venta): Venta = error("Must not issue unverified receipt")
            override suspend fun countHoy(fecha: LocalDate): Long = error("Unexpected access")
            override suspend fun sumTotalHoy(fecha: LocalDate): Double = error("Unexpected access")
            override suspend fun countAll(): Long = error("Unexpected access")
            override suspend fun siguienteNumeroBoleta(): String = error("Must not allocate receipt")
            override suspend fun eliminar(ids: List<String>) = error("Unexpected access")
        }
        val clientes = object : ClienteRepository {
            override fun observeAll(): Flow<List<Cliente>> = error("Unexpected access")
            override suspend fun getAll(): List<Cliente> = error("Unexpected access")
            override suspend fun getById(id: String): Cliente? = error("Unexpected access")
            override suspend fun getMasFrecuente(): Cliente? = error("Unexpected access")
            override suspend fun crear(cliente: Cliente) = error("Unexpected access")
            override suspend fun actualizar(cliente: Cliente) = error("Unexpected access")
            override suspend fun setDescuentos(id: String,favorito: Boolean,descuentoFavorito: Double,mayorista: Boolean,descuentoMayorista: Double) = error("Unexpected access")
            override suspend fun incrementarCompras(id: String) = error("Unexpected access")
            override suspend fun eliminar(id: String) = error("Unexpected access")
            override suspend fun countAll(): Long = error("Unexpected access")
        }
        val productos = object : ProductoRepository {
            override fun observeActivos(): Flow<List<Producto>> = error("Unexpected access")
            override fun observeAll(): Flow<List<Producto>> = error("Unexpected access")
            override suspend fun getAll(): List<Producto> = error("Unexpected access")
            override suspend fun getActivos(): List<Producto> = error("Unexpected access")
            override suspend fun getById(id: String): Producto? = error("Unexpected access")
            override suspend fun countActivos(): Long = error("Unexpected access")
        }
        val items=listOf(CarritoItem(Producto("p1","Queso","",7.0,true,10.0),2))
        val persisted=mutableMapOf<String,String>()
        val drafts=object:BorradorVentaYapeRepository {
            override suspend fun guardar(vendedorId:String,borrador:BorradorVentaYape) { persisted[vendedorId]=kotlinx.serialization.json.Json.encodeToString(BorradorVentaYape.serializer(),borrador) }
            override suspend fun cargar(vendedorId:String):BorradorVentaYape?=persisted[vendedorId]?.let { kotlinx.serialization.json.Json.decodeFromString(BorradorVentaYape.serializer(),it) }
            override suspend fun eliminar(vendedorId:String) { persisted.remove(vendedorId) }
        }
        val result=RegistrarVentaUseCase(ventas,clientes,productos,drafts)(RegistrarVentaInput(items,TotalesCarrito(14.0,0.0,"",14.0),null,null,null,MetodoPago.YAPE,null,vendedorId="seller1"))
        val recovered=drafts.cargar("seller1")
        assertEquals(items,recovered?.items)
        assertNull(drafts.cargar("other_seller"))
        assertFalse(com.example.ecolacteoshuata.data.sync.RegistroCambios.TABLAS.contains("borrador_venta_yape"))
        assertIs<AppResult.Error>(result)
        assertEquals(2,items.single().cantidad)
    }
}
