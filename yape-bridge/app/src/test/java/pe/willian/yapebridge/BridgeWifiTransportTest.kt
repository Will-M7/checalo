package pe.willian.yapebridge

import org.junit.Assert.*
import org.junit.Test
import pe.willian.yapebridge.bridge.*
import pe.willian.yapebridge.store.BridgeOutboxEvent
import java.net.ServerSocket
import java.net.SocketException
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** HTTP real de JVM con ServerSocket: no usa APIs de servidor ajenas al classpath Android. */
class BridgeWifiTransportTest {
    private val token = "device-fixture-" + "k".repeat(40)
    private val event = BridgeOutboxEvent("stable_wifi_event_0001", 1, CanonicalProtocol.EXPECTED_PACKAGE, 1720000000000, null,
        "Yape! Persona Prueba te envió un pago por S/ 7.00", null, null, null, receiverId = "r1", securityCode = "381")
    private data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String)
    private data class Response(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap())
    private class Server(private val handler: (Request) -> Response) : java.io.Closeable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port get() = socket.localPort
        private val runner = thread(isDaemon = true, name = "bridge-fixture-http") {
            while (!socket.isClosed) {
                try { socket.accept().use { client ->
                    client.soTimeout = 3000
                    val input = client.getInputStream().buffered()
                    fun line(): String {
                        val bytes = java.io.ByteArrayOutputStream()
                        while (true) { val b = input.read(); if (b == -1 || b == 10) break; if (b != 13) bytes.write(b) }
                        return bytes.toString("UTF-8")
                    }
                    val request = line().split(' ')
                    val headers = mutableMapOf<String, String>()
                    while (true) { val value = line(); if (value.isEmpty()) break; headers[value.substringBefore(':').lowercase()] = value.substringAfter(':').trim() }
                    val size = headers["content-length"]?.toIntOrNull() ?: 0
                    val bytes = ByteArray(size); var offset = 0
                    while (offset < size) { val n = input.read(bytes, offset, size - offset); if (n == -1) break; offset += n }
                    val response = handler(Request(request[0], request[1], headers, String(bytes, Charsets.UTF_8)))
                    val body = response.body.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 ${response.status} Fixture\r\nContent-Length: ${body.size}\r\nContent-Type: application/json\r\nConnection: close\r\n" + response.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n"
                    client.getOutputStream().use { it.write(head.toByteArray()); it.write(body) }
                } } catch (_: SocketException) { if (!socket.isClosed) throw IllegalStateException("Fixture HTTP interrumpida") }
            }
        }
        override fun close() { socket.close(); runner.join(1000) }
    }

    @Test fun `one APK can change server hostname and port and transmits identical authenticated events`() {
        val seen = java.util.Collections.synchronizedList(mutableListOf<Request>())
        val handler: (Request) -> Response = { req -> seen.add(req); Response(202, "{\"accepted\":true,\"eventId\":\"${event.eventId}\",\"result\":\"OBSERVED\"}") }
        Server(handler).use { a -> Server(handler).use { b ->
            val http = BridgeHttpTransport(allowHttp = true)
            for (base in listOf("http://localhost:${a.port}", "http://127.0.0.1:${b.port}")) {
                val response = http.post(base, BridgeConfig.NOTIFICATION_PATH, token, CanonicalProtocol.json(event))
                assertEquals(202, response.status); assertTrue(response.body.contains(event.eventId))
            }
            assertEquals(2, seen.size); assertEquals(seen[0].body, seen[1].body)
            seen.forEach { assertEquals("Bearer $token", it.headers["authorization"]); assertEquals("POST", it.method); assertEquals(BridgeConfig.NOTIFICATION_PATH, it.path) }
            assertFalse(seen[0].body.contains("extras")); assertFalse(seen[0].body.contains(token))
        } }
    }
    @Test fun `outage and 503 do not acknowledge and retry keeps event identity`() {
        val failing = AtomicBoolean(true); var sent = 0; var attempts = 0
        val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = Server { r ->
            bodies += r.body
            if (failing.get()) Response(503, "{\"accepted\":false}")
            else Response(202, "{\"accepted\":true,\"eventId\":\"${event.eventId}\"}")
        }
        val base = "http://127.0.0.1:${server.port}"
        val http = BridgeHttpTransport(allowHttp = true)
        fun deliver(): Boolean = CanonicalDelivery.deliver(event, "r1", token, { attempts++ }, { body ->
            val r = http.post(base, BridgeConfig.NOTIFICATION_PATH, token, body)
            DeliveryAck(r.status, r.body == "{\"accepted\":true,\"eventId\":\"${event.eventId}\"}", event.eventId)
        }, { sent++ })
        try {
            assertFalse(deliver()); assertEquals(0, sent)
            failing.set(false)
            assertTrue(deliver()); assertEquals(1, sent); assertEquals(bodies[0], bodies[1])
            server.close()
            assertFalse(deliver()); assertEquals(1, sent); assertEquals(3, attempts)
        } finally { server.close() }
    }
    @Test fun `redirects never transmit credentials to another endpoint`() {
        val leaked = AtomicBoolean(false)
        Server { leaked.set(true); Response(200) }.use { destination ->
            Server { Response(307, headers = mapOf("Location" to "http://127.0.0.1:${destination.port}/")) }.use { redirect ->
                val r = BridgeHttpTransport(true).post("http://127.0.0.1:${redirect.port}", BridgeConfig.HEALTH_PATH, token, "{}")
                assertEquals(307, r.status); assertFalse(leaked.get())
            }
        }
    }
}
