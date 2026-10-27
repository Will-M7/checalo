package com.example.ecolacteoshuata.authorization

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.data.remote.*
import com.example.ecolacteoshuata.data.remote.datasource.*
import com.example.ecolacteoshuata.data.remote.dto.*
import com.example.ecolacteoshuata.data.repository.*
import com.example.ecolacteoshuata.data.sync.*
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.platform.*
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.usecase.*
import com.example.ecolacteoshuata.presentation.navigation.*
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.*

/** Contratos generados por el kernel Laravel con SQLite en memoria; HTTP del cliente KMP real. */
class IntegracionRolesAdministrativosTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture(nombre: String): String = File("/tmp/$nombre").takeIf { it.exists() }?.readText()
        ?: javaClass.getResourceAsStream("/$nombre")!!.bufferedReader().readText()
    private fun login() = json.decodeFromString<LoginResponseDto>(fixture("huata-supervisor-login.json"))
    private fun revocado() = json.decodeFromString<AutorizacionUsuario>(fixture("huata-supervisor-revocado.json"))
    private fun base(d: SqliteTestDriver): AppDatabase { AppDatabase.Schema.create(d); RegistroCambios.instalar(d); return AppDatabase(d) }
    private class Conexion(var valor: ConexionServidor = ConexionServidor("127.0.0.1", "1", true, null, "token-prueba")) : ConexionServidorRepository {
        override suspend fun get() = valor
        override fun observe(): Flow<ConexionServidor> = flowOf(valor)
        override suspend fun guardar(conexion: ConexionServidor) { valor = conexion }
        override suspend fun actualizarUltimaSincronizacion(fecha: String) { valor = valor.copy(ultimaSincronizacion = fecha) }
        override suspend fun reiniciarSincronizacion() {}
        override suspend fun guardarToken(token: String?) { valor = valor.copy(token = token) }
    }
    private class Token : TokenStorage {
        var valor: String? = "token-prueba"
        override suspend fun save(token: String) { valor = token }
        override suspend fun read() = valor
        override suspend fun clear() { valor = null }
    }

    @Test fun clienteNativoConsultaCreaEditaRolesYUsuariosPorHttpAutenticado() = runTest {
        val inicial = json.decodeFromString<CatalogoAdministrativo>(fixture("huata-supervisor-catalogo.json"))
        val solicitud = json.decodeFromString<GuardarRolCentral>(fixture("huata-supervisor-modificar.json"))
        val rol = inicial.roles.single { it.codigo == "supervisor_prueba" }
        val llamadas = mutableListOf<Triple<String, String, String>>()
        var catalogo = inicial
        val servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/api/mobile/") { x ->
            val ruta = x.requestURI.path
            val cuerpo = x.requestBody.bufferedReader().readText()
            synchronized(llamadas) { llamadas += Triple(x.requestMethod, ruta, cuerpo) }
            var estado = if (x.requestHeaders.getFirst("Authorization") == "Bearer token-prueba" || ruta.endsWith("/login")) 200 else 401
            val respuesta = when {
                estado == 401 -> "{\"message\":\"No autenticado\"}"
                ruta.endsWith("/login") -> fixture("huata-supervisor-login.json")
                ruta.endsWith("/autorizaciones/asignar") -> json.encodeToString(login().autorizacion!!)
                ruta.endsWith("/usuarios") -> json.encodeToString(UsuarioAdministrado(login().id, login().autorizacion!!))
                ruta.endsWith("/roles/${rol.registro_id}") -> {
                    val recibido = json.decodeFromString<GuardarRolCentral>(cuerpo)
                    catalogo = inicial.copy(roles = inicial.roles.map { if (it.codigo == rol.codigo) it.copy(nombre = recibido.nombre, activo = recibido.activo, pantallas = recibido.pantallas) else it })
                    json.encodeToString(catalogo)
                }
                ruta.endsWith("/roles") && cuerpo.contains("rechazado") -> { estado = 422; "{\"message\":\"Inválido\",\"errors\":{\"pantallas\":[\"No puedes delegar funciones que no posees.\"]}}" }
                ruta.endsWith("/roles") -> json.encodeToString(catalogo)
                ruta.endsWith("/autorizaciones") -> json.encodeToString(inicial.autorizacion)
                else -> { estado = 404; "{}" }
            }
            val bytes = respuesta.toByteArray()
            x.responseHeaders.add("Content-Type", "application/json")
            x.sendResponseHeaders(estado, bytes.size.toLong()); x.responseBody.use { it.write(bytes) }
        }
        servidor.start()
        val cliente = HttpClientFactory.create(OkHttp.create(), ApiConfig(), Token())
        try {
            val puerto = servidor.address.port.toString()
            val api = SyncRemoteDataSource(cliente, ApiConfig())
            val remoto = AuthRemoteDataSourceImpl(cliente, ApiConfig()).login("127.0.0.1", puerto, "supervisor_prueba", "secreto123")
            assertIs<AppResult.Success<UsuarioRemoto>>(remoto); assertNull(remoto.data.rol)
            assertEquals("supervisor_prueba", remoto.data.autorizacion!!.roles.single().id)
            SqliteTestDriver().use { d ->
                val db = base(d); val sesion = SessionRepositoryImpl(db)
                val central = AdministrarRolesUseCase(Conexion(ConexionServidor("127.0.0.1", puerto, true, null, "token-prueba")), api, db, sesion)
                assertIs<AppResult.Success<CatalogoAdministrativo>>(central.cargar())
                assertEquals(Screen.AdminRoles, PantallasNativas.pantallas["admin.roles"])
                assertIs<AppResult.Success<CatalogoAdministrativo>>(central.guardar(null, GuardarRolCentral("Otro rol", "otro_rol", true, listOf("vendedor.clientes"))))
                val guardado = central.guardar(rol.registro_id, solicitud)
                assertIs<AppResult.Success<CatalogoAdministrativo>>(guardado)
                assertEquals(listOf("vendedor.clientes"), guardado.data.roles.single { it.codigo == rol.codigo }.pantallas)
                assertIs<AppResult.Error>(central.guardar(null, GuardarRolCentral("Rechazado", "rechazado", true, listOf("no_existe"))))
                val asignar = AsignarRolesUseCase(Conexion(ConexionServidor("127.0.0.1", puerto, true, null, "token-prueba")), api, db)
                assertIs<AppResult.Success<Unit>>(asignar(login().id, setOf("supervisor_prueba"), false))
                val asignacion = llamadas.single { it.second.endsWith("/asignar") }.third
                assertEquals(JsonArray(listOf(JsonPrimitive("supervisor_prueba"))), json.parseToJsonElement(asignacion).jsonObject["roles"])
                assertFalse(llamadas.any { it.second.endsWith("/cambios") })
                val repo = UsuarioRepositoryImpl(db, d)
                val guardarUsuario = GuardarUsuarioUseCase(repo, PerfilesPorRol(ProveedorRepositoryImpl(db), AcopiadorRepositoryImpl(db)), administracion = central)
                val usuario = guardarUsuario(GuardarUsuarioInput(null, "Supervisor", "Prueba", "11223344", "", null, "supervisor_prueba", "secreto123", true, rolesDinamicos = setOf("supervisor_prueba")))
                assertIs<AppResult.Success<Usuario>>(usuario); assertNull(usuario.data.rol)
                assertNull(repo.getById(usuario.data.id)!!.rol)
                assertEquals("", db.usuarioQueries.selectById(usuario.data.id).executeAsOne().rol)
                assertTrue(db.cambioPendienteQueries.selectTodos().executeAsList().isEmpty())
                val editar = llamadas.single { it.first == "PUT" }.third
                assertEquals(json.parseToJsonElement(fixture("huata-supervisor-modificar.json")), json.parseToJsonElement(editar))
                val crear = llamadas.single { it.second.endsWith("/usuarios") }.third
                assertEquals(JsonArray(listOf(JsonPrimitive("supervisor_prueba"))), json.parseToJsonElement(crear).jsonObject["rolesSeleccionados"])
                assertFalse(json.parseToJsonElement(crear).jsonObject.containsKey("rol"))
            }
        } finally { cliente.close(); servidor.stop(0) }
    }

    @Test fun supervisorSinEnumUsaPantallasNativasYConservaPendientesAlRevocarProduccion() = runTest {
        val dto = login(); val a = dto.autorizacion!!; val remoto = dto.toDomain()!!
        assertNull(remoto.rol); assertEquals(emptyList(), a.categorias_historicas)
        SqliteTestDriver().use { d ->
            val db = base(d); val repo = UsuarioRepositoryImpl(db, d)
            repo.guardarAutorizacion(a)
            repo.guardarDesdeServidor(Usuario(dto.id, dto.nombres, dto.apellidos, dto.documento, dto.celular, null, dto.usuario, "", true, kotlinx.datetime.LocalDateTime.parse("2026-10-26T10:00:00"), autorizacion = a), "secreto123")
            val user = repo.verificarCredenciales(dto.usuario, "secreto123")!!
            assertNull(user.rol); assertTrue(user.rolesAsignados.isEmpty())
            val sesion = SessionRepositoryImpl(db); sesion.setUsuario(user)
            assertEquals(setOf(Screen.VendedorClientes, Screen.ProduccionUnidades), a.pantallas.map { PantallasNativas.pantallas.getValue(it) }.toSet())
            ClienteRepositoryImpl(db).crear(Cliente("cliente-offline", "Trabajo cliente", "", "55667788", 0, false, 0.0, false, 0.0))
            UnidadMedidaRepositoryImpl(db).crear(UnidadMedida("pendiente-supervisor", "Trabajo offline supervisor", "os"))
            assertFails { d.execute(null, "INSERT INTO descuento(id,nombre,porcentaje,activo) VALUES ('no','No',10,1)", 0) }
            val r = revocado()
            val api = object : SincronizacionRemota {
                override suspend fun autorizaciones(host: String, puerto: String) = r
                override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>): SubidaCambiosResponse {
                    assertFalse(AutorizacionLocal.leer(db, a.usuario_id)!!.permite("produccion.unidades"))
                    assertTrue(cambios.any { it.datos?.get("nombre") == "Trabajo offline supervisor" })
                    return SubidaCambiosResponse(ids = listOf(IdAsignadoDto("cliente", "cliente-offline", "cliente-remoto")), errores = listOf(ErrorCambioDto("unidad_medida", "pendiente-supervisor", "AUTHORIZATION_REVOKED")))
                }
                override suspend fun sync(host: String, puerto: String, since: String?) = SyncResponseDto("2026-10-26T12:00:00+00:00", autorizacion = r)
                override suspend fun probar(host: String, puerto: String) = 1L
            }
            val red = object : NetworkMonitor { override val enLinea = MutableStateFlow(false) }
            val notificador = object : NotificadorSistema { override fun mostrar(id: String, titulo: String, mensaje: String) {} }
            RealSyncService(db, d, api, Conexion(), red, notificador, backgroundScope, sesion).sincronizarAhora()
            assertTrue(db.autorizacionLocalQueries.revisiones().executeAsList().any { it.documento.contains("Trabajo offline supervisor") })
            assertFalse(sesion.usuarioActual.value!!.autorizacion!!.permite("produccion.unidades"))
            assertTrue(sesion.usuarioActual.value!!.autorizacion!!.permite("vendedor.clientes"))
            assertFails { db.unidadMedidaQueries.insert("retirado", "No permitido", "np") }
            ClienteRepositoryImpl(db).crear(Cliente("sigue", "Sigue autorizado", "", "22334455", 0, false, 0.0, false, 0.0))
            assertNotNull(ClienteRepositoryImpl(db).getById("sigue"))
        }
    }

    @Test fun administracionSinConexionNoConcedeRolesNiModificaElTrabajoLocal() = runTest {
        SqliteTestDriver().use { d ->
            val db = base(d); val a = login().autorizacion!!
            AutorizacionLocal.guardar(db, a); db.autorizacionLocalQueries.sesion(a.usuario_id)
            db.unidadMedidaQueries.insert("pendiente", "Conservar", "c")
            var llamadas = 0
            fun sinRed(): Nothing { llamadas++; error("Servidor sin conexión") }
            val api = object : AdministracionRolesRemota {
                override suspend fun distribuciones(host: String, puerto: String): CatalogoDistribuciones = sinRed()
                override suspend fun guardarDistribucion(host: String, puerto: String, datos: GuardarDistribucion): CatalogoDistribuciones = sinRed()
                override suspend fun prepararDistribucion(host: String, puerto: String, datos: PrepararDistribucion): VistaDistribucion = sinRed()
                override suspend fun confirmarDistribucion(host: String, puerto: String, datos: ConfirmarDistribucion): CatalogoDistribuciones = sinRed()
                override suspend fun autorizaciones(host: String, puerto: String): AutorizacionUsuario = sinRed()
                override suspend fun asignarRoles(host: String, puerto: String, id: String, roles: Set<String>): AutorizacionUsuario = sinRed()
                override suspend fun catalogoRoles(host: String, puerto: String): CatalogoAdministrativo = sinRed()
                override suspend fun guardarRol(host: String, puerto: String, id: Long?, datos: GuardarRolCentral): CatalogoAdministrativo = sinRed()
                override suspend fun guardarUsuario(host: String, puerto: String, id: String?, datos: GuardarUsuarioCentral): UsuarioAdministrado = sinRed()
            }
            val central = AdministrarRolesUseCase(Conexion(ConexionServidor(activa = false)), api, db, SessionRepositoryImpl(db))
            assertIs<AppResult.Error>(central.cargar())
            assertIs<AppResult.Error>(central.guardar(null, GuardarRolCentral("No", "no", true, listOf("admin.roles"))))
            assertIs<AppResult.Error>(central.guardarUsuario(null, GuardarUsuarioCentral("No", "No", "12345678", "", "no", "1234", true, listOf("administrador"))))
            assertIs<AppResult.Error>(central.distribuciones())
            assertIs<AppResult.Error>(central.guardarDistribucion(GuardarDistribucion(modo = "guardar", nombre = "Sin conexión")))
            assertIs<AppResult.Error>(central.prepararDistribucion(PrepararDistribucion("aplicar", 1)))
            assertIs<AppResult.Error>(central.confirmarDistribucion(ConfirmarDistribucion("no", true)))
            assertEquals(0, llamadas)
            val sinRed = AdministrarRolesUseCase(Conexion(), api, db, SessionRepositoryImpl(db))
            assertIs<AppResult.Error>(sinRed.guardar(null, GuardarRolCentral("No", "no", true, listOf("admin.roles"))))
            assertEquals(1, llamadas)
            assertEquals(a, AutorizacionLocal.leer(db, a.usuario_id))
            assertEquals(1, db.cambioPendienteQueries.selectTodos().executeAsList().size)
        }
    }
}
