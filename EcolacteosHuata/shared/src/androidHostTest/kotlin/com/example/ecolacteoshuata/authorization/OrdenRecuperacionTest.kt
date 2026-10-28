package com.example.ecolacteoshuata.authorization

import app.cash.sqldelight.db.QueryResult
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.presentation.login.PrimeraDescargaViewModel
import com.example.ecolacteoshuata.presentation.login.EtapaDescarga
import com.example.ecolacteoshuata.data.remote.datasource.*
import com.example.ecolacteoshuata.data.remote.dto.*
import com.example.ecolacteoshuata.data.sync.*
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.platform.*
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrdenRecuperacionTest {
    private fun base(d: SqliteTestDriver): AppDatabase {
        AppDatabase.Schema.create(d); RegistroCambios.instalar(d); return AppDatabase(d)
    }
    @Test fun altasLocalesVanPrimeroYEditarOReemplazarNoCambiaSuLugar() {
        SqliteTestDriver().use { d ->
            val db = base(d)
            db.unidadMedidaQueries.insert("z-uuid-antiguo", "Primero", "p")
            db.unidadMedidaQueries.insert("a-uuid-nuevo", "Segundo", "s")
            assertEquals(listOf("a-uuid-nuevo", "z-uuid-antiguo"), db.unidadMedidaQueries.selectAll().executeAsList().map { it.id })
            db.unidadMedidaQueries.update("Primero editado", "p", "z-uuid-antiguo")
            db.cambioPendienteQueries.marcarAplicando(1)
            db.unidadMedidaQueries.upsertFromSync("z-uuid-antiguo", "Primero confirmado", "p")
            db.cambioPendienteQueries.marcarAplicando(0)
            val filas = db.unidadMedidaQueries.selectAll().executeAsList()
            assertEquals(listOf("a-uuid-nuevo", "z-uuid-antiguo"), filas.map { it.id })
            assertEquals("Primero confirmado", filas.last().nombre)
            assertEquals(2, filas.size)
        }
    }
    @Test fun fechasRealesPredominanYIdsNumericosDesempatanSinOrdenLexicografico() {
        SqliteTestDriver().use { d ->
            val db = base(d)
            db.cambioPendienteQueries.marcarAplicando(1)
            listOf("10", "2", "1").forEach { db.unidadMedidaQueries.upsertFromSync(it, "Unidad $it", "u") }
            listOf("10", "2").forEach { db.ordenAltaQueries.guardarFechaServidor("2026-10-27T10:00:00.000Z", "unidad_medida", it) }
            db.ordenAltaQueries.guardarFechaServidor("2026-10-28T10:00:00.000Z", "unidad_medida", "1")
            db.cambioPendienteQueries.marcarAplicando(0)
            assertEquals(listOf("1", "10", "2"), db.unidadMedidaQueries.selectAll().executeAsList().map { it.id })
        }
    }
    @Test fun migracion21PreservaFilasHistoricasYSusFechasDesconocidas() {
        SqliteTestDriver().use { d ->
            AppDatabase.Schema.create(d)
            val db = AppDatabase(d)
            db.unidadMedidaQueries.insert("historica", "Conservar", "c")
            d.execute(null, "DROP TABLE orden_alta", 0)
            AppDatabase.Schema.migrate(d, 21, 22)
            RegistroCambios.instalar(d)
            assertEquals("Conservar", db.unidadMedidaQueries.selectAll().executeAsList().single().nombre)
            val fecha = d.executeQuery(null, "SELECT creado_en FROM orden_alta WHERE tabla='unidad_medida' AND registro_id='historica'", { c -> c.next(); QueryResult.Value(c.getString(0)) }, 0).value
            assertNull(fecha)
        }
    }
    private class Conexion : ConexionServidorRepository {
        val valor = MutableStateFlow(ConexionServidor("127.0.0.1", "8000", false, null, "sesion-valida"))
        override suspend fun get() = valor.value
        override fun observe() = valor
        override suspend fun guardar(conexion: ConexionServidor) { valor.value = conexion }
        override suspend fun guardarToken(token: String?) { valor.value = valor.value.copy(token = token) }
        override suspend fun reiniciarSincronizacion() { valor.value = valor.value.copy(ultimaSincronizacion = null) }
        override suspend fun actualizarUltimaSincronizacion(fecha: String) { valor.value = valor.value.copy(ultimaSincronizacion = fecha) }
    }
    private class Api(var fallos: Int, val error: () -> Exception) : SincronizacionRemota {
        var llamadas = 0
        override suspend fun autorizaciones(host: String, puerto: String): AutorizacionUsuario {
            llamadas++
            if (llamadas <= fallos) throw error()
            return AutorizacionUsuario("1", version = "prueba", pantallas = listOf("produccion.unidades"))
        }
        override suspend fun sync(host: String, puerto: String, since: String?) = SyncResponseDto("2026-10-27T12:00:00Z", unidades_medida = listOf(UnidadMedidaDto("10", "Servidor", "s")), altas = listOf(AltaSyncDto("unidad_medida", "10", "2026-10-27T10:00:00.000Z")))
        override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>) = SubidaCambiosResponse()
        override suspend fun probar(host: String, puerto: String) = 1L
    }
    private val red = object : NetworkMonitor { override val enLinea = MutableStateFlow(true) }
    private val notificador = object : NotificadorSistema { override fun mostrar(id: String, titulo: String, mensaje: String) {} }

    @Test fun descargaInicialSeRecuperaSolaConEsperaProgresivaYSinPerderToken() = runTest {
        SqliteTestDriver().use { d ->
            val db = base(d); val c = Conexion(); val api = Api(2) { ErrorSyncHttp(503, "descarga") }
            val sync = RealSyncService(db, d, api, c, red, notificador, backgroundScope)
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val vm = PrimeraDescargaViewModel(c, sync)
            try {
            runCurrent(); c.valor.value = c.valor.value.copy(activa = true); runCurrent()
            assertEquals(1, api.llamadas)
            advanceTimeBy(1_999); runCurrent(); assertEquals(1, api.llamadas)
            advanceTimeBy(1); runCurrent(); assertEquals(2, api.llamadas)
            advanceTimeBy(5_000); runCurrent(); assertEquals(3, api.llamadas)
            assertEquals(EstadoSincronizacion.SINCRONIZADO, sync.estado.value)
            assertNotNull(c.get().ultimaSincronizacion)
            assertEquals("sesion-valida", c.get().token)
            assertEquals(listOf("10"), db.unidadMedidaQueries.selectAll().executeAsList().map { it.id })
            assertTrue(db.cambioPendienteQueries.selectTodos().executeAsList().isEmpty())
            assertEquals(EtapaDescarga.LISTA, vm.state.value.etapa)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }
    @Test fun falloPersistenteTerminaTrasCuatroReintentosYSinBuclePeriodicoInicial() = runTest {
        SqliteTestDriver().use { d ->
            val db = base(d); val c = Conexion(); val api = Api(100) { ErrorSyncHttp(500, "descarga") }
            val sync = RealSyncService(db, d, api, c, red, notificador, backgroundScope)
            runCurrent(); c.valor.value = c.valor.value.copy(activa = true); runCurrent()
            sync.sincronizarAhora(); runCurrent()
            advanceTimeBy(53_000); runCurrent()
            assertEquals(5, api.llamadas)
            advanceTimeBy(120_000); runCurrent(); assertEquals(5, api.llamadas)
            assertEquals("sesion-valida", c.get().token)
            assertNull(c.get().ultimaSincronizacion)
            assertNull(sync.progreso.value)
        }
    }
    @Test fun sesionRechazadaYPermisosDenegadosNoSeReintentanComoFalloDeRed() = runTest {
        for (codigo in listOf(401, 403, 404)) {
            SqliteTestDriver().use { d ->
                val db = base(d); val c = Conexion()
                val api = Api(100) { if (codigo == 401) TokenRechazadoException() else ErrorSyncHttp(codigo, "descarga") }
                val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
                try {
                    val sync = RealSyncService(db, d, api, c, red, notificador, scope)
                    runCurrent(); c.valor.value = c.valor.value.copy(activa = true); runCurrent()
                    sync.sincronizarAhora(); runCurrent(); advanceTimeBy(80_000); runCurrent()
                    assertEquals(1, api.llamadas)
                    assertEquals(if (codigo == 401) null else "sesion-valida", c.get().token)
                } finally { scope.cancel() }
            }
        }
    }
    @Test fun cambiarTokenCancelaReintentosDeLaSesionAnterior() = runTest {
        SqliteTestDriver().use { d ->
            val db = base(d); val c = Conexion(); val api = Api(100) { ErrorSyncHttp(503, "descarga") }
            val sync = RealSyncService(db, d, api, c, red, notificador, backgroundScope)
            runCurrent(); c.valor.value = c.valor.value.copy(activa = true); runCurrent()
            sync.sincronizarAhora(); runCurrent()
            c.guardarToken("otra-sesion"); runCurrent(); advanceTimeBy(80_000); runCurrent()
            assertEquals(1, api.llamadas)
            assertEquals("otra-sesion", c.get().token)
        }
    }

    @Test fun contratoLaravelSincronizaAltasConOrdenDistintoDeIdsYNombres() = runTest {
        val payload = javaClass.getResourceAsStream("/huata-orden-altas-sync.json")!!.bufferedReader().readText()
        val dto = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<SyncResponseDto>(payload)
        SqliteTestDriver().use { d ->
            val db = base(d); val c = Conexion(); c.valor.value = c.valor.value.copy(activa = true)
            val api = object : SincronizacionRemota {
                override suspend fun autorizaciones(host: String, puerto: String) = dto.autorizacion!!
                override suspend fun sync(host: String, puerto: String, since: String?) = dto
                override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>) = SubidaCambiosResponse()
                override suspend fun probar(host: String, puerto: String) = 1L
            }
            val sync = RealSyncService(db, d, api, c, red, notificador, backgroundScope)
            sync.sincronizarAhora()
            assertNull(sync.ultimoError.value)
            assertEquals(listOf("Zeta creada después", "Alfa creada antes"), db.unidadMedidaQueries.selectAll().executeAsList().map { it.nombre })
            sync.sincronizarAhora()
            assertEquals(2, db.unidadMedidaQueries.selectAll().executeAsList().size)
            assertEquals(listOf("Zeta creada después", "Alfa creada antes"), db.unidadMedidaQueries.selectAll().executeAsList().map { it.nombre })
        }
    }
}
