package pe.willian.qrlab.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import pe.willian.qrlab.bridge.RawBridgeNotification

/**
 * Rutas HTTP. Trabajan solo con DTOs del [PaymentService];
 * ninguna ruta cambia estados manualmente ni confirma pagos.
 */
fun Route.paymentRoutes(service: PaymentService) {
    get("/health") {
        call.respond(service.health())
    }
    get("/") {
        call.respondRedirect("/demo")
    }
    get("/demo") {
        val html = this::class.java.getResource("/static/payment-lab.html")?.readText()
            ?: throw NoSuchElementException("demo no disponible")
        call.respondText(html, ContentType.Text.Html)
    }
    staticResources("/static", "static")
    route("/api/payments") {
        post {
            val request = call.receive<CreatePaymentRequest>()
            val created = service.create(request.reference, request.amountMinor, request.currency, request.method)
            call.respond(HttpStatusCode.Created, created)
        }
        get("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            val payment = service.get(id)
                ?: throw NoSuchElementException("pago no encontrado: $id")
            call.respond(payment)
        }
        get("/{id}/qr") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            val bytes = service.qrBytes(id)
                ?: throw NoSuchElementException("QR no encontrado: $id")
            call.respondBytes(bytes, ContentType.Image.PNG)
        }
        post("/{id}/cancel") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            call.respond(service.cancel(id))
        }
        post("/{id}/resolve-yape") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            val request = call.receive<ResolveYapeRequest>()
            call.respond(service.resolveYape(id, request.securityCode))
        }
        get("/{id}/events") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            val current = service.get(id)
                ?: throw NoSuchElementException("pago no encontrado: $id")
            val flow = service.events(id)
                ?: throw NoSuchElementException("pago no encontrado: $id")
            call.respondTextWriter(ContentType.Text.EventStream) {
                write("event: payment-status\n")
                write("data: ${Json.encodeToString(PaymentStatusEventDto(id, current.status))}\n\n")
                flush()
                try {
                    flow.collect { event ->
                        write("event: payment-status\n")
                        write("data: ${Json.encodeToString(event)}\n\n")
                        flush()
                    }
                } catch (_: CancellationException) {
                    // Cliente desconectado: cierre normal del stream.
                }
            }
        }
    }
    // QR estático del negocio (payload privado, nunca hardcodeado).
    // Sin configurar -> 404 con mensaje claro.
    get("/api/yape/static-qr") {
        val png = service.staticQrPng()
            ?: throw NoSuchElementException(
                "QR estático no configurado: defina YAPE_STATIC_QR_PAYLOAD_FILE " +
                    "con el archivo privado del payload",
            )
        call.respondBytes(png, ContentType.Image.PNG)
    }
    // Bridge de notificaciones Android (teléfono del negocio).
    // Exige X-Bridge-Key; sin él -> 401. Nunca loguea el secreto.
    post("/api/bridge/notifications") {
        val key = call.request.headers["X-Bridge-Key"]
        if (!service.checkBridgeKey(key)) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorDto("unauthorized", "X-Bridge-Key ausente o inválido"),
            )
            return@post
        }
        val raw = call.receive<RawBridgeNotification>()
        val (view, duplicate) = service.bridgeReceive(raw)
        call.respond(if (duplicate) HttpStatusCode.OK else HttpStatusCode.Created, view)
    }
    // LAB ONLY - NEVER ENABLE FOR REAL PAYMENT PROVIDERS.
    // Simulador sintético separado del PaymentProvider genérico.
    route("/api/lab/payments") {
        post("/{id}/confirm") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            call.respond(service.labConfirm(id))
        }
        post("/{id}/fail") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            call.respond(service.labFail(id))
        }
        post("/{id}/expire") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("falta parámetro id")
            call.respond(service.labExpire(id))
        }
    }
    // LAB ONLY: eventos del bridge e inyección normalizada sin Android.
    // La inyección pasa por el mismo matcher que las notificaciones reales.
    route("/api/lab/bridge") {
        get("/events") {
            call.respond(service.labBridgeEvents())
        }
        post("/inject") {
            val request = call.receive<LabBridgeInjectRequest>()
            call.respond(HttpStatusCode.Created, service.labInject(request))
        }
    }
}
