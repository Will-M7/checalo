package com.example.ecolacteoshuata.authorization

import app.cash.sqldelight.db.QueryResult
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.data.remote.dto.*
import com.example.ecolacteoshuata.data.remote.datasource.SincronizacionRemota
import com.example.ecolacteoshuata.data.repository.*
import com.example.ecolacteoshuata.data.sync.*
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.platform.*
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.presentation.navigation.PantallasNativas
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.*

class AutorizacionMultiplataformaTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun contrato(revocado: Boolean = false): AutorizacionUsuario {
        val nombre = if (revocado) "huata-auth-revoked.json" else "huata-auth-contract.json"
        val directo = File("/tmp/$nombre")
        val texto = if (directo.exists()) directo.readText() else javaClass.getResourceAsStream("/$nombre")!!.bufferedReader().readText()
        return json.decodeFromString(texto)
    }
    private fun base(driver: SqliteTestDriver): AppDatabase {
        AppDatabase.Schema.create(driver)
        RegistroCambios.instalar(driver)
        return AppDatabase(driver)
    }
    @Test fun rolNuevoDeLaravelSeConservaSinDependerDelEnumYMapeaLasDosPantallas() {
        val a = contrato()
        val user = LoginResponseDto("2", "dinamico", "Prueba", "Central", rol = "dos_funciones", autorizacion = a).toDomain()
        assertNotNull(user)
        assertEquals("dos_funciones", user.autorizacion!!.roles.single().id)
        assertEquals(setOf("produccion.unidades", "vendedor.clientes"), a.pantallas.toSet())
        a.pantallas.forEach { assertEquals(it, PantallasNativas.id(PantallasNativas.pantallas.getValue(it))) }
        a.mapeo_android.forEach { (id, pantalla) -> assertEquals(pantalla, PantallasNativas.pantallas.getValue(id)::class.simpleName) }
        assertFalse(a.permite("vendedor.ventas"))
    }
    @Test fun lasOperacionesOriginalesSeGuardanOfflineYNoSePuedeEscribirUnaFuncionNoAsignada() = runTest {
        SqliteTestDriver().use { driver ->
            val db = base(driver); val a = contrato()
            AutorizacionLocal.guardar(db, a); db.autorizacionLocalQueries.sesion(a.usuario_id)
            ClienteRepositoryImpl(db).crear(Cliente("cliente-offline", "Prueba", "Central", "12345678", 0, false, 0.0, false, 0.0))
            UnidadMedidaRepositoryImpl(db).crear(UnidadMedida("unidad-offline", "Litro", "L"))
            assertEquals("Prueba", ClienteRepositoryImpl(db).getById("cliente-offline")!!.nombres)
            assertEquals(2, db.cambioPendienteQueries.selectTodos().executeAsList().size)
            assertTrue(db.cambioPendienteQueries.selectTodos().executeAsList().all { it.actor_id == a.usuario_id })
            assertFails { driver.execute(null, "INSERT INTO descuento(id,nombre,porcentaje,activo) VALUES ('no','No',10,1)", 0) }
            assertEquals(a.version, AutorizacionLocal.leer(db, a.usuario_id)!!.version)
        }
    }
    private class Conexion : ConexionServidorRepository {
        var valor = ConexionServidor("prueba", "8000", true, null, "token-de-prueba")
        override suspend fun get() = valor
        override fun observe(): Flow<ConexionServidor> = flowOf(valor)
        override suspend fun guardar(conexion: ConexionServidor) { valor = conexion }
        override suspend fun actualizarUltimaSincronizacion(fecha: String) { valor = valor.copy(ultimaSincronizacion = fecha) }
        override suspend fun reiniciarSincronizacion() {}
        override suspend fun guardarToken(token: String?) { valor = valor.copy(token = token) }
    }
    @Test fun reconexionAplicaRevocacionAntesDeSubirYConservaElTrabajoRechazado() = runTest {
        SqliteTestDriver().use { driver ->
            val db = base(driver); val a = contrato(); val revocado = contrato(true)
            AutorizacionLocal.guardar(db, a); db.autorizacionLocalQueries.sesion(a.usuario_id)
            db.unidadMedidaQueries.insert("unidad-offline", "Trabajo pendiente", "tp")
            val pasos = mutableListOf<String>()
            val api = object : SincronizacionRemota {
                override suspend fun autorizaciones(host: String, puerto: String): AutorizacionUsuario { pasos += "autorizacion"; return revocado }
                override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>): SubidaCambiosResponse {
                    pasos += "subir"
                    assertFalse(AutorizacionLocal.leer(db, a.usuario_id)!!.permite("produccion.unidades"))
                    assertEquals("Trabajo pendiente", cambios.single().datos!!["nombre"])
                    return SubidaCambiosResponse(errores = listOf(ErrorCambioDto("unidad_medida", "unidad-offline", "AUTHORIZATION_REVOKED")))
                }
                override suspend fun sync(host: String, puerto: String, since: String?): SyncResponseDto {
                    pasos += "descargar"
                    assertEquals(1, db.autorizacionLocalQueries.revisiones().executeAsList().size)
                    return SyncResponseDto("2026-10-26T12:00:00+00:00", autorizacion = revocado)
                }
                override suspend fun probar(host: String, puerto: String) = 1L
            }
            val red = object : NetworkMonitor { override val enLinea = MutableStateFlow(false) }
            val notificador = object : NotificadorSistema { override fun mostrar(id: String, titulo: String, mensaje: String) {} }
            val sync = RealSyncService(db, driver, api, Conexion(), red, notificador, backgroundScope)
            sync.sincronizarAhora()
            assertEquals(listOf("autorizacion", "subir", "descargar"), pasos)
            val conservado = db.autorizacionLocalQueries.revisiones().executeAsOne()
            assertTrue(conservado.documento.contains("Trabajo pendiente"))
            assertEquals(a.usuario_id, conservado.actor_id)
            assertFails { db.unidadMedidaQueries.insert("otro", "Ya revocado", "r") }
            assertTrue(AutorizacionLocal.leer(db, a.usuario_id)!!.permite("vendedor.clientes"))
        }
    }
    @Test fun migracionVeinteAVeintiunoNoBorraLaColaNiLosRegistrosExistentes() {
        SqliteTestDriver().use { driver ->
            AppDatabase.Schema.create(driver)
            listOf("autorizacion_local", "autorizacion_sesion", "autorizacion_pantalla", "cambio_revision", "autorizacion_migracion").forEach { driver.execute(null, "DROP TABLE $it", 0) }
            driver.execute(null, "ALTER TABLE cambio_pendiente DROP COLUMN actor_id", 0)
            driver.execute(null, "INSERT INTO unidad_medida(id,nombre,simbolo) VALUES ('anterior','Anterior','a')", 0)
            driver.execute(null, "INSERT INTO cambio_pendiente(tabla,registro_id,operacion) VALUES ('unidad_medida','anterior','guardar')", 0)
            driver.execute(null, "INSERT INTO usuario(id,nombres,apellidos,documento,celular,rol,usuario,password_hash,activo,creado_en,toma_asistencia) VALUES ('2','Prueba','Anterior','12345678','','VENDEDOR','anterior','hash-intacto',1,'2026-10-26T10:00:00',1)", 0)
            AppDatabase.Schema.migrate(driver, 20, 21)
            val db = AppDatabase(driver)
            com.example.ecolacteoshuata.data.local.MigracionAutorizacionHeredada.ejecutar(db)
            assertTrue(AutorizacionLocal.leer(db, "2")!!.permite("vendedor.ventas"))
            assertTrue(AutorizacionLocal.leer(db, "2")!!.permite("asistencia.tomar"))
            assertEquals("hash-intacto", db.usuarioQueries.selectById("2").executeAsOne().password_hash)
            AutorizacionLocal.guardar(db, contrato())
            com.example.ecolacteoshuata.data.local.MigracionAutorizacionHeredada.ejecutar(db)
            assertFalse(AutorizacionLocal.leer(db, "2")!!.permite("vendedor.ventas"))
            assertEquals("Anterior", db.unidadMedidaQueries.selectById("anterior").executeAsOne().nombre)
            assertEquals(1, db.cambioPendienteQueries.selectTodos().executeAsList().size)
            assertNull(db.cambioPendienteQueries.selectTodos().executeAsOne().actor_id)
        }
    }
    @Test fun autorizacionYTrabajoOfflineSobrevivenAlCierreDelProceso() {
        val archivo = File.createTempFile("huata-autorizacion-", ".db")
        try {
            val a = contrato()
            SqliteTestDriver("jdbc:sqlite:${archivo.absolutePath}").use { driver ->
                val db = base(driver)
                AutorizacionLocal.guardar(db, a); db.autorizacionLocalQueries.sesion(a.usuario_id)
                db.unidadMedidaQueries.insert("persistente", "Sin señal", "ss")
            }
            SqliteTestDriver("jdbc:sqlite:${archivo.absolutePath}").use { driver ->
                val db = AppDatabase(driver)
                RegistroCambios.instalar(driver)
                assertEquals(a.version, AutorizacionLocal.leer(db, a.usuario_id)!!.version)
                assertTrue(AutorizacionLocal.leer(db, a.usuario_id)!!.permite("produccion.unidades"))
                assertEquals("Sin señal", db.unidadMedidaQueries.selectById("persistente").executeAsOne().nombre)
                assertEquals(a.usuario_id, db.cambioPendienteQueries.selectTodos().executeAsOne().actor_id)
            }
        } finally { archivo.delete() }
    }
    @Test fun sinConexionNoSePierdeLaColaNiLaUltimaAutorizacionConocida() = runTest {
        SqliteTestDriver().use { driver ->
            val db = base(driver); val a = contrato()
            AutorizacionLocal.guardar(db, a); db.autorizacionLocalQueries.sesion(a.usuario_id)
            db.unidadMedidaQueries.insert("offline", "Pendiente sin conexión", "pc")
            val api = object : SincronizacionRemota {
                override suspend fun autorizaciones(host: String, puerto: String): AutorizacionUsuario = throw IllegalStateException("Sin red")
                override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>): SubidaCambiosResponse = error("No debe enviar")
                override suspend fun sync(host: String, puerto: String, since: String?): SyncResponseDto = error("No debe descargar")
                override suspend fun probar(host: String, puerto: String) = 1L
            }
            val red = object : NetworkMonitor { override val enLinea = MutableStateFlow(false) }
            val notificador = object : NotificadorSistema { override fun mostrar(id: String, titulo: String, mensaje: String) {} }
            RealSyncService(db, driver, api, Conexion(), red, notificador, backgroundScope).sincronizarAhora()
            assertEquals(1, db.cambioPendienteQueries.selectTodos().executeAsList().size)
            assertEquals(a.version, AutorizacionLocal.leer(db, a.usuario_id)!!.version)
            db.unidadMedidaQueries.insert("todavia", "Última autorización conocida", "ua")
            assertEquals(2, db.cambioPendienteQueries.selectTodos().executeAsList().size)
        }
    }
    @Test fun pendientesDeOtraSesionNuncaSeEnvianConElTokenActual() = runTest {
        SqliteTestDriver().use { driver ->
            val db = base(driver); val a = contrato(); val otra = a.copy(usuario_id = "otra-cuenta")
            AutorizacionLocal.guardar(db, otra); db.autorizacionLocalQueries.sesion(otra.usuario_id)
            db.unidadMedidaQueries.insert("otro-autor", "Trabajo de otra cuenta", "oc")
            AutorizacionLocal.guardar(db, a); db.autorizacionLocalQueries.sesion(a.usuario_id)
            var envios = 0
            val api = object : SincronizacionRemota {
                override suspend fun autorizaciones(host: String, puerto: String) = a
                override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>): SubidaCambiosResponse { envios++; return SubidaCambiosResponse() }
                override suspend fun sync(host: String, puerto: String, since: String?) = SyncResponseDto("2026-10-26T12:00:00+00:00", autorizacion = a)
                override suspend fun probar(host: String, puerto: String) = 1L
            }
            val red = object : NetworkMonitor { override val enLinea = MutableStateFlow(false) }
            val notificador = object : NotificadorSistema { override fun mostrar(id: String, titulo: String, mensaje: String) {} }
            RealSyncService(db, driver, api, Conexion(), red, notificador, backgroundScope).sincronizarAhora()
            assertEquals(0, envios)
            val revision = db.autorizacionLocalQueries.revisiones().executeAsOne()
            assertEquals(otra.usuario_id, revision.actor_id)
            assertTrue(revision.documento.contains("Trabajo de otra cuenta"))
        }
    }

    @Test fun loginRemotoConservaReferenciasDeUnaCuentaLocalYDevuelveRolesFlexibles() = runTest {
        SqliteTestDriver().use { driver ->
            val db = base(driver); val a = contrato()
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            db.usuarioQueries.insert("telefono-uuid", "Prueba", "Central", "12345678", "51999999999", "VENDEDOR", "dinamico", "hash-anterior", 1, "2026-10-26T10:00:00")
            db.acopiadorQueries.insert("perfil-local", "telefono-uuid", "Ruta conservada")
            val repo = UsuarioRepositoryImpl(db, driver)
            repo.guardarAutorizacion(a)
            repo.guardarDesdeServidor(Usuario(a.usuario_id, "Prueba", "Central", "12345678", "51999999999", Rol.VENDEDOR, "dinamico", "", true, kotlinx.datetime.LocalDateTime.parse("2026-10-26T10:00:00")), "secreto")
            val user = repo.getByUsuario("dinamico")!!
            assertEquals(a.usuario_id, user.id)
            assertEquals("dos_funciones", user.autorizacion!!.roles.single().id)
            assertEquals(a.usuario_id, db.acopiadorQueries.selectById("perfil-local").executeAsOne().usuario_id)
            assertNotNull(repo.verificarCredenciales("dinamico", "secreto"))
            assertTrue(db.autorizacionLocalQueries.revisiones().executeAsList().any { it.tabla == "usuario" })
        }
    }

    @Test fun reconectarUnaSesionConUuidAntiguoActualizaSuIdentidadYAplicaLasRevocaciones() = runTest {
        SqliteTestDriver().use { driver ->
            val db = base(driver); val actual = contrato(true)
            val antigua = contrato().copy(usuario_id = "uuid-antiguo")
            db.usuarioQueries.insert(antigua.usuario_id, "Prueba", "Central", "12345678", "", "VENDEDOR", "dinamico", "hash", 1, "2026-10-26T10:00:00")
            db.cambioPendienteQueries.olvidar("usuario", antigua.usuario_id)
            AutorizacionLocal.guardar(db, antigua)
            val sesion = SessionRepositoryImpl(db)
            sesion.setUsuario(Usuario(antigua.usuario_id, "Prueba", "Central", "12345678", "", Rol.VENDEDOR, "dinamico", "hash", true, kotlinx.datetime.LocalDateTime.parse("2026-10-26T10:00:00"), autorizacion = antigua))
            db.unidadMedidaQueries.insert("pendiente-antiguo", "Trabajo anterior", "ta")
            val propio = UsuarioSyncDto(id = actual.usuario_id, nombres = "Prueba", apellidos = "Central", documento = "12345678", celular = "", rol = "vendedor", usuario = "dinamico", activo = true)
            val api = object : SincronizacionRemota {
                override suspend fun autorizaciones(host: String, puerto: String) = actual
                override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>): SubidaCambiosResponse {
                    assertEquals(actual.usuario_id, sesion.usuarioActual.value!!.id)
                    return SubidaCambiosResponse(errores = listOf(ErrorCambioDto("unidad_medida", "pendiente-antiguo", "Función retirada")))
                }
                override suspend fun sync(host: String, puerto: String, since: String?) = SyncResponseDto("2026-10-26T12:00:00+00:00", autorizacion = actual, usuarios = listOf(propio))
                override suspend fun probar(host: String, puerto: String) = 1L
            }
            val red = object : NetworkMonitor { override val enLinea = MutableStateFlow(false) }
            val notificador = object : NotificadorSistema { override fun mostrar(id: String, titulo: String, mensaje: String) {} }
            RealSyncService(db, driver, api, Conexion(), red, notificador, backgroundScope, sesion).sincronizarAhora()
            assertEquals(actual.usuario_id, sesion.usuarioActual.value!!.id)
            assertFalse(sesion.usuarioActual.value!!.autorizacion!!.permite("produccion.unidades"))
            assertEquals(actual.usuario_id, db.autorizacionLocalQueries.revisiones().executeAsOne().actor_id)
            assertTrue(db.autorizacionLocalQueries.revisiones().executeAsOne().documento.contains("Trabajo anterior"))
        }
    }

}
