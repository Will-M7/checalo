package com.example.ecolacteoshuata.data.remote

import com.example.ecolacteoshuata.domain.platform.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Un único HttpClient para toda la app (login, sync y subida de cambios). El motor lo aporta
 * cada plataforma (OkHttp en Android, Darwin en iOS) desde `platformModule`.
 */
object HttpClientFactory {

    fun create(engine: HttpClientEngine, config: ApiConfig, tokenStorage: TokenStorage): HttpClient =
        HttpClient(engine) {
            // Las fuentes remotas revisan el código de estado (401 = token rechazado, 422 = datos):
            // no se lanzan excepciones automáticas por 4xx/5xx.
            expectSuccess = false

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true // Laravel puede mandar campos que la app aún no usa
                        isLenient = true
                        explicitNulls = false
                    },
                )
            }

            // Logcat (Android) y consola de Xcode (iOS), sin mostrar nunca el token.
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("KtorClient => $message")
                    }
                }
                level = LogLevel.INFO
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }

            install(HttpTimeout) {
                connectTimeoutMillis = config.conectarTimeoutMs
                requestTimeoutMillis = config.solicitudTimeoutMs
                socketTimeoutMillis = config.solicitudTimeoutMs
            }

            // Laravel entrega un token por dispositivo (sin endpoint de refresh): se envía en cada
            // solicitud a la API salvo en el login. Ante un 401 se relee el almacén seguro: si se
            // inició sesión de nuevo hay otro token y se reintenta con él; si es el mismo (o ya no
            // hay), queda el 401 y el sync pide volver a iniciar sesión con conexión.
            install(Auth) {
                bearer {
                    loadTokens { tokenStorage.read()?.let { BearerTokens(it, "") } }
                    refreshTokens {
                        tokenStorage.read()
                            ?.takeIf { it != oldTokens?.accessToken }
                            ?.let { BearerTokens(it, "") }
                    }
                    sendWithoutRequest { request -> !request.url.encodedPath.endsWith("/login") }
                }
            }

            defaultRequest {
                headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
}
