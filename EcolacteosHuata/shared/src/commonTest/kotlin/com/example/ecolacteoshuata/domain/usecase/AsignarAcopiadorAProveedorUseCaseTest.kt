package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AsignarAcopiadorAProveedorUseCaseTest {

    private class FakeRutas(var rutas: List<Ruta>) : RutaRepository {
        override fun observeAll(): Flow<List<Ruta>> = flowOf(rutas)
        override fun observeByAcopiador(acopiadorId: String): Flow<List<Ruta>> = flowOf(rutas.filter { it.acopiadorId == acopiadorId })
        override suspend fun getAll() = rutas
        override suspend fun getByAcopiador(acopiadorId: String) = rutas.filter { it.acopiadorId == acopiadorId }
        override suspend fun getById(id: String) = rutas.firstOrNull { it.id == id }
        override suspend fun crear(ruta: Ruta) { rutas = rutas + ruta }
        override suspend fun actualizar(ruta: Ruta) = Unit
        override suspend fun eliminar(id: String) = Unit
        override suspend fun asignarProveedores(rutaId: String, proveedorIds: List<String>) {
            rutas = rutas.map { if (it.id == rutaId) it.copy(proveedorIds = proveedorIds) else it }
        }
        override suspend fun reordenarProveedores(rutaId: String, proveedorIdsEnOrden: List<String>) = asignarProveedores(rutaId, proveedorIdsEnOrden)
        override suspend fun actualizarTrazo(rutaId: String, trazo: List<PuntoGeo>) = Unit
        fun paradas(id: String) = rutas.first { it.id == id }.proveedorIds
    }

    private class FakeProveedores : ProveedorRepository {
        val asignaciones = mutableMapOf<String, String?>()
        override fun observeAll(): Flow<List<Proveedor>> = flowOf(emptyList())
        override suspend fun getAll(): List<Proveedor> = emptyList()
        override suspend fun getById(id: String): Proveedor? = null
        override suspend fun getByUsuarioId(usuarioId: String): Proveedor? = null
        override suspend fun getByAcopiador(acopiadorId: String): List<Proveedor> = emptyList()
        override suspend fun crear(proveedor: Proveedor) = Unit
        override suspend fun actualizarFinca(id: String, ubicacion: String, referencia: String, lat: Double?, lon: Double?) = Unit
        override suspend fun asignarAcopiador(id: String, acopiadorId: String?) { asignaciones[id] = acopiadorId }
        override suspend fun incrementarAlteraciones(id: String) = 0
        override suspend fun resetAlteraciones(id: String) = Unit
        override suspend fun bloquear(id: String, bloqueado: Boolean) = Unit
        override suspend fun eliminar(id: String) = Unit
    }

    private fun rutas() = FakeRutas(
        listOf(
            Ruta("r1", "a1", "Ruta Huata", activa = true, proveedorIds = listOf("p1", "p2")),
            Ruta("r2", "a2", "Ruta Coata", activa = true, proveedorIds = listOf("p3")),
        ),
    )

    @Test
    fun asignarAOtroAcopiador_mueveLaParadaAlFinalDeSuRuta() = runTest {
        val rutas = rutas()
        AsignarAcopiadorAProveedorUseCase(FakeProveedores(), rutas)("p1", "a2")
        assertEquals(listOf("p2"), rutas.paradas("r1"))
        assertEquals(listOf("p3", "p1"), rutas.paradas("r2"))
    }

    @Test
    fun quitarAcopiador_quitaLaParadaDeLaRuta() = runTest {
        val rutas = rutas()
        AsignarAcopiadorAProveedorUseCase(FakeProveedores(), rutas)("p2", null)
        assertEquals(listOf("p1"), rutas.paradas("r1"))
    }

    @Test
    fun reasignarAlMismoAcopiador_conservaElOrden() = runTest {
        val rutas = rutas()
        AsignarAcopiadorAProveedorUseCase(FakeProveedores(), rutas)("p1", "a1")
        assertEquals(listOf("p1", "p2"), rutas.paradas("r1"))
    }
}
