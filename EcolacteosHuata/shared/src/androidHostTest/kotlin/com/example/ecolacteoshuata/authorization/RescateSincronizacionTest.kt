package com.example.ecolacteoshuata.authorization

import com.example.ecolacteoshuata.data.remote.*
import com.example.ecolacteoshuata.data.remote.datasource.*
import com.example.ecolacteoshuata.data.remote.dto.*
import com.example.ecolacteoshuata.data.repository.*
import com.example.ecolacteoshuata.data.sync.*
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.platform.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.test.*
import kotlinx.coroutines.*
import com.example.ecolacteoshuata.domain.usecase.*
import com.example.ecolacteoshuata.presentation.proveedor.*
import com.example.ecolacteoshuata.presentation.proveedor.dashboard.*
import com.example.ecolacteoshuata.presentation.proveedor.entregas.*
import com.example.ecolacteoshuata.presentation.proveedor.laboratorio.*
import com.example.ecolacteoshuata.presentation.proveedor.comunicados.*
import kotlinx.serialization.encodeToString
import androidx.lifecycle.viewModelScope
import java.net.InetSocketAddress
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RescateSincronizacionTest {
    @Test fun pruebaSinTokenSeguidaDeLoginUsaTokenNuevoSinRecrearCliente() = runTest {
        var token: String? = null
        val storage = object : TokenStorage {
            override suspend fun save(value: String) { token = value }
            override suspend fun read() = token
            override suspend fun clear() { token = null }
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { x ->
            val valid = x.requestHeaders.getFirst("Authorization") == "Bearer token-nuevo"
            val bytes = "{}".toByteArray()
            x.responseHeaders.add("Content-Type", "application/json")
            x.sendResponseHeaders(if (valid) 200 else 401, bytes.size.toLong())
            x.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = HttpClientFactory.create(OkHttp.create(), ApiConfig(), storage)
        try {
            val url = "http://127.0.0.1:${server.address.port}/api/mobile/sync"
            assertEquals(401, client.get(url).status.value)
            storage.save("token-nuevo")
            assertEquals(200, client.get(url).status.value, "La prueba anterior no debe conservar credenciales vacías")
            storage.save("otra-sesion")
            assertEquals(401, client.get(url).status.value, "Un cambio de cuenta no debe seguir enviando la sesión anterior")
            storage.clear()
            assertEquals(401, client.get(url).status.value, "No debe seguir enviando el token eliminado")
        } finally { client.close(); server.stop(0) }
    }

    @Test fun descargaRealDeLaravelPersistePerfilYRegistrosEnInstalacionVacia() = runTest {
        val payload = javaClass.getResourceAsStream("/huata-proveedor-sync.json")!!.bufferedReader().readText()
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<SyncResponseDto>(payload)
        SqliteTestDriver().use { driver ->
            AppDatabase.Schema.create(driver); RegistroCambios.instalar(driver)
            val db = AppDatabase(driver)
            val storage = object : TokenStorage {
                var value: String? = null
                override suspend fun save(token: String) { value = token }
                override suspend fun read() = value
                override suspend fun clear() { value = null }
            }
            val conexion = ConexionServidorRepositoryImpl(db, storage)
            conexion.guardar(ConexionServidor("127.0.0.1", "8000", true))
            conexion.guardarToken("token-fixture")
            var errorHttp: Int? = null
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/api/mobile/") { x ->
                val status = if (x.requestHeaders.getFirst("Authorization") != "Bearer token-fixture") 401 else errorHttp ?: 200
                val body = if (status != 200) "{}" else if (x.requestURI.path.endsWith("autorizaciones")) Json.encodeToString(dto.autorizacion!!) else payload
                val bytes = body.toByteArray()
                x.responseHeaders.add("Content-Type", "application/json")
                x.sendResponseHeaders(status, bytes.size.toLong()); x.responseBody.use { it.write(bytes) }
            }
            server.start()
            conexion.guardar(ConexionServidor("127.0.0.1", server.address.port.toString(), true))
            conexion.guardarToken("token-fixture")
            val client = HttpClientFactory.create(OkHttp.create(), ApiConfig(), storage)
            val api = SyncRemoteDataSource(client, ApiConfig())
            val models = mutableListOf<androidx.lifecycle.ViewModel>()
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
            val sync = RealSyncService(db, driver, api, conexion,
                object : NetworkMonitor { override val enLinea = MutableStateFlow(true) },
                object : NotificadorSistema { override fun mostrar(id: String, titulo: String, mensaje: String) {} }, backgroundScope, SessionRepositoryImpl(db))
            val proveedorRepo = ProveedorRepositoryImpl(db)
            val recojoRepo = RecojoRepositoryImpl(db)
            val labRepo = ResultadoLaboratorioRepositoryImpl(db)
            val avisosRepo = ComunicadoRepositoryImpl(db)
            val turnoRepo = TurnoRepositoryImpl(db)
            val root = ProveedorRootViewModel(dto.autorizacion!!.usuario_id, GetProveedorPorUsuarioUseCase(proveedorRepo),
                GetNotificacionesUseCase(labRepo, VentaRepositoryImpl(db), recojoRepo, proveedorRepo, avisosRepo),
                ObservarEstadoSincronizacionUseCase(sync), sync, conexion)
            models += root
            assertNull(root.state.first { it.cargado }.proveedor)
            sync.sincronizarAhora()
            assertNull(sync.ultimoError.value)
            assertEquals(EstadoSincronizacion.SINCRONIZADO, sync.estado.value)
            assertNotNull(conexion.get().ultimaSincronizacion)
            val perfil = ProveedorRepositoryImpl(db).getByUsuarioId(dto.autorizacion!!.usuario_id)
            assertEquals(dto.proveedores.single().id, perfil?.id)
            assertEquals(perfil!!.id, root.state.first { it.proveedor != null }.proveedor!!.id)
            val panel = ProveedorDashboardViewModel(perfil.id, proveedorRepo, recojoRepo, AcopiadorRepositoryImpl(db), GetDashboardProveedorUseCase(recojoRepo, AcopiadorRepositoryImpl(db), turnoRepo))
            val entregas = ProveedorEntregasViewModel(perfil.id, recojoRepo, turnoRepo)
            val laboratorio = ProveedorLaboratorioViewModel(perfil.id, labRepo, turnoRepo)
            val comunicados = ProveedorComunicadosViewModel(ObserveComunicadosUseCase(avisosRepo))
            models += listOf(panel, entregas, laboratorio, comunicados)
            assertNotNull(panel.state.first { !it.cargando }.dashboard)
            val fechaFixture = kotlinx.datetime.LocalDate.parse(dto.recojos.single().fecha)
            entregas.onFecha(fechaFixture)
            laboratorio.onFecha(fechaFixture)
            assertEquals(12.0, entregas.state.first { it.entregas.isNotEmpty() }.totalLitros)
            assertEquals(1, laboratorio.state.first { it.resultados.isNotEmpty() }.resultados.size)
            assertEquals("Aviso de prueba", comunicados.comunicados.first { it.isNotEmpty() }.single().titulo)
            assertEquals(1, db.recojoQueries.selectAll().executeAsList().size)
            assertEquals(1, db.resultadoLaboratorioQueries.selectAll().executeAsList().size)
            assertEquals(1, db.comunicadoQueries.selectAll().executeAsList().size)
            val fecha = conexion.get().ultimaSincronizacion
            for (code in listOf(403, 404, 500)) {
                errorHttp = code
                sync.sincronizarAhora()
                assertEquals(EstadoSincronizacion.SIN_SENAL, sync.estado.value)
                assertTrue(sync.ultimoError.value!!.contains(code.toString()))
                assertNull(sync.progreso.value)
                assertEquals(fecha, conexion.get().ultimaSincronizacion)
                assertNotNull(ProveedorRepositoryImpl(db).getByUsuarioId(dto.autorizacion!!.usuario_id))
            }
            errorHttp = null
            sync.sincronizarAhora()
            assertNull(sync.ultimoError.value)
            assertEquals(EstadoSincronizacion.SINCRONIZADO, sync.estado.value)
            errorHttp = 401
            sync.sincronizarAhora()
            assertNull(conexion.get().token)
            assertEquals(fecha, conexion.get().ultimaSincronizacion)
            errorHttp = null
            conexion.guardarToken("token-fixture")
            sync.sincronizarAhora()
            assertEquals(EstadoSincronizacion.SINCRONIZADO, sync.estado.value)
            } finally { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain(); client.close(); server.stop(0) }
        }
    }
}
