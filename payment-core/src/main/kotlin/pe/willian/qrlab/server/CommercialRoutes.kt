package pe.willian.qrlab.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import pe.willian.qrlab.payment.PaymentIdempotencyConflictException

/** No laboratory routes or second Yape authority are installed in this application. */
fun Application.commercialModule(payments: CanonicalPayments) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ReceiverAdminDenied> { call,_ -> call.respond(HttpStatusCode.Forbidden,ErrorDto("receiver-admin-forbidden","Receiver administration scope required")) }
        exception<ReceiverAdminConflict> { call,e -> call.respond(HttpStatusCode.Conflict,ErrorDto(e.reason,"Receiver administration rejected")) }
        exception<AttemptLimit> { call,_ -> call.response.headers.append("Retry-After","60"); call.respond(HttpStatusCode.TooManyRequests,ErrorDto("rate-limited","Wait one minute before retrying")) }
        exception<PaymentIdempotencyConflictException> { call,_ -> call.respond(HttpStatusCode.Conflict,ErrorDto("idempotency-conflict","Immutable payment reference conflict")) }
        exception<NoSuchElementException> { call,_ -> call.respond(HttpStatusCode.NotFound,ErrorDto("not-found","Not found")) }
        exception<IllegalArgumentException> { call,_ -> call.respond(HttpStatusCode.BadRequest,ErrorDto("bad-request","Invalid request")) }
        exception<IllegalStateException> { call,_ -> call.respond(HttpStatusCode.Conflict,ErrorDto("conflict","Operation unavailable or conflicting evidence")) }
        exception<Throwable> { call,_ -> call.respond(HttpStatusCode.BadRequest,ErrorDto("invalid-request","Request could not be processed")) }
    }
    fun token(call: ApplicationCall) = call.request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ") ?: ""
    suspend fun transport(call: ApplicationCall): Boolean {
        val local=call.request.local
        if(local.scheme=="https" || (payments.config.allowLocalHttp && local.remoteHost in listOf("127.0.0.1","::1","localhost"))) return true
        call.respond(HttpStatusCode.Forbidden,ErrorDto("tls-required","Secure transport required")); return false
    }
    suspend fun client(call: ApplicationCall): PaymentClient? {
        if(!transport(call)) return null
        val principal=payments.config.client(token(call))
        if(principal==null) call.respond(HttpStatusCode.Unauthorized,ErrorDto("unauthorized","Client authentication required"))
        return principal
    }
    suspend fun device(call: ApplicationCall): PaymentDevice? {
        if(!transport(call)) return null
        val principal=payments.registry.device(token(call))
        if(principal==null) call.respond(HttpStatusCode.Unauthorized,ErrorDto("unauthorized","Device authentication required"))
        return principal
    }
    routing {
        get("/health") { call.respond(mapOf("status" to "ok","mode" to "commercial")) }
        get("/api/receivers") { val c=client(call) ?: return@get; call.respond(payments.receivers(c)) }
        post("/api/admin/receivers") { val c=client(call) ?: return@post; call.respond(payments.registry.registerReceiver(c,call.receive())) }
        get("/api/admin/devices") { val c=client(call) ?: return@get; call.respond(payments.registry.list(c)) }
        post("/api/admin/devices") { val c=client(call) ?: return@post; call.respond(payments.registry.registerDevice(c,call.receive())) }
        post("/api/admin/devices/{id}/state") { val c=client(call) ?: return@post; call.respond(payments.registry.changeDevice(c,call.parameters["id"]!!,call.receive())) }
        post("/api/admin/devices/{id}/grant") { val c=client(call) ?: return@post; call.respond(payments.registry.grant(c,call.parameters["id"]!!,call.receive())) }
        post("/api/admin/receivers/{id}/state") {
            val c=client(call) ?: return@post
            if(!c.receiverAdministration) { call.respond(HttpStatusCode.Forbidden,ErrorDto("receiver-admin-forbidden","Receiver administration scope required")); return@post }
            call.respond(payments.receiverAdministration.execute(c,call.parameters["id"]!!,call.receive()))
        }
        route("/api/payments") {
            post { val c=client(call) ?: return@post; call.respond(HttpStatusCode.Created,payments.create(c,call.receive())) }
            get("/{id}") { val c=client(call) ?: return@get; call.respond(payments.get(c,call.parameters["id"]!!)) }
            post("/{id}/resolve-yape") { val c=client(call) ?: return@post; call.respond(payments.resolve(c,call.parameters["id"]!!,call.receive<ResolveYapeRequest>().securityCode)) }
            post("/{id}/cancel") { val c=client(call) ?: return@post; call.respond(payments.cancel(c,call.parameters["id"]!!)) }
            get("/{id}/events") {
                val c=client(call) ?: return@get; val id=call.parameters["id"]!!
                payments.get(c,id)
                call.respondTextWriter(ContentType.Text.EventStream) {
                    var last=""
                    while(true) {
                        val view=payments.get(c,id); val data=Json.encodeToString(PaymentStatusEventDto(id,view.status))
                        if(data!=last) { write("retry: 2000\nevent: payment-status\ndata: $data\n\n"); flush(); last=data }
                        if(view.status!="PENDING") break
                        delay(1000)
                    }
                }
            }
        }
        post("/api/bridge/notifications") { val d=device(call) ?: return@post; call.respond(HttpStatusCode.Accepted,payments.receive(d,call.receive())) }
        post("/api/bridge/heartbeat") { val d=device(call) ?: return@post; payments.heartbeat(d,call.receive<BridgeHeartbeat>().listenerConnected); call.respond(mapOf("accepted" to true)) }
    }
}
