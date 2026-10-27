package com.example.ecolacteoshuata.data.remote

import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.*

/** Uses the existing authenticated client; never exports its bearer to another application. */
class BridgeSessionAssertion(private val client:HttpClient,private val config:ApiConfig,private val connection:ConexionServidorRepository,private val db:AppDatabase) {
    suspend fun proof(device:String,nonce:String,event:String):String {
        val id=db.autorizacionLocalQueries.sesionActual().executeAsOneOrNull() ?: error("No local session")
        val user=db.usuarioQueries.selectById(id).executeAsOneOrNull() ?: error("No local user")
        check(user.activo==1L)
        val c=connection.get();val url=config.baseUrl(c.host,c.puerto)
        val response=client.post(url+"bridge/assertion") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("deviceId",device);put("nonce",nonce);put("eventId",event);put("sessionUsername",user.usuario) })
        }
        check(response.status.isSuccess())
        val proof=response.body<JsonObject>()
        return buildJsonObject { put("proof",proof.getValue("proof"));put("nonce",nonce);put("backendUrl",url.removeSuffix("api/mobile/")) }.toString()
    }
}
