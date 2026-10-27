package com.example.ecolacteoshuata.data.remote.datasource

import com.example.ecolacteoshuata.data.remote.ApiConfig
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class PaginaFinanzas<T>(val data: List<T> = emptyList(), val current_page: Int = 1, val last_page: Int = 1)
@Serializable data class OpcionFinanzas(val id: Long, val nombre: String)
@Serializable data class MovimientoFinanzas(val id: Long, val tipo: String, val origen: String, val estado: String, val amount_minor: Long, val pendiente_rendir_minor: Long = 0, val created_at: String, val vendedor_id: Long? = null, val custodio_id: Long? = null, val receptor_pago_id: Long? = null)
@Serializable data class RespuestaFinanzas(val resumen: Map<String, Long>, val actividad: Map<String, Long>, val movimientos: PaginaFinanzas<MovimientoFinanzas>, val usuarios: List<OpcionFinanzas>, val receptores: List<OpcionFinanzas>, val tipos: List<String>)
@Serializable data class ClienteFinanzas(val id: Long, val nombre: String, val contacto: String? = null, val email: String? = null, val saldo_minor: Long, val recargado_minor: Long, val consumido_minor: Long, val pedidos_total: Long, val pedidos_pendientes: Long, val pedidos_pagados: Long)
@Serializable data class PedidoClienteFinanzas(val id: Long, val fecha: String, val vendedor: String, val total: String, val estado: String, val pago_estado: String)
@Serializable data class RendicionFinanzas(val ingreso: Long, val amount_minor: Long, val clave: String, val referencia: String, val recibido: Boolean)
@Serializable data class ConfirmacionFinanzas(val id: Long, val confirmado: Boolean)
@Serializable data class DisponibilidadWeb(val id: Long, val nombre: String, val activo: Boolean, val capacidad: Boolean, val autorizado: Boolean, val pausado: Boolean, val estado: String)

/** Transporte al libro Laravel. No acredita dinero ni encola rendiciones offline. */
class FinanzasRemota(private val client: HttpClient, private val config: ApiConfig, private val conexion: ConexionServidorRepository) {
    private suspend fun url(path: String): String {
        val c = conexion.get()
        require(c.activa && !c.token.isNullOrBlank()) { "Conecta con Laravel para consultar o confirmar esta operación." }
        return config.baseUrl(c.host, c.puerto) + path
    }
    private suspend fun comprobar(r: HttpResponse): HttpResponse {
        if (!r.status.isSuccess()) {
            val json = runCatching { Json.parseToJsonElement(r.bodyAsText()).jsonObject }.getOrNull()
            val detalle = json?.get("errors")?.jsonObject?.values?.firstOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            error(detalle ?: json?.get("message")?.jsonPrimitive?.contentOrNull ?: "El servidor rechazó la operación (${r.status.value}).")
        }
        return r
    }
    suspend fun resumen(f: Map<String, String>, pagina: Int): RespuestaFinanzas = comprobar(client.get(url("finanzas")) { f.filterValues { it.isNotBlank() }.forEach { (k,v) -> parameter(k,v) }; parameter("page", pagina) }).body()
    suspend fun clientes(f: Map<String, String>, pagina: Int): PaginaFinanzas<ClienteFinanzas> = comprobar(client.get(url("finanzas/clientes")) { f.filterValues { it.isNotBlank() }.forEach { (k,v) -> parameter(k,v) }; parameter("page", pagina) }).body()
    suspend fun pedidos(id: Long, pagina: Int): PaginaFinanzas<PedidoClienteFinanzas> = comprobar(client.get(url("finanzas/clientes/$id/pedidos")) { parameter("page", pagina) }).body()
    suspend fun rendir(d: RendicionFinanzas): ConfirmacionFinanzas = comprobar(client.post(url("finanzas/rendiciones")) { contentType(ContentType.Application.Json); setBody(d) }).body()
    suspend fun disponibilidad(): DisponibilidadWeb = comprobar(client.get(url("vendedores-web/propio"))).body()
    suspend fun vendedores(): List<DisponibilidadWeb> = comprobar(client.get(url("vendedores-web"))).body()
    suspend fun pausar(v: Boolean): DisponibilidadWeb = comprobar(client.post(url("vendedores-web/pausa")) { contentType(ContentType.Application.Json); setBody(buildJsonObject { put("pausado", v) }) }).body()
    suspend fun autorizar(id: Long, v: Boolean): DisponibilidadWeb = comprobar(client.post(url("vendedores-web/$id/autorizacion")) { contentType(ContentType.Application.Json); setBody(buildJsonObject { put("autorizado", v) }) }).body()
}
