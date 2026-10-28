package com.example.ecolacteoshuata.yape

import com.example.ecolacteoshuata.data.remote.*
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class HuataTransportPrivacyTest {
    @Test fun evidenciaYSesionNoSiguenRedireccionesFueraDeLaravel() = runTest {
        val escaped = AtomicBoolean(false)
        val other = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        other.createContext("/evidence") { escaped.set(true); it.sendResponseHeaders(200,-1); it.close() };other.start()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/api/mobile/yape/evidence") { it.responseHeaders.add("Location","http://127.0.0.1:${other.address.port}/evidence");it.sendResponseHeaders(307,-1);it.close() };server.start()
        val tokens = object : TokenStorage { override suspend fun save(token:String) {};override suspend fun read()="session-fixture-0000000000000000000000";override suspend fun clear() {} }
        val original = HttpClientFactory.create(OkHttp.create(),ApiConfig(),tokens)
        val client = original.yapeTransport()
        try {
            val r = client.post("http://127.0.0.1:${server.address.port}/api/mobile/yape/evidence") { setBody("fixture sin datos operativos") }
            assertEquals(307,r.status.value);assertFalse(escaped.get())
        } finally { client.close();original.close();server.stop(0);other.stop(0) }
    }
}
