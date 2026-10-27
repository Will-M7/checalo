package com.example.ecolacteoshuata.authorization

import com.example.ecolacteoshuata.data.remote.*
import com.example.ecolacteoshuata.data.remote.datasource.*
import com.example.ecolacteoshuata.domain.model.ConexionServidor
import com.example.ecolacteoshuata.domain.model.AutorizacionUsuario
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.presentation.admin.finanzas.*
import com.example.ecolacteoshuata.presentation.navigation.*
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import kotlin.test.*

class FinanzasAndroidTest {
    private class Conexion(var valor: ConexionServidor) : ConexionServidorRepository {
        override suspend fun get() = valor
        override fun observe() = flowOf(valor)
        override suspend fun guardar(conexion: ConexionServidor) { valor = conexion }
        override suspend fun actualizarUltimaSincronizacion(fecha: String) {}
        override suspend fun reiniciarSincronizacion() {}
        override suspend fun guardarToken(token: String?) { valor = valor.copy(token = token) }
    }
    private class Token : TokenStorage {
        override suspend fun save(token: String) {}
        override suspend fun read() = "token-finanzas-aislado"
        override suspend fun clear() {}
    }
    @Test fun importesConservanCentimosSinRedondeoFlotante() {
        assertEquals(10001L, centimosFinanzas("100.01"))
        assertEquals(999999999L, centimosFinanzas("9999999.99"))
        assertEquals("S/ 123456789.01", dineroFinanzas(12345678901L))
        assertEquals("-S/ 0.01", dineroFinanzas(-1))
        assertNull(centimosFinanzas("1.001")); assertNull(centimosFinanzas("-1")); assertNull(centimosFinanzas("1,00"))
        assertEquals(Screen.AdminFinanzas, PantallasNativas.pantallas["admin.finanzas"])
    }
    @Test fun rendicionNoSeConfirmaOfflineNiAnteRechazoYReintentaMismoCuerpo() = runTest {
        val cuerpos = mutableListOf<String>()
        var rechazar = true
        val servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/api/mobile/finanzas/rendiciones") { x ->
            assertEquals("Bearer token-finanzas-aislado", x.requestHeaders.getFirst("Authorization"))
            cuerpos.add(x.requestBody.bufferedReader().readText())
            val texto = if (rechazar) """{"message":"La recepción debe ser real.","errors":{"recibido":["Confirma la recepción real."]}}""" else """{"id":99,"confirmado":true}"""
            val bytes = texto.toByteArray(); x.responseHeaders.add("Content-Type", "application/json")
            x.sendResponseHeaders(if (rechazar) 422 else 200, bytes.size.toLong()); x.responseBody.use { it.write(bytes) }
        }
        servidor.start()
        val conexion = Conexion(ConexionServidor("127.0.0.1", servidor.address.port.toString(), true, null, "token-finanzas-aislado"))
        val client = HttpClientFactory.create(OkHttp.create(), ApiConfig(), Token())
        try {
            val api = FinanzasRemota(client, ApiConfig(), conexion)
            val d = RendicionFinanzas(1, 3001, "00000000-0000-4000-8000-000000000001", "Constancia aislada 001", true)
            val error = assertFailsWith<IllegalStateException> { api.rendir(d) }
            assertTrue(error.message!!.contains("recepción real"))
            rechazar = false
            assertTrue(api.rendir(d).confirmado)
            assertEquals(cuerpos[0], cuerpos[1])
            assertEquals(3001, Json.parseToJsonElement(cuerpos[0]).jsonObject["amount_minor"]!!.jsonPrimitive.int)
            conexion.valor = conexion.valor.copy(activa = false)
            assertFailsWith<IllegalArgumentException> { api.rendir(d) }
            assertEquals(2, cuerpos.size)
        } finally { client.close(); servidor.stop(0) }
    }
}
