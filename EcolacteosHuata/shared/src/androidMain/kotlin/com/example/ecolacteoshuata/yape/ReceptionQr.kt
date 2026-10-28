package com.example.ecolacteoshuata.yape

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.Url

/** Usa el servidor configurado del teléfono, incluso cuando APP_URL tiene otro host público. */
internal suspend fun loadReceptionQr(client: HttpClient, base: String, reference: String, token: String): ByteArray {
    val url = if (reference.startsWith("/")) base.trimEnd('/') + reference else reference
    val target = Url(url); val origin = Url(base)
    check(target.protocol == origin.protocol && target.host == origin.host && target.port == origin.port)
    check(target.user.isNullOrEmpty() && target.password.isNullOrEmpty())
    val response = client.get(url) { bearerAuth(token) }
    check(response.status.value == 200)
    check((response.headers["Content-Length"]?.toLongOrNull() ?: 0) <= 2 * 1024 * 1024)
    val bytes = response.readRawBytes()
    check(bytes.size <= 2 * 1024 * 1024)
    return bytes
}
