package com.example.ecolacteoshuata.authorization

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.data.remote.*
import com.example.ecolacteoshuata.data.remote.datasource.SyncRemoteDataSource
import com.example.ecolacteoshuata.data.repository.*
import com.example.ecolacteoshuata.data.sync.RegistroCambios
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.usecase.AdministrarRolesUseCase
import com.example.ecolacteoshuata.presentation.navigation.PantallasNativas
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import kotlin.test.*

class DistribucionesRolesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture(nombre: String) = javaClass.getResourceAsStream("/$nombre")!!.bufferedReader().readText()
    private class Conexion(private val puerto: String) : ConexionServidorRepository {
        private var valor = ConexionServidor("127.0.0.1", puerto, true, null, "token-distribucion")
        override suspend fun get() = valor
        override fun observe(): Flow<ConexionServidor> = flowOf(valor)
        override suspend fun guardar(conexion: ConexionServidor) { valor = conexion }
        override suspend fun actualizarUltimaSincronizacion(fecha: String) {}
        override suspend fun reiniciarSincronizacion() {}
        override suspend fun guardarToken(token: String?) { valor = valor.copy(token = token) }
    }
    private class Token : TokenStorage {
        override suspend fun save(token: String) {}
        override suspend fun read() = "token-distribucion"
        override suspend fun clear() {}
    }
    @Test fun inicialPreserva41Pantallas39NativasSeisRolesYContratosDelBackend() {
        val c = json.decodeFromString<CatalogoDistribuciones>(fixture("huata-distribuciones-catalogo.json"))
        assertEquals(41, c.pantallas.size)
        assertEquals(39, c.pantallas.count { it.android != null })
        c.pantallas.filter { it.android != null }.forEach { assertNotNull(PantallasNativas.pantallas[it.id]) }
        val inicial = c.distribuciones.single { it.predeterminada }
        assertEquals("base", inicial.tipo)
        assertEquals(listOf(15, 7, 6, 4, 9, 9), inicial.asignaciones.map { it.pantallas.size })
        assertEquals(41, inicial.asignaciones.flatMap { it.pantallas }.toSet().size)
        assertEquals(6, c.roles.size)
        assertTrue(c.distribuciones.single { it.codigo == "en-desarrollo" }.origen.toString().contains("0fd6a98e3010187a041baa1ce60cf79333f582d4"))
        assertFalse(c.rol_prueba_presente)
        assertFalse(c.auxiliar_pendiente)
    }
    @Test fun transporteNativoUsaApiCentralPreviewConfirmacionYConservaPendientesAnteRevocacionYConflicto() = runTest {
        val c = json.decodeFromString<CatalogoDistribuciones>(fixture("huata-distribuciones-catalogo.json"))
        val v = json.decodeFromString<VistaDistribucion>(fixture("huata-distribuciones-preview.json"))
        val llamadas = mutableListOf<Triple<String, String, String>>()
        var conflicto = false
        val servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/api/mobile/autorizaciones/distribuciones") { x ->
            val cuerpo = x.requestBody.bufferedReader().readText()
            synchronized(llamadas) { llamadas += Triple(x.requestMethod, x.requestURI.path, cuerpo) }
            val estado = if (x.requestHeaders.getFirst("Authorization") != "Bearer token-distribucion") 401 else if (conflicto) 409 else 200
            val texto = if (estado == 409) "{\"message\":\"Las asignaciones cambiaron. Genera otra vista previa.\"}" else if (x.requestURI.path.endsWith("/previsualizar")) json.encodeToString(v) else json.encodeToString(c)
            val bytes = texto.toByteArray(); x.responseHeaders.add("Content-Type", "application/json")
            x.sendResponseHeaders(estado, bytes.size.toLong()); x.responseBody.use { it.write(bytes) }
        }
        servidor.start()
        val cliente = HttpClientFactory.create(OkHttp.create(), ApiConfig(), Token())
        try {
            SqliteTestDriver().use { d ->
                AppDatabase.Schema.create(d); RegistroCambios.instalar(d); val db = AppDatabase(d)
                val anterior = c.autorizacion.copy(version = "anterior", pantallas = c.autorizacion.pantallas + "produccion.unidades")
                AutorizacionLocal.guardar(db, anterior); db.autorizacionLocalQueries.sesion(anterior.usuario_id)
                db.unidadMedidaQueries.insert("pendiente-distribucion", "Conservar después de revocar", "p")
                val pendientes = db.cambioPendienteQueries.selectTodos().executeAsList()
                assertEquals(1, pendientes.size)
                val central = AdministrarRolesUseCase(Conexion(servidor.address.port.toString()), SyncRemoteDataSource(cliente, ApiConfig()), db, SessionRepositoryImpl(db))
                assertIs<AppResult.Success<CatalogoDistribuciones>>(central.distribuciones())
                assertFalse(AutorizacionLocal.leer(db, anterior.usuario_id)!!.permite("produccion.unidades"))
                assertEquals(pendientes, db.cambioPendienteQueries.selectTodos().executeAsList())
                val solicitudes = listOf(
                    GuardarDistribucion(modo = "copiar", nombre = "Duplicada", origen = 1),
                    GuardarDistribucion(modo = "copiar", nombre = "Actual"),
                    GuardarDistribucion(modo = "guardar", nombre = "Nueva", asignaciones = listOf(AsignacionDistribucion("vendedor", pantallas = listOf("vendedor.clientes")))),
                    GuardarDistribucion(modo = "guardar", id = 3, version = 1, nombre = "Editada", asignaciones = listOf(AsignacionDistribucion("vendedor", pantallas = listOf("vendedor.clientes")))),
                )
                solicitudes.forEach { assertIs<AppResult.Success<CatalogoDistribuciones>>(central.guardarDistribucion(it)) }
                assertIs<AppResult.Success<VistaDistribucion>>(central.prepararDistribucion(PrepararDistribucion("aplicar", 3)))
                assertEquals(pendientes, db.cambioPendienteQueries.selectTodos().executeAsList())
                val aplicar = ConfirmarDistribucion(v.token!!, true)
                assertIs<AppResult.Success<CatalogoDistribuciones>>(central.confirmarDistribucion(aplicar))
                assertIs<AppResult.Success<VistaDistribucion>>(central.prepararDistribucion(PrepararDistribucion("recuperar", aplicacion_id = "00000000-0000-0000-0000-000000000001")))
                conflicto = true
                val rechazado = central.confirmarDistribucion(aplicar)
                assertIs<AppResult.Error>(rechazado); assertTrue(rechazado.message.contains("Genera otra vista previa"))
                assertEquals(pendientes, db.cambioPendienteQueries.selectTodos().executeAsList())
                assertNotNull(db.unidadMedidaQueries.selectById("pendiente-distribucion").executeAsOneOrNull())
                assertEquals(c.autorizacion, AutorizacionLocal.leer(db, anterior.usuario_id))
                assertFalse(llamadas.any { it.second.endsWith("/cambios") })
                assertTrue(llamadas.filter { it.first == "POST" && it.second.endsWith("/distribuciones") }.all { json.parseToJsonElement(it.third).jsonObject.containsKey("modo") })
                assertEquals(solicitudes, llamadas.filter { it.first == "POST" && it.second.endsWith("/distribuciones") }.map { json.decodeFromString<GuardarDistribucion>(it.third) })
                assertEquals(aplicar, json.decodeFromString<ConfirmarDistribucion>(llamadas.first { it.second.endsWith("/confirmar") }.third))
            }
        } finally { cliente.close(); servidor.stop(0) }
    }
}
