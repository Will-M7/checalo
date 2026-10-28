package com.example.ecolacteoshuata.yape

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class HuataReceptionQrTest {
    @Test fun qrRelativoUsaLaConexionHuataYSuSesion() = runTest {
        val authorized = AtomicBoolean(false)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val qr = byteArrayOf(1,2,3,4)
        server.createContext("/storage/pagos/qr/test.png") {
            authorized.set(it.requestHeaders.getFirst("Authorization") == "Bearer fixture-session")
            it.sendResponseHeaders(200, qr.size.toLong()); it.responseBody.use { body -> body.write(qr) }
        }
        server.start()
        val client = HttpClient(OkHttp) { followRedirects = false }
        try {
            assertContentEquals(qr,loadReceptionQr(client,"http://127.0.0.1:${server.address.port}","/storage/pagos/qr/test.png","fixture-session"))
            assertTrue(authorized.get())
        } finally { client.close(); server.stop(0) }
    }

    @Test fun qrExternoORedirigidoNoRecibeLaSesion() = runTest {
        val escaped = AtomicBoolean(false)
        val other = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        other.createContext("/qr") { escaped.set(true); it.sendResponseHeaders(200,-1); it.close() }; other.start()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/redirect") { it.responseHeaders.add("Location","http://127.0.0.1:${other.address.port}/qr"); it.sendResponseHeaders(307,-1); it.close() }; server.start()
        val client = HttpClient(OkHttp) { followRedirects = false; expectSuccess = false }
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertFailsWith<IllegalStateException> { loadReceptionQr(client,base,"http://127.0.0.1:${other.address.port}/qr","fixture-session") }
            assertFailsWith<IllegalStateException> { loadReceptionQr(client,base,"/redirect","fixture-session") }
            assertFalse(escaped.get())
        } finally { client.close(); server.stop(0); other.stop(0) }
    }
}
