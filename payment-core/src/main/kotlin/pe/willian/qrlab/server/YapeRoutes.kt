package pe.willian.qrlab.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import pe.willian.qrlab.yape.YapeLabInjectRequest
import pe.willian.qrlab.yape.YapeManualResolveRequest
import pe.willian.qrlab.yape.YapeNotificationViewDto
import pe.willian.qrlab.yape.YapeRawNotificationRequest
import pe.willian.qrlab.yape.YapeResolveRequest
import pe.willian.qrlab.yape.YapeStateDto
import pe.willian.qrlab.yape.YapeWaitCreateRequest
import pe.willian.qrlab.yape.YapeWaitService

/**
 * Rutas del MODELO 2 de detección Yape (LAB).
 *
 * - Múltiples ventas WAITING simultáneas (auto match con UN candidato).
 * - Resolución de ambigüedad por código HMAC o nombre exacto normalizado,
 *   diseñada para usarse también desde checkout web (sin sesión): el
 *   backend SIEMPRE contrasta contra notificaciones reales recibidas.
 * - Separadas del motor de pagos existente; sin APIs de Yape.
 */
fun Route.yapeRoutes(yape: YapeWaitService) {

    // Crea UNA venta WAITING más (no cancela las anteriores: Modelo 2).
    post("/api/yape/wait") {
        val request = call.receive<YapeWaitCreateRequest>()
        call.respond(yape.createWait(request.amountMinor, request.reference))
    }

    // Última venta creada. Sin ventas -> 404.
    get("/api/yape/wait/current") {
        val current = yape.current()
            ?: throw NoSuchElementException("no hay espera Yape actual")
        call.respond(current)
    }

    // Todas las ventas (tabla de la UI: Venta / Monto / Estado).
    get("/api/yape/waits") {
        val status = call.request.queryParameters["status"]?.uppercase()
        val waits = yape.listWaits()
        call.respond(status?.let { requested -> waits.filter { it.status == requested } } ?: waits)
    }

    // Cancela una venta WAITING (una CANCELLED ya no es candidata).
    post("/api/yape/waits/{paymentId}/cancel") {
        val paymentId = call.parameters["paymentId"]
            ?: throw IllegalArgumentException("falta parámetro paymentId")
        call.respond(yape.cancelWait(paymentId))
    }

    /**
     * Resolución de caso ambiguo (online-ready). Body: securityCode
     * (3 dígitos) O payerName. Exactamente 1 notificación real coincide ->
     * PAID; 0 o 2+ -> 409 sin revelar el código esperado.
     */
    post("/api/yape/waits/{paymentId}/resolve") {
        val paymentId = call.parameters["paymentId"]
            ?: throw IllegalArgumentException("falta parámetro paymentId")
        val request = call.receive<YapeResolveRequest>()
        call.respond(yape.resolve(paymentId, request.securityCode, request.payerName))
    }

    // SSE del Modelo 2: ventas + último resultado + caso ambiguo.
    get("/api/yape/wait/events") {
        call.respondTextWriter(ContentType.Text.EventStream) {
            var previousPaidIds: Set<String>? = null
            try {
                yape.state.collect { state ->
                    val paidIds = state.waits.filter { it.status == "PAID" }.map { it.id }.toSet()
                    if (previousPaidIds != null) {
                        (paidIds - previousPaidIds!!).forEach { paymentId ->
                            write("event: payment-paid\n")
                            write("data: {\"paymentId\":${Json.encodeToString(paymentId)}}\n\n")
                        }
                    }
                    previousPaidIds = paidIds
                    write("event: yape-state\n")
                    val data = Json.encodeToString(YapeStateDto.serializer(), state)
                    write("data: $data\n\n")
                    write("event: payment-state\n")
                    write("data: $data\n\n")
                    flush()
                }
            } catch (_: CancellationException) {
                // Cliente desconectado: cierre normal del stream.
            }
        }
    }

    // LAB ONLY: puente de notificaciones Android.
    // El contenido raw se procesa en memoria: no se persiste raw text ni se
    // responde con el texto de la notificación (solo metadatos + HMAC).
    post("/api/bridge/raw-notification") {
        val raw = call.receive<YapeRawNotificationRequest>()
        if (!raw.bridgeEventId.isNullOrBlank()) {
            call.respond(HttpStatusCode.Accepted, yape.receiveRawAck(raw))
        } else {
            val debug = yape.receiveRaw(raw)
            call.respond(HttpStatusCode.Accepted, debug)
        }
    }

    // LAB ONLY: inyección estructurada (amount/payerName/securityCode)
    // que pasa por EXACTAMENTE el mismo matcher real.
    route("/api/lab/yape") {
        post("/inject") {
            val request = call.receive<YapeLabInjectRequest>()
            call.respond(HttpStatusCode.Accepted, yape.labInject(request))
        }
        // Fallback LAB: selección manual explícita de un evento ambiguo
        // sin código ni nombre. Nunca automático.
        post("/resolve-manual") {
            val request = call.receive<YapeManualResolveRequest>()
            val paymentId = call.request.queryParameters["paymentId"]
                ?: throw IllegalArgumentException("falta query parameter paymentId")
            call.respond(yape.manualResolve(paymentId, request.notificationId))
        }
        // Diagnóstico admin: notificaciones normalizadas (sin raw text,
        // sin código: solo indicador de que existe HMAC).
        get("/notifications") {
            call.respond<List<YapeNotificationViewDto>>(yape.notifications())
        }
    }

    // Interfaz de la prueba (sin nombres automáticos ni códigos esperados).
    get("/yape-test") {
        val html = this::class.java.getResource("/static/yape-test.html")?.readText()
            ?: throw NoSuchElementException("yape-test no disponible")
        call.respondText(html, ContentType.Text.Html)
    }
}
