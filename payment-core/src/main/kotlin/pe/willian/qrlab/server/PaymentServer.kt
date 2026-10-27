package pe.willian.qrlab.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.willian.qrlab.bridge.BridgeStore
import pe.willian.qrlab.bridge.InMemoryBridgeStore
import pe.willian.qrlab.bridge.SqliteBridgeStore
import pe.willian.qrlab.bridge.YapeBridgeConfig
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentProvider
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.SqlitePaymentRepository
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.payment.yape.YapeNotificationPaymentProvider
import pe.willian.qrlab.yape.SqliteYapeLabStore
import pe.willian.qrlab.yape.YapeWaitService
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Módulo Ktor inyectable (producción y tests usan el mismo).
 *
 * [yapeWait] es el MODELO 2 de detección Yape (múltiples ventas,
 * resolución de ambigüedad), deliberadamente separado del motor de
 * pagos [service]. El default es un servicio en memoria; producción
 * usa [PaymentServer.defaultYapeService] (SQLite).
 */
fun Application.paymentModule(
    service: PaymentService,
    yapeWait: YapeWaitService = YapeWaitService(),
) {
    launch {
        while (isActive) {
            yapeWait.expireDue()
            delay(5_000)
        }
    }
    install(ContentNegotiation) {
        json()
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorDto("bad-request", cause.message ?: "solicitud inválida"),
            )
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorDto("not-found", cause.message ?: "no encontrado"),
            )
        }
        exception<pe.willian.qrlab.payment.PaymentIdempotencyConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorDto("payment_idempotency_conflict", cause.message ?: "conflicto de idempotencia"),
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorDto("conflict", cause.message ?: "transición inválida"),
            )
        }
        exception<Throwable> { call, _ ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorDto("internal-error", "error interno"),
            )
        }
    }
    // LOCAL DEVELOPMENT ONLY: orígenes de desarrollo explícitos,
    // nunca presentar como configuración de producción.
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Bridge-Key")
        allowHost("localhost", schemes = listOf("http", "https"))
        allowHost("127.0.0.1", schemes = listOf("http", "https"))
    }
    routing {
        paymentRoutes(service)
        yapeRoutes(yapeWait)
    }
}

/** Servidor del laboratorio: pagos sintéticos + receptor Yape (sin proveedores reales). */
object PaymentServer {

    /** Ruta de la base de datos (env QR_LAB_DB_PATH o data/qr-lab.db). */
    fun dbPath(): Path = Paths.get(System.getenv("QR_LAB_DB_PATH") ?: "data/qr-lab.db")

    /**
     * Host de bind (env QR_LAB_HOST, default 127.0.0.1).
     *
     * El default NUNCA es 0.0.0.0: desarrollo local seguro por defecto.
     * Para recibir dispositivos de la red local (p. ej. yape-bridge Android)
     * se exporta explícitamente, p. ej. QR_LAB_HOST=0.0.0.0.
     * Cadena vacía o en blanco -> default.
     */
    fun defaultHost(): String =
        System.getenv("QR_LAB_HOST")?.takeIf { it.isNotBlank() } ?: "127.0.0.1"

    /**
     * Servicio por defecto con persistencia SQLite.
     * Pagos e incoming_notifications comparten el archivo DB (sin borrar datos).
     * Los tests usan repositorios temporales para aislamiento.
     */
    fun defaultService(
        qrDir: Path = Paths.get("output", "payments"),
        dbPath: Path = dbPath(),
        bridgeConfig: YapeBridgeConfig = YapeBridgeConfig.fromEnv(),
    ): PaymentService {
        val repository = SqlitePaymentRepository(dbPath).also { it.requireLaboratoryStorage() }
        val bridgeStore: BridgeStore = SqliteBridgeStore(dbPath)
        val synthetic: PaymentProvider = SyntheticPaymentProvider(repository, qrDir)
        return PaymentService(
            provider = synthetic,
            repository = repository,
            simulator = SyntheticPaymentSimulator(repository),
            eventBus = PaymentEventBus(),
            qrDir = qrDir,
            storageName = "sqlite",
            yapeProvider = YapeNotificationPaymentProvider(repository),
            bridgeStore = bridgeStore,
            bridgeConfig = bridgeConfig,
        )
    }

    /** Servicio en memoria (tests rápidos y demos efímeras). */
    fun inMemoryService(
        qrDir: Path,
        bridgeConfig: YapeBridgeConfig = YapeBridgeConfig.fromEnv(),
    ): PaymentService {
        val repository: PaymentRepository = InMemoryPaymentRepository()
        return PaymentService(
            provider = SyntheticPaymentProvider(repository, qrDir),
            repository = repository,
            simulator = SyntheticPaymentSimulator(repository),
            eventBus = PaymentEventBus(),
            qrDir = qrDir,
            storageName = "memory",
            yapeProvider = YapeNotificationPaymentProvider(repository),
            bridgeStore = InMemoryBridgeStore(),
            bridgeConfig = bridgeConfig,
        )
    }

    /**
     * Servicio MODELO 2 con persistencia SQLite: un reinicio conserva
     * ventas y eventos ambiguos (misma DB que el resto del laboratorio,
     * tablas yape_ con esquema idempotente, sin raw text ni códigos).
     */
    fun defaultYapeService(dbPath: Path = dbPath()): YapeWaitService =
        YapeWaitService(store = SqliteYapeLabStore(dbPath))

    /**
     * Levanta Netty y bloquea (wait = true).
     *
     * Host desde QR_LAB_HOST (default 127.0.0.1, ver [defaultHost]);
     * QR_LAB_HOST=0.0.0.0 escucha en todas las interfaces de red.
     * Puerto desde QR_LAB_PORT (default 8080).
     */
    fun start(
        host: String = defaultHost(),
        port: Int = 8080,
        service: PaymentService = defaultService(),
        yapeWait: YapeWaitService = defaultYapeService(),
    ) {
        check(System.getenv("PAYMENT_MODE") == "lab") { "Legacy server is LAB only" }
        embeddedServer(Netty, host = host, port = port) {
            paymentModule(service, yapeWait)
        }.start(wait = true)
    }
}
