package com.example.ecolacteoshuata.data.remote.datasource

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.data.remote.ApiConfig
import com.example.ecolacteoshuata.data.remote.dto.ErrorResponseDto
import com.example.ecolacteoshuata.data.remote.dto.LoginRequestDto
import com.example.ecolacteoshuata.data.remote.dto.LoginResponseDto
import com.example.ecolacteoshuata.domain.model.UsuarioRemoto
import com.example.ecolacteoshuata.domain.repository.AuthRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/** `POST /api/mobile/login` del backend Laravel (MySQL), con el HttpClient compartido. */
class AuthRemoteDataSourceImpl(
    private val client: HttpClient,
    private val config: ApiConfig,
) : AuthRemoteDataSource {

    override suspend fun login(host: String, puerto: String, usuario: String, password: String): AppResult<UsuarioRemoto> {
        if (config.limpiarHost(host).isBlank() || puerto.isBlank()) {
            return AppResult.Error("Configure el host / IP y el puerto del servidor.")
        }
        val baseUrl = try {
            config.baseUrl(host, puerto)
        } catch (e: IllegalArgumentException) {
            return AppResult.Error(e.message ?: "Datos de conexión inválidos.")
        }
        val servidor = "${config.limpiarHost(host)}:${puerto.trim()}"
        return try {
            val response: HttpResponse = client.post(baseUrl + "login") {
                // El login no debe hacer esperar tanto como el sync completo.
                timeout { requestTimeoutMillis = 8_000 }
                contentType(ContentType.Application.Json)
                setBody(LoginRequestDto(usuario = usuario, password = password))
            }
            if (response.status == HttpStatusCode.OK) {
                response.body<LoginResponseDto>().toDomain()?.let { AppResult.Success(it) }
                    ?: AppResult.Error("El usuario no tiene un rol válido asignado en el servidor.")
            } else {
                val error = runCatching { response.body<ErrorResponseDto>() }.getOrNull()
                AppResult.Error(error?.message ?: "Usuario o contraseña incorrectos.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            AppResult.Error(
                "El servidor $servidor no respondió a tiempo. Verifique que esté encendido y en la misma red.",
                sinConexion = true,
            )
        } catch (e: Exception) {
            val motivo = e.message?.takeIf { it.isNotBlank() }
            AppResult.Error(
                buildString {
                    append("No se pudo conectar con $servidor. ")
                    append("Verifique la IP, el puerto y que el dispositivo esté en la misma red.")
                    if (motivo != null) append(" ($motivo)")
                },
                sinConexion = true,
            )
        }
    }
}
