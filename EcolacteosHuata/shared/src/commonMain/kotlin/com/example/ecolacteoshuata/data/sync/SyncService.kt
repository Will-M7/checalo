package com.example.ecolacteoshuata.data.sync

import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.PasswordHasher
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.data.remote.datasource.SyncRemoteDataSource
import com.example.ecolacteoshuata.data.remote.datasource.TokenRechazadoException
import com.example.ecolacteoshuata.data.remote.dto.CambioDto
import com.example.ecolacteoshuata.data.remote.dto.NotificacionSyncDto
import com.example.ecolacteoshuata.domain.platform.NotificadorSistema
import com.example.ecolacteoshuata.data.remote.dto.SyncResponseDto
import com.example.ecolacteoshuata.domain.platform.NetworkMonitor
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.TipoComunicado
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import com.example.ecolacteoshuata.core.util.AppResult
import kotlinx.coroutines.sync.withLock
import com.example.ecolacteoshuata.data.mapper.codificarTrazo
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.example.ecolacteoshuata.data.repository.AutorizacionLocal
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Avance de una sincronización en curso, para la pantalla de primera descarga. */
data class ProgresoSync(val etapa: String, val fraccion: Float)

/** Cada cuánto se traen los cambios de Laravel mientras la app está abierta y en línea. */
private const val INTERVALO_ACTUALIZACION_MS = 30_000L

/** Pausa entre un cambio en el teléfono y su subida a Laravel. */
private const val ESPERA_ANTES_DE_SUBIR_MS = 2_000L

/**
 * Tablas locales que reflejan una tabla de Laravel (claves del mapa `ids` del sync). Solo en
 * estas se borran las filas que ya no existen en el servidor.
 */
private val TABLAS_ESPEJO = setOf(
    "empresa", "precio_leche", "descuento", "usuario", "acopiador", "proveedor", "turno",
    "turno_produccion", "ruta", "cliente", "unidad_medida", "insumo", "receta", "receta_detalle",
    "comunicado", "recojo", "resultado_laboratorio", "entrega_planta", "pago_proveedor", "compra",
    "compra_detalle", "produccion", "venta", "horario_asistencia", "dia_asistencia_acopiador",
    "asistencia", "justificacion",
)

/**
 * Dónde se guarda el id de cada tabla en las demás (tabla, columna): al subir un registro creado
 * en el teléfono, su UUID se reemplaza por el id de Laravel en todas ellas.
 */
private val REFERENCIAS = mapOf(
    "usuario" to listOf(
        "acopiador" to "usuario_id", "proveedor" to "usuario_id", "comunicado" to "autor_id",
        "venta" to "vendedor_id", "produccion" to "responsable_id",
        "asistencia" to "usuario_id", "asistencia" to "registrado_por",
    ),
    "acopiador" to listOf(
        "proveedor" to "acopiador_id", "ruta" to "acopiador_id", "turno" to "acopiador_id",
        "recojo" to "acopiador_id", "entrega_planta" to "acopiador_id",
    ),
    "proveedor" to listOf(
        "ruta_proveedor" to "proveedor_id", "recojo" to "proveedor_id",
        "resultado_laboratorio" to "proveedor_id", "pago_proveedor" to "proveedor_id",
    ),
    "turno" to listOf("recojo" to "turno_id", "entrega_planta" to "turno_id", "resultado_laboratorio" to "turno_id"),
    "turno_produccion" to listOf("produccion" to "turno_id"),
    "ruta" to listOf("ruta_proveedor" to "ruta_id"),
    "unidad_medida" to listOf("insumo" to "unidad_medida_id"),
    "insumo" to listOf("receta_detalle" to "insumo_id", "compra_detalle" to "insumo_id"),
    "receta" to listOf("receta_detalle" to "receta_id", "produccion" to "receta_id"),
    "recojo" to listOf("pago_proveedor" to "recojo_id"),
    "compra" to listOf("compra_detalle" to "compra_id"),
    "cliente" to listOf("venta" to "cliente_id"),
    "produccion" to listOf("venta_item" to "produccion_id", "venta_item" to "producto_id"),
    "venta" to listOf("venta_item" to "venta_id"),
    "horario_asistencia" to listOf("asistencia" to "horario_asistencia_id"),
    "asistencia" to listOf("justificacion" to "asistencia_id"),
)

/** Cuántos cambios se mandan por petición, para no armar un JSON gigante tras días sin señal. */
private const val CAMBIOS_POR_ENVIO = 200

/** Los ids de Laravel son numéricos; los creados en el teléfono son UUID y nunca se borran. */
private fun esIdDelServidor(id: String) = id.isNotEmpty() && id.all { it.isDigit() }

/** Cuántos registros de cada tipo llegaron del servidor en la última sincronización. */
data class ConteoSync(val etiqueta: String, val cantidad: Int)

interface SyncService {
    val estado: StateFlow<EstadoSincronizacion>
    val pendientes: StateFlow<Int>
    val progreso: StateFlow<ProgresoSync?>
    val ultimoError: StateFlow<String?>
    val ultimoResumen: StateFlow<List<ConteoSync>>
    fun actualizarPendientes(cantidad: Int)
    suspend fun sincronizarAhora()

    /** Sincronización silenciosa (sin progreso en pantalla): la usa el trabajo en segundo plano. */
    suspend fun sincronizarEnSegundoPlano() = sincronizarAhora()

    /** Olvida la última sincronización y vuelve a descargar todo el sistema. */
    suspend fun descargarTodoDeNuevo()

    /** Prueba un servidor sin guardarlo: milisegundos de respuesta o el motivo del error. */
    suspend fun probarConexion(host: String, puerto: String): AppResult<Long>
}

class FakeSyncService(private val scope: CoroutineScope) : SyncService {
    private val _estado = MutableStateFlow(EstadoSincronizacion.SIN_SENAL)
    override val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()
    override val progreso: StateFlow<ProgresoSync?> = MutableStateFlow(null)
    override val ultimoError: StateFlow<String?> = MutableStateFlow(null)
    override val ultimoResumen: StateFlow<List<ConteoSync>> = MutableStateFlow(emptyList())

    private val _pendientes = MutableStateFlow(0)
    override val pendientes: StateFlow<Int> = _pendientes.asStateFlow()

    init {
        scope.launch {
            delay(2500)
            _estado.value = EstadoSincronizacion.SINCRONIZANDO
            delay(1800)
            _estado.value = EstadoSincronizacion.SINCRONIZADO
        }
    }

    override fun actualizarPendientes(cantidad: Int) {
        _pendientes.value = cantidad
    }

    override suspend fun sincronizarAhora() {
        _estado.value = EstadoSincronizacion.SINCRONIZANDO
        delay(1200)
        _estado.value = EstadoSincronizacion.SINCRONIZADO
        _pendientes.value = 0
    }

    override suspend fun descargarTodoDeNuevo() = sincronizarAhora()

    override suspend fun probarConexion(host: String, puerto: String): AppResult<Long> = AppResult.Success(0)
}

/**
 * Trae los datos del backend Laravel/MySQL y los refleja en SQLite: sin `ultimaSincronizacion`
 * guardada trae todo (primera instalación); con ella, solo lo que cambió desde entonces.
 * Deja intactas las columnas que solo existen en el dispositivo (ver los `upsertFromSync` de
 * cada tabla) y borra lo que se eliminó en Laravel. Antes de descargar sube lo que se creó,
 * editó o eliminó en el teléfono (cola `cambio_pendiente`, ver [RegistroCambios]). Necesita el
 * token del primer login con conexión.
 */
class RealSyncService(
    private val db: AppDatabase,
    private val driver: SqlDriver,
    private val syncApi: com.example.ecolacteoshuata.data.remote.datasource.SincronizacionRemota,
    private val conexionServidorRepository: ConexionServidorRepository,
    private val networkMonitor: NetworkMonitor,
    private val notificador: NotificadorSistema,
    scope: CoroutineScope,
    private val sesion: com.example.ecolacteoshuata.domain.repository.SessionRepository? = null,
) : SyncService {
    private val _estado = MutableStateFlow(EstadoSincronizacion.SIN_SENAL)
    override val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()

    private val _pendientes = MutableStateFlow(0)
    override val pendientes: StateFlow<Int> = _pendientes.asStateFlow()

    private val _progreso = MutableStateFlow<ProgresoSync?>(null)
    override val progreso: StateFlow<ProgresoSync?> = _progreso.asStateFlow()

    private val _ultimoError = MutableStateFlow<String?>(null)
    override val ultimoError: StateFlow<String?> = _ultimoError.asStateFlow()

    private val _ultimoResumen = MutableStateFlow<List<ConteoSync>>(emptyList())
    override val ultimoResumen: StateFlow<List<ConteoSync>> = _ultimoResumen.asStateFlow()

    // El login y la pantalla de primera descarga pueden pedir una sincronización a la vez;
    // la segunda espera a la primera y, como ya habrá `ultimaSincronizacion`, solo trae cambios.
    private val mutex = Mutex()

    init {
        scope.launch {
            var primeraVez = true
            conexionServidorRepository.observe().collect { conexion ->
                val configurada = conexion.activa && conexion.host.isNotBlank()
                val conToken = configurada && conexion.token != null
                when {
                    !configurada -> _estado.value = EstadoSincronizacion.MODO_LOCAL
                    _estado.value == EstadoSincronizacion.MODO_LOCAL -> _estado.value = EstadoSincronizacion.SIN_SENAL
                }
                // Al abrir la app ya instalada y conectada, trae los cambios sin esperar al login.
                // (Si nunca sincronizó, de eso se encarga la pantalla de primera descarga.)
                if (primeraVez && conToken && conexion.ultimaSincronizacion != null) launch { sincronizarAhora() }
                primeraVez = false
            }
        }
        // Apenas se guarda algo en el teléfono se intenta subir. Sin señal queda en la cola
        // `cambio_pendiente` de SQLite y se reintenta en la vuelta periódica de abajo.
        scope.launch {
            db.cambioPendienteQueries.contarRegistros().asFlow().mapToOne(Dispatchers.Default).collectLatest { cantidad ->
                _pendientes.value = cantidad.toInt()
                if (cantidad == 0L) return@collectLatest
                // Espera a que termine de guardarse lo que se está registrando (ej. venta + ítems).
                delay(ESPERA_ANTES_DE_SUBIR_MS)
                val conexion = conexionServidorRepository.get()
                val lista = conexion.activa && conexion.host.isNotBlank() && conexion.token != null &&
                    conexion.ultimaSincronizacion != null
                // Fuera de este collect: la propia subida vacía la cola y no debe cancelarse a sí misma.
                if (lista && !mutex.isLocked) scope.launch { sincronizar(silencioso = true) }
            }
        }
        // Monitor de red (ConnectivityManager / NWPathMonitor): al perder la señal se muestra "Sin
        // señal" sin esperar a que falle una petición; al volver, se sube lo pendiente y se trae lo nuevo.
        scope.launch {
            var anterior: Boolean? = null
            networkMonitor.enLinea.collect { enLinea ->
                if (anterior == enLinea) return@collect
                val volvio = anterior == false && enLinea
                anterior = enLinea
                if (!enLinea) {
                    if (_estado.value != EstadoSincronizacion.MODO_LOCAL) _estado.value = EstadoSincronizacion.SIN_SENAL
                    return@collect
                }
                val conexion = conexionServidorRepository.get()
                val lista = conexion.activa && conexion.host.isNotBlank() && conexion.token != null &&
                    conexion.ultimaSincronizacion != null
                if (volvio && lista && !mutex.isLocked) scope.launch { sincronizar(silencioso = true) }
            }
        }
        // Mientras la app está abierta y en línea, trae los cambios de Laravel cada cierto tiempo.
        // Es silenciosa (sin barra de progreso) y solo pide lo que cambió desde la última vez.
        // Si no hay señal, marca "Sin señal" y lo vuelve a intentar en la siguiente vuelta.
        scope.launch {
            while (true) {
                delay(INTERVALO_ACTUALIZACION_MS)
                // Sin red no se intenta (evita esperar el timeout); el monitor avisa cuando vuelve.
                if (!networkMonitor.hayConexion()) continue
                val conexion = conexionServidorRepository.get()
                val lista = conexion.activa && conexion.host.isNotBlank() && conexion.token != null &&
                    conexion.ultimaSincronizacion != null
                actualizarPendientes(0)
                if (lista && !mutex.isLocked) sincronizar(silencioso = true)
            }
        }
    }

    override suspend fun descargarTodoDeNuevo() {
        conexionServidorRepository.reiniciarSincronizacion()
        sincronizarAhora()
    }

    override suspend fun probarConexion(host: String, puerto: String): AppResult<Long> = try {
        AppResult.Success(syncApi.probar(host, puerto))
    } catch (e: IllegalArgumentException) {
        AppResult.Error(e.message ?: "Datos de conexión inválidos.")
    } catch (e: Exception) {
        AppResult.Error(
            "No responde $host:$puerto. Revisa que Laravel esté encendido con " +
                "php artisan serve --host=0.0.0.0 --port=$puerto y que el teléfono esté en la misma red Wi-Fi.",
            sinConexion = true,
        )
    }

    /**
     * Lo pendiente de subir sale de la cola `cambio_pendiente` (todas las tablas, no solo
     * recojos): [cantidad] se ignora y se cuenta la cola.
     */
    override fun actualizarPendientes(cantidad: Int) {
        _pendientes.value = db.cambioPendienteQueries.contarRegistros().executeAsOne().toInt()
    }

    override suspend fun sincronizarAhora() = sincronizar(silencioso = false)

    override suspend fun sincronizarEnSegundoPlano() = sincronizar(silencioso = true)

    /** [silencioso] = actualización automática en segundo plano: no muestra progreso ni el ícono girando. */
    private suspend fun sincronizar(silencioso: Boolean) = mutex.withLock {
        val conexion = conexionServidorRepository.get()
        if (!conexion.activa || conexion.host.isBlank()) {
            _estado.value = EstadoSincronizacion.MODO_LOCAL
            return@withLock
        }
        val token = conexion.token
        if (token == null) {
            // Recién instalada (o se cambió de servidor): el primer login con conexión trae el token.
            if (!silencioso) {
                _ultimoError.value = "Inicia sesión con conexión al servidor para descargar los datos."
                _progreso.value = null
            }
            return@withLock
        }
        if (!silencioso) {
            _estado.value = EstadoSincronizacion.SINCRONIZANDO
            _ultimoError.value = null
            _progreso.value = ProgresoSync("Conectando con ${conexion.host}:${conexion.puerto}", 0.05f)
        }
        try {
            // Primero se sube lo hecho en el teléfono: así la descarga de abajo ya lo trae con su
            // id de Laravel y no pisa cambios que el servidor todavía no conocía.
            if (!silencioso) _progreso.value = ProgresoSync("Subiendo los cambios del teléfono", 0.1f)
            val autorizacion = syncApi.autorizaciones(conexion.host, conexion.puerto)
            val anterior = AutorizacionLocal.leer(db, autorizacion.usuario_id)
            val usuarioSesion = sesion?.usuarioActual?.value
            if (usuarioSesion != null && usuarioSesion.id != autorizacion.usuario_id) {
                // Una cuenta local antigua puede conservar un UUID anterior al primer login remoto.
                // El perfil propio del token debe demostrar que corresponde a la misma cuenta.
                val identidad = syncApi.sync(conexion.host, conexion.puerto, null).usuarios.firstOrNull { it.id == autorizacion.usuario_id }
                if (usuarioSesion.id.all { it.isDigit() } || identidad?.usuario != usuarioSesion.usuario || identidad.documento != usuarioSesion.documento) {
                    conexionServidorRepository.guardarToken(null)
                    throw IllegalStateException("Inicia sesión con conexión en esta cuenta: el token guardado pertenece a otra identidad. No se envió su cola.")
                }
                conDatosDelServidor { migrarIdentidadUsuario(driver, usuarioSesion.id, autorizacion.usuario_id) }
                AutorizacionLocal.guardar(db, autorizacion)
                sesion?.setUsuario(usuarioSesion.copy(id = autorizacion.usuario_id, autorizacion = autorizacion))
            } else {
                AutorizacionLocal.guardar(db, autorizacion)
                if (usuarioSesion != null) sesion?.setUsuario(usuarioSesion.copy(autorizacion = autorizacion))
            }
            val avisos = subirCambios(conexion.host, conexion.puerto, token, autorizacion.usuario_id)
            // Si Laravel rechazó algo, el teléfono quedó distinto al servidor (ej. una baja que
            // allá no procedió): se descarga todo para volver a dejarlo igual.
            val desde = if (avisos.isEmpty() && anterior?.version == autorizacion.version) conexion.ultimaSincronizacion else null
            val respuesta = syncApi.sync(conexion.host, conexion.puerto, desde)
            if (!silencioso) _progreso.value = ProgresoSync("Datos recibidos, guardando en el teléfono", 0.35f)
            enSegundoPlano = silencioso
            aplicar(respuesta, avisarNuevas = desde != null)
            sesion?.usuarioActual?.value?.let { actual ->
                db.usuarioQueries.selectById(actual.id).executeAsOneOrNull()?.toDomain()?.let { u ->
                    sesion.setUsuario(u.copy(autorizacion = AutorizacionLocal.leer(db, u.id)))
                }
            }
            avisarNotificacionesNuevas()
            conexionServidorRepository.actualizarUltimaSincronizacion(respuesta.servidor_hora)
            if (!silencioso) {
                _ultimoResumen.value = resumen(respuesta)
                _progreso.value = ProgresoSync("Listo", 1f)
            }
            _ultimoError.value = avisos.takeIf { it.isNotEmpty() }
                ?.joinToString("\n", prefix = "Cambios conservados en revisión local (Laravel no los aceptó):\n")
            _estado.value = EstadoSincronizacion.SINCRONIZADO
        } catch (e: TokenRechazadoException) {
            // Usuario desactivado en Laravel o token de otro servidor: hay que volver a iniciar sesión.
            conexionServidorRepository.guardarToken(null)
            _ultimoError.value = "El servidor pide iniciar sesión nuevamente con conexión para seguir descargando datos."
            if (!silencioso) _progreso.value = null
            _estado.value = EstadoSincronizacion.SIN_SENAL
        } catch (e: Exception) {
            println("RealSyncService: no se pudo sincronizar (${e.message})")
            _ultimoError.value = "No se pudo descargar desde ${conexion.host}:${conexion.puerto}. " +
                "Verifique que el servidor Laravel esté encendido (php artisan serve --host=0.0.0.0) " +
                "y que el teléfono esté en la misma red.\n\nDetalle: ${e::class.simpleName}: ${e.message}"
            if (!silencioso) _progreso.value = null
            _estado.value = EstadoSincronizacion.SIN_SENAL
        }
    }

    /**
     * Sube a Laravel lo que se creó, editó o eliminó en el teléfono (cola `cambio_pendiente`) y
     * cambia los UUID de lo creado por el id que les tocó allá. Devuelve los cambios que Laravel
     * rechazó: se conservan íntegros en cambio_revision antes de aplicar la descarga. Sin
     * conexión lanza la excepción y la cola queda intacta para el próximo intento.
     */
    private suspend fun subirCambios(host: String, puerto: String, token: String, actorAutorizado: String): List<String> {
        val avisos = mutableListOf<String>()
        while (true) {
            val cola = db.cambioPendienteQueries.selectTodos().executeAsList()
            if (cola.isEmpty()) break
            val tanda = cola.take(CAMBIOS_POR_ENVIO)
            val hasta = tanda.last().orden
            val actorActual = actorAutorizado
            val ajenos = tanda.filter { it.actor_id != actorActual }
            if (ajenos.isNotEmpty()) {
                avisos += "Trabajo de otra sesión o de una versión anterior conservado para revisión administrativa."
                conDatosDelServidor {
                    ajenos.forEach { e ->
                        conservarRevision(e.tabla, e.registro_id, e.operacion, leerFila(e.tabla, e.registro_id), "Pendiente de otra sesión; requiere revisión del autor.", e.actor_id)
                        driver.execute(null, "DELETE FROM cambio_pendiente WHERE orden = ?", 1) { bindLong(0, e.orden) }
                    }
                }
                continue
            }

            // Varias ediciones del mismo registro viajan como una sola, con la fila tal como está
            // ahora; van en el orden en que aparecieron para que un registro llegue antes que los
            // que lo usan (y una eliminación, después de lo que dependía de él).
            val cambios = tanda.groupBy { it.tabla to it.registro_id }
                .map { (clave, entradas) ->
                    val (tabla, id) = clave
                    val ultima = entradas.last()
                    if (ultima.operacion == "eliminar") {
                        ultima.orden to CambioDto(tabla, id, "eliminar")
                    } else {
                        entradas.first().orden to leerFila(tabla, id)?.let { CambioDto(tabla, id, "guardar", it) }
                    }
                }
                .sortedBy { it.first }
                .mapNotNull { it.second }

            if (cambios.isEmpty()) {
                db.cambioPendienteQueries.borrarHasta(hasta)
                continue
            }
            val respuesta = syncApi.subirCambios(host, puerto, cambios)
            conDatosDelServidor {
                respuesta.errores.forEach { error ->
                    cambios.firstOrNull { it.tabla == error.tabla && it.id == error.id }?.let { cambio ->
                        conservarRevision(cambio.tabla, cambio.id, cambio.operacion, cambio.datos, error.message, actorActual)
                    }
                }
                respuesta.ids.forEach { reasignarId(it.tabla, it.id_local, it.id) }
                db.cambioPendienteQueries.borrarHasta(hasta)
            }
            if (respuesta.ids.isNotEmpty()) {
                driver.notifyListeners(*(RegistroCambios.TABLAS + "ruta_proveedor" + "venta_item").toTypedArray())
            }
            respuesta.errores.forEach { avisos += "• ${etiqueta(it.tabla)}: ${it.message}" }
        }
        _pendientes.value = 0
        return avisos.distinct()
    }

    private fun conservarRevision(tabla: String, id: String, operacion: String, datos: Map<String, String?>?, motivo: String, actor: String?) {
        val version = actor?.let { AutorizacionLocal.leer(db, it)?.version }
        db.autorizacionLocalQueries.revisar(actor, version, tabla, id, operacion,
            Json.encodeToString(CambioDto(tabla, id, operacion, datos)), motivo, nowLocalDateTime().toString())
    }

    /** La fila local completa, columna por columna y como texto; `null` si ya no existe. */
    private fun leerFila(tabla: String, id: String): Map<String, String?>? {
        val columnas = driver.executeQuery(null, "PRAGMA table_info($tabla)", { cursor ->
            val lista = mutableListOf<String>()
            while (cursor.next().value) cursor.getString(1)?.let { lista += it }
            QueryResult.Value(lista)
        }, 0).value
        if (columnas.isEmpty()) return null
        val fila = driver.executeQuery(null, "SELECT ${columnas.joinToString()} FROM $tabla WHERE id = ?", { cursor ->
            QueryResult.Value(
                if (cursor.next().value) columnas.indices.associate { columnas[it] to cursor.getString(it) } else null,
            )
        }, 1) { bindString(0, id) }.value ?: return null
        if (tabla == "venta") return fila + ("items" to itemsDeVenta(id))
        if (tabla != "ruta") return fila
        // Los proveedores de la ruta, en su orden de recojo.
        val proveedores = driver.executeQuery(null, "SELECT proveedor_id FROM ruta_proveedor WHERE ruta_id = ? ORDER BY orden", { cursor ->
            val lista = mutableListOf<String>()
            while (cursor.next().value) cursor.getString(0)?.let { lista += it }
            QueryResult.Value(lista)
        }, 1) { bindString(0, id) }.value
        return fila + ("proveedor_ids" to proveedores.joinToString(","))
    }

    /** Los ítems de la venta como JSON: viajan con la venta, como en `VentaItem` de Laravel. */
    private fun itemsDeVenta(ventaId: String): String = buildJsonArray {
        db.ventaQueries.selectItemsByVenta(ventaId).executeAsList().forEach { item ->
            add(
                buildJsonObject {
                    put("produccion_id", item.produccion_id)
                    put("nombre", item.nombre)
                    put("precio_unitario", item.precio_unitario)
                    put("cantidad", item.cantidad)
                    put("subtotal", item.subtotal)
                },
            )
        }
    }.toString()

    /**
     * Pone el id de Laravel a un registro creado en el teléfono: en su tabla, en todas las
     * columnas que lo usan y en la cola. `OR REPLACE`: si la descarga ya había traído la fila
     * del servidor, queda una sola.
     */
    private fun reasignarId(tabla: String, idLocal: String, idServidor: String) {
        (listOf(tabla to "id") + REFERENCIAS[tabla].orEmpty()).forEach { (t, columna) ->
            driver.execute(null, "UPDATE OR REPLACE $t SET $columna = ? WHERE $columna = ?", 2) {
                bindString(0, idServidor)
                bindString(1, idLocal)
            }
        }
        db.cambioPendienteQueries.cambiarRegistroId(idServidor, tabla, idLocal)
    }

    /** Guarda lo que viene de Laravel sin que los triggers lo anoten como cambio del teléfono. */
    private fun conDatosDelServidor(bloque: () -> Unit) {
        db.transaction {
            db.cambioPendienteQueries.asegurarControl()
            db.cambioPendienteQueries.marcarAplicando(1)
            bloque()
            db.cambioPendienteQueries.marcarAplicando(0)
        }
    }

    private fun etiqueta(tabla: String) = tabla.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun resumen(r: SyncResponseDto): List<ConteoSync> = listOf(
        ConteoSync("Usuarios", r.usuarios.size),
        ConteoSync("Proveedores", r.proveedores.size),
        ConteoSync("Acopiadores", r.acopiadores.size),
        ConteoSync("Rutas", r.rutas.size),
        ConteoSync("Turnos", r.turnos.size + r.turnos_produccion.size),
        ConteoSync("Recojos", r.recojos.size),
        ConteoSync("Laboratorio", r.resultados_laboratorio.size),
        ConteoSync("Entregas a planta", r.entregas_planta.size),
        ConteoSync("Pagos", r.pagos_proveedor.size),
        ConteoSync("Clientes", r.clientes.size),
        ConteoSync("Insumos", r.insumos.size),
        ConteoSync("Recetas", r.recetas.size),
        ConteoSync("Compras", r.compras.size),
        ConteoSync("Producción", r.producciones.size),
        ConteoSync("Ventas", r.ventas.size),
        ConteoSync("Comunicados", r.comunicados.size),
        ConteoSync("Asistencias", r.asistencias.size),
    )

    /** true mientras corre la actualización automática: no se reporta avance por etapas. */
    private var enSegundoPlano = false

    private fun paso(etapa: String, n: Int) {
        if (enSegundoPlano) return
        // 0.35 -> 0.95 repartido entre los 9 grupos de tablas que se guardan.
        _progreso.value = ProgresoSync(etapa, 0.35f + 0.6f * n / 9f)
    }

    /**
     * La campana del usuario se reemplaza con la de Laravel, sin perder lo marcado como leído en
     * el teléfono que aún no se subió. En la primera descarga ([avisarNuevas] = false) no se
     * avisan las viejas: solo las que lleguen después.
     */
    private fun guardarNotificaciones(lista: List<NotificacionSyncDto>, avisarNuevas: Boolean) {
        val locales = db.notificacionQueries.selectTodas().executeAsList().associateBy { it.id }
        db.notificacionQueries.borrarTodas()
        lista.forEach { n ->
            val local = locales[n.id]
            val leida = n.leida || local?.leida == 1L
            db.notificacionQueries.insertar(
                id = n.id,
                tipo = n.tipo,
                titulo = n.titulo,
                mensaje = n.mensaje,
                leida = if (leida) 1L else 0L,
                creada_en = n.creada_en ?: nowLocalDateTime().toString(),
                avisada = local?.avisada ?: if (avisarNuevas && !leida) 0L else 1L,
            )
        }
    }

    /** Lleva al celular (notificación del sistema) las que llegaron y no se avisaron. */
    private fun avisarNotificacionesNuevas() {
        db.notificacionQueries.selectTodas().executeAsList()
            .filter { it.avisada == 0L && it.leida == 0L }
            .forEach {
                notificador.mostrar(it.id, it.titulo, it.mensaje)
                db.notificacionQueries.marcarAvisada(it.id)
            }
    }

    private fun aplicar(r: SyncResponseDto, avisarNuevas: Boolean) {
        r.autorizacion?.let { AutorizacionLocal.guardar(db, it) }
        conDatosDelServidor {
            paso("Guardando empresa y precio de la leche", 1)
            r.empresa.forEach { db.empresaQueries.upsertFromSync(it.id, it.razon_social, it.ruc, it.telefono, it.direccion) }
            r.precio_leche.forEach { db.precioLecheQueries.upsertFromSync(it.id, it.precio_litro, it.vigente_desde) }
            r.descuentos.forEach { db.descuentoQueries.upsertFromSync(it.id, it.nombre, it.porcentaje, if (it.activo) 1L else 0L) }

            r.usuarios.forEach { dto ->
                val rol = Rol.fromServerValue(dto.rol)
                dto.autorizacion?.let { AutorizacionLocal.guardar(db, it) }
                val usuarioQueries = db.usuarioQueries
                val existentePorId = usuarioQueries.selectById(dto.id).executeAsOneOrNull()
                val existentePorNombre = usuarioQueries.selectByUsuario(dto.usuario).executeAsOneOrNull()
                // El id local puede no coincidir con el del servidor si este usuario se creó
                // antes (demo local o login previo); se conserva su password_hash/creado_en y
                // se descarta la fila vieja para que el id quede igual al del servidor, ya que
                // otras tablas (acopiador, proveedor, comunicado) referencian ese id.
                // El hash bcrypt de Laravel manda: así el usuario entra sin señal aunque nunca
                // haya iniciado sesión en este teléfono.
                val passwordHash = dto.password_hash?.takeIf { it.isNotBlank() }
                    ?: existentePorId?.password_hash
                    ?: existentePorNombre?.password_hash
                    ?: PasswordHasher.hash(IdGenerator.newId())
                val creadoEn = existentePorId?.creado_en ?: existentePorNombre?.creado_en ?: nowLocalDateTime().toString()
                if (existentePorNombre != null && existentePorNombre.id != dto.id) {
                    usuarioQueries.deleteById(existentePorNombre.id)
                }
                if (existentePorId != null) {
                    usuarioQueries.update(
                        nombres = dto.nombres,
                        apellidos = dto.apellidos,
                        documento = dto.documento,
                        celular = dto.celular,
                        rol = rol?.name.orEmpty(),
                        usuario = dto.usuario,
                        password_hash = passwordHash,
                        activo = if (dto.activo) 1L else 0L,
                        id = dto.id,
                    )
                } else {
                    usuarioQueries.insert(
                        id = dto.id,
                        nombres = dto.nombres,
                        apellidos = dto.apellidos,
                        documento = dto.documento,
                        celular = dto.celular,
                        rol = rol?.name.orEmpty(),
                        usuario = dto.usuario,
                        password_hash = passwordHash,
                        activo = if (dto.activo) 1L else 0L,
                        creado_en = creadoEn,
                    )
                }
                val adicionales = dto.roles.mapNotNull { Rol.fromServerValue(it) }.filter { it != rol }
                usuarioQueries.actualizarRoles(rol?.name.orEmpty(), Rol.codificarLista(adicionales), dto.id)
                val tomaAsistencia = if (dto.toma_asistencia) 1L else 0L
                usuarioQueries.actualizarTomaAsistencia(tomaAsistencia, dto.id, tomaAsistencia)
            }

            paso("Guardando acopiadores y proveedores", 2)
            r.acopiadores.forEach { db.acopiadorQueries.upsertFromSync(it.id, it.usuario_id, it.ruta_nombre) }

            r.proveedores.forEach {
                db.proveedorQueries.upsertFromSync(
                    id = it.id,
                    usuario_id = it.usuario_id,
                    finca_ubicacion = it.finca_ubicacion,
                    finca_referencia = it.finca_referencia,
                    latitud = it.latitud,
                    longitud = it.longitud,
                    acopiador_id = it.acopiador_id,
                    bloqueado = if (it.bloqueado) 1L else 0L,
                    alteraciones = it.alteraciones.toLong(),
                )
            }

            paso("Guardando turnos y rutas", 3)
            r.turnos.forEach { db.turnoQueries.upsertFromSync(it.id, it.acopiador_id, it.nombre, it.hora_inicio, it.hora_fin) }
            r.turnos_produccion.forEach { db.turnoProduccionQueries.upsertFromSync(it.id, it.nombre, it.hora_inicio, it.hora_fin) }

            r.rutas.forEach { ruta ->
                val trazo = ruta.trazo.mapNotNull { p -> if (p.size >= 2) PuntoGeo(p[0], p[1]) else null }
                db.rutaQueries.upsertFromSync(ruta.id, ruta.acopiador_id, ruta.nombre, if (ruta.activa) 1L else 0L, codificarTrazo(trazo))
                db.rutaQueries.limpiarProveedoresDeRuta(ruta.id)
                ruta.proveedor_ids.forEachIndexed { indice, proveedorId ->
                    db.rutaQueries.asignarProveedor(ruta.id, proveedorId, indice + 1L)
                }
            }

            paso("Guardando clientes", 4)
            r.clientes.forEach {
                val clienteQueries = db.clienteQueries
                if (clienteQueries.selectById(it.id).executeAsOneOrNull() != null) {
                    clienteQueries.actualizarDatosSync(it.nombres, it.apellidos, it.documento, it.compras_count.toLong(), it.id)
                } else {
                    clienteQueries.insert(it.id, it.nombres, it.apellidos, it.documento, it.compras_count.toLong(), 0L, 0.0, 0L, 0.0)
                }
            }

            paso("Guardando insumos y recetas", 5)
            r.unidades_medida.forEach { db.unidadMedidaQueries.upsertFromSync(it.id, it.nombre, it.simbolo) }

            r.insumos.forEach {
                val insumoQueries = db.insumoQueries
                val existente = insumoQueries.selectById(it.id).executeAsOneOrNull()
                // El stock real lo lleva Laravel (compras y producciones de todos los dispositivos).
                val stock = it.stock_actual ?: existente?.stock_actual ?: 0.0
                if (existente != null) {
                    insumoQueries.actualizarDatosSync(it.nombre, it.unidad_medida_id, it.costo_unitario, stock, it.id)
                } else {
                    insumoQueries.insert(it.id, it.nombre, it.unidad_medida_id, 0.0, it.costo_unitario, stock, stock)
                }
            }

            r.recetas.forEach {
                val recetaQueries = db.recetaQueries
                if (recetaQueries.selectById(it.id).executeAsOneOrNull() != null) {
                    recetaQueries.actualizarNombreSync(it.nombre, it.id)
                } else {
                    recetaQueries.insert(it.id, it.nombre, "", null)
                }
            }

            // Fila por fila: con `since` solo llegan los detalles que cambiaron; los eliminados
            // en Laravel se quitan más abajo con la lista de ids.
            r.receta_detalles.forEach { db.recetaQueries.upsertDetalleFromSync(it.id, it.receta_id, it.insumo_id, it.cantidad) }

            paso("Guardando comunicados", 6)
            r.comunicados.forEach {
                val tipo = it.tipo?.uppercase() ?: TipoComunicado.COMUNICADO.name
                val roles = it.roles?.let { valores -> Rol.codificarLista(valores.mapNotNull { v -> Rol.fromServerValue(v) }) }
                db.comunicadoQueries.upsertFromSync(it.id, tipo, it.titulo, it.descripcion, it.fecha, it.autor_id, roles)
            }

            paso("Guardando recojos y laboratorio", 7)
            r.recojos.forEach {
                db.recojoQueries.upsertFromSync(
                    id = it.id,
                    proveedor_id = it.proveedor_id,
                    acopiador_id = it.acopiador_id,
                    turno_id = it.turno_id,
                    fecha = it.fecha,
                    litros = it.litros,
                    estado_leche = it.estado_leche.uppercase(),
                    estado_otro = it.estado_otro,
                    precio_litro = it.precio_litro,
                    total = it.total,
                    pagado = if (it.pagado) 1L else 0L,
                    metodo_pago = it.metodo_pago?.uppercase(),
                    entregado = if (it.entregado) 1L else 0L,
                    litros_entregados = it.litros_entregados ?: it.litros,
                )
            }

            r.resultados_laboratorio.forEach {
                db.resultadoLaboratorioQueries.upsertFromSync(
                    id = it.id,
                    proveedor_id = it.proveedor_id,
                    fecha = it.fecha,
                    turno_id = it.turno_id,
                    // Laravel guarda "conforme" / "no conforme": se normaliza al enum local.
                    resultado = ResultadoTipo.desde(it.resultado).name,
                    densidad = it.densidad,
                    acidez = it.acidez,
                    temperatura = it.temperatura,
                    observaciones = it.observaciones,
                    conforme = if (it.conforme) 1L else 0L,
                    litros = it.litros,
                    grasa = it.grasa,
                    sng = it.sng,
                    proteina = it.proteina,
                    lactosa = it.lactosa,
                    sales = it.sales,
                    total_solidos = it.total_solidos,
                    agua_anadida = it.agua_anadida,
                    punto_congelacion = it.punto_congelacion,
                    ph = it.ph,
                    sn = it.sn,
                    modo = it.modo,
                    hora_analisis = it.hora_analisis,
                    fecha_analisis = it.fecha_analisis,
                    enviado_produccion = if (it.enviado_produccion) 1L else 0L,
                )
            }

            paso("Guardando entregas, pagos y compras", 8)
            r.entregas_planta.forEach {
                db.entregaPlantaQueries.upsertFromSync(
                    it.id, it.acopiador_id, it.turno_id, it.fecha, it.litros_total,
                    it.total_pagado, it.total_deuda, it.proveedores_no_recogidos.toLong(), it.guia,
                )
            }

            r.pagos_proveedor.forEach {
                db.pagoProveedorQueries.upsertFromSync(
                    it.id, it.proveedor_id, it.recojo_id, it.fecha, it.monto, it.metodo_pago.uppercase(), it.periodo,
                )
            }

            r.compras.forEach {
                val compraQueries = db.compraQueries
                if (compraQueries.selectById(it.id).executeAsOneOrNull() != null) {
                    compraQueries.actualizarDatosSync(it.fecha, it.descripcion, it.total, it.id)
                } else {
                    compraQueries.insert(it.id, it.fecha, "", it.descripcion, it.total)
                }
            }

            r.compra_detalles.forEach {
                db.compraQueries.upsertDetalleFromSync(it.id, it.compra_id, it.insumo_id, it.cantidad, it.costo_unitario, it.subtotal)
            }

            r.producciones.forEach {
                db.produccionQueries.upsertFromSync(
                    id = it.id,
                    fecha = it.fecha,
                    receta_id = it.receta_id,
                    cantidad_lotes = it.cantidad_producida,
                    cantidad_producida = it.cantidad_producida,
                    turno_id = it.turno_id,
                    estado = it.estado.uppercase(),
                    nombre = it.nombre,
                    lote = it.lote,
                    cantidad_vendida = it.cantidad_vendida,
                    responsable_id = it.responsable_id,
                )
            }

            r.ventas.forEach { venta ->
                db.ventaQueries.upsertFromSync(
                    id = venta.id,
                    numero_boleta = venta.numero_boleta,
                    cliente_id = venta.cliente_id,
                    cliente_nombre_libre = venta.cliente_nombre_libre,
                    cliente_doc_libre = venta.cliente_doc_libre,
                    fecha = venta.fecha,
                    subtotal = venta.subtotal,
                    descuento = venta.descuento,
                    descuento_tipo = venta.descuento_tipo,
                    total = venta.total,
                    metodo_pago = venta.metodo_pago.uppercase(),
                    efectivo_recibido = venta.efectivo_recibido,
                    vuelto = venta.vuelto,
                    vendedor_id = venta.vendedor_id,
                )
                db.ventaQueries.deleteItemsByVenta(venta.id)
                venta.items.forEach { item ->
                    db.ventaQueries.insertItem(
                        id = item.id,
                        venta_id = venta.id,
                        producto_id = item.produccion_id.orEmpty(),
                        nombre = item.nombre,
                        precio_unitario = item.precio_unitario,
                        cantidad = item.cantidad.toLong(),
                        subtotal = item.subtotal,
                        produccion_id = item.produccion_id,
                    )
                }
            }

            paso("Guardando asistencia", 9)
            r.horarios_asistencia.forEach {
                db.asistenciaQueries.upsertHorarioFromSync(
                    it.id, it.nombre, it.hora_inicio, it.hora_limite_presente, it.hora_limite_tardanza, if (it.activo) 1L else 0L,
                )
            }
            r.dias_asistencia_acopiador.forEach { db.asistenciaQueries.upsertDiaFromSync(it.id, it.fecha) }
            r.asistencias.forEach {
                db.asistenciaQueries.upsertAsistenciaFromSync(
                    id = it.id,
                    usuario_id = it.usuario_id,
                    fecha = it.fecha,
                    horario_asistencia_id = it.horario_asistencia_id,
                    estado = it.estado.uppercase(),
                    hora_marcada = it.hora_marcada,
                    registrado_por = it.registrado_por,
                )
            }
            r.justificaciones.forEach {
                db.asistenciaQueries.upsertJustificacionFromSync(
                    it.id, it.asistencia_id, it.motivo, if (it.tiene_archivo) 1L else 0L, it.estado.uppercase(), it.enviada_en,
                )
            }

            r.notificaciones?.let { guardarNotificaciones(it, avisarNuevas) }

            r.ids?.let { borrarEliminadosEnServidor(it) }
        }
        // Los borrados se hicieron con SQL directo: se avisa a las pantallas que observan esas tablas.
        r.ids?.keys?.filter { it in TABLAS_ESPEJO }?.let { tablas ->
            if (tablas.isNotEmpty()) driver.notifyListeners(*(tablas + "ruta_proveedor" + "venta_item").toTypedArray())
        }
    }

    /**
     * Borra las filas que vinieron de Laravel y allá ya no existen. Las creadas en el teléfono
     * (id UUID) no se tocan. Se compara en memoria para no pasar miles de parámetros a SQLite.
     */
    private fun borrarEliminadosEnServidor(ids: Map<String, List<String>>) {
        ids.forEach { (tabla, vigentes) ->
            if (tabla !in TABLAS_ESPEJO) return@forEach
            val enServidor = vigentes.toHashSet()
            val locales = driver.executeQuery(null, "SELECT id FROM $tabla", { cursor ->
                val lista = mutableListOf<String>()
                while (cursor.next().value) cursor.getString(0)?.let { lista += it }
                QueryResult.Value(lista)
            }, 0).value
            locales.filter { esIdDelServidor(it) && it !in enServidor }.forEach { id ->
                driver.execute(null, "DELETE FROM $tabla WHERE id = ?", 1) { bindString(0, id) }
            }
        }
        driver.execute(null, "DELETE FROM ruta_proveedor WHERE ruta_id NOT IN (SELECT id FROM ruta)", 0)
        driver.execute(null, "DELETE FROM venta_item WHERE venta_id NOT IN (SELECT id FROM venta)", 0)
    }
}
