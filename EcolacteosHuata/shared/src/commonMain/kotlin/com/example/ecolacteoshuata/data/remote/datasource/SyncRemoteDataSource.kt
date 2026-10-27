package com.example.ecolacteoshuata.data.remote.datasource

import com.example.ecolacteoshuata.data.remote.ApiConfig
import com.example.ecolacteoshuata.data.remote.dto.CambioDto
import com.example.ecolacteoshuata.data.remote.dto.SubidaCambiosRequest
import com.example.ecolacteoshuata.data.remote.dto.SubidaCambiosResponse
import com.example.ecolacteoshuata.data.remote.dto.SyncResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.*
import com.example.ecolacteoshuata.domain.model.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.TimeSource

/**
 * `GET /api/mobile/sync` y `POST /api/mobile/cambios` del backend Laravel (MySQL). El token lo
 * agrega el plugin Auth del HttpClient (ver HttpClientFactory).
 */
interface SincronizacionRemota {
    suspend fun autorizaciones(host: String, puerto: String): com.example.ecolacteoshuata.domain.model.AutorizacionUsuario
    suspend fun sync(host: String, puerto: String, since: String?): SyncResponseDto
    suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>): SubidaCambiosResponse
    suspend fun probar(host: String, puerto: String): Long
}

class SyncRemoteDataSource(
    private val client: HttpClient,
    private val config: ApiConfig,
) : SincronizacionRemota, AdministracionRolesRemota {
    override suspend fun autorizaciones(host: String, puerto: String): com.example.ecolacteoshuata.domain.model.AutorizacionUsuario {
        val respuesta = client.get(config.baseUrl(host, puerto) + "autorizaciones")
        if (respuesta.status == HttpStatusCode.Unauthorized) throw TokenRechazadoException()
        check(respuesta.status.isSuccess()) { "No se pudo actualizar la autorización central: ${respuesta.status}." }
        return respuesta.body()
    }

    override suspend fun asignarRoles(host: String, puerto: String, id: String, roles: Set<String>): com.example.ecolacteoshuata.domain.model.AutorizacionUsuario {
        val respuesta = client.post(config.baseUrl(host, puerto) + "autorizaciones/asignar") {
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject {
                put("usuario_id", kotlinx.serialization.json.JsonPrimitive(id))
                put("roles", kotlinx.serialization.json.JsonArray(roles.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            })
        }
        check(respuesta.status.isSuccess()) { "Laravel no autorizó la asignación (${respuesta.status}). Revisa las funciones del administrador." }
        return respuesta.body()
    }

    private suspend fun exigirRespuestaAdministrativa(r: HttpResponse) {
        if (r.status == HttpStatusCode.Unauthorized) throw TokenRechazadoException()
        if (!r.status.isSuccess()) {
            val mensaje = runCatching {
                val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
                json["errors"]?.jsonObject?.values?.flatMap { it.jsonArray }?.joinToString("\n") { it.jsonPrimitive.content }
                    ?: json["message"]?.jsonPrimitive?.content
            }.getOrNull()
            error(mensaje ?: "Laravel rechazó la operación (${r.status}).")
        }
    }

    override suspend fun catalogoRoles(host: String, puerto: String): CatalogoAdministrativo {
        val r = client.get(config.baseUrl(host, puerto) + "autorizaciones/roles")
        exigirRespuestaAdministrativa(r)
        return r.body()
    }

    override suspend fun guardarRol(host: String, puerto: String, id: Long?, datos: GuardarRolCentral): CatalogoAdministrativo {
        val url = config.baseUrl(host, puerto) + "autorizaciones/roles"
        val r = if (id == null) client.post(url) { contentType(ContentType.Application.Json); setBody(datos) }
            else client.put("$url/$id") { contentType(ContentType.Application.Json); setBody(datos) }
        exigirRespuestaAdministrativa(r)
        return r.body()
    }

    override suspend fun distribuciones(host: String, puerto: String): CatalogoDistribuciones {
        val r = client.get(config.baseUrl(host, puerto) + "autorizaciones/distribuciones")
        exigirRespuestaAdministrativa(r)
        return r.body()
    }

    override suspend fun guardarDistribucion(host: String, puerto: String, datos: GuardarDistribucion): CatalogoDistribuciones {
        val r = client.post(config.baseUrl(host, puerto) + "autorizaciones/distribuciones") { contentType(ContentType.Application.Json); setBody(datos) }
        exigirRespuestaAdministrativa(r)
        return r.body()
    }

    override suspend fun prepararDistribucion(host: String, puerto: String, datos: PrepararDistribucion): VistaDistribucion {
        val r = client.post(config.baseUrl(host, puerto) + "autorizaciones/distribuciones/previsualizar") { contentType(ContentType.Application.Json); setBody(datos) }
        exigirRespuestaAdministrativa(r)
        return r.body()
    }

    override suspend fun confirmarDistribucion(host: String, puerto: String, datos: ConfirmarDistribucion): CatalogoDistribuciones {
        val r = client.post(config.baseUrl(host, puerto) + "autorizaciones/distribuciones/confirmar") { contentType(ContentType.Application.Json); setBody(datos) }
        exigirRespuestaAdministrativa(r)
        return r.body()
    }

    override suspend fun guardarUsuario(host: String, puerto: String, id: String?, datos: GuardarUsuarioCentral): UsuarioAdministrado {
        val url = config.baseUrl(host, puerto) + "autorizaciones/usuarios"
        val r = if (id == null) client.post(url) { contentType(ContentType.Application.Json); setBody(datos) }
            else client.put("$url/$id") { contentType(ContentType.Application.Json); setBody(datos) }
        exigirRespuestaAdministrativa(r)
        return r.body()
    }

    override suspend fun sync(host: String, puerto: String, since: String?): SyncResponseDto {
        val respuesta: HttpResponse = client.get(config.baseUrl(host, puerto) + "sync") {
            if (!since.isNullOrBlank()) parameter("since", since)
        }
        if (respuesta.status == HttpStatusCode.Unauthorized) throw TokenRechazadoException()
        check(respuesta.status.isSuccess()) { "El servidor respondió ${respuesta.status}." }
        return respuesta.body()
    }

    /** Sube lo que se creó, editó o eliminó en el teléfono, en el orden en que pasó. */
    override suspend fun subirCambios(host: String, puerto: String, cambios: List<CambioDto>): SubidaCambiosResponse {
        val respuesta: HttpResponse = client.post(config.baseUrl(host, puerto) + "cambios") {
            contentType(ContentType.Application.Json)
            header("X-Huata-Authorization-Protocol", "1")
            setBody(SubidaCambiosRequest(cambios))
        }
        if (respuesta.status == HttpStatusCode.Unauthorized) throw TokenRechazadoException()
        check(respuesta.status.isSuccess()) { "El servidor respondió ${respuesta.status} al subir los cambios." }
        return respuesta.body()
    }

    /**
     * Comprueba que el servidor responda y devuelve cuántos milisegundos tardó. Un 401 de
     * `/api/mobile/sync` también confirma que es el Laravel de la app y está encendido.
     */
    override suspend fun probar(host: String, puerto: String): Long {
        val inicio = TimeSource.Monotonic.markNow()
        val respuesta: HttpResponse = client.get(config.baseUrl(host, puerto) + "sync") {
            parameter("since", "2999-01-01T00:00:00+00:00")
        }
        check(respuesta.status == HttpStatusCode.Unauthorized || respuesta.status.isSuccess()) {
            "El servidor respondió ${respuesta.status}."
        }
        return inicio.elapsedNow().inWholeMilliseconds
    }
}

/** Laravel no aceptó el token (nunca se inició sesión, se desactivó el usuario o cambió el servidor). */
class TokenRechazadoException : Exception("El servidor pide iniciar sesión nuevamente.")
