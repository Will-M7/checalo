package com.example.ecolacteoshuata.data.repository

import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.AutorizacionUsuario
import kotlinx.serialization.json.Json

object AutorizacionLocal {
    private val json = Json { ignoreUnknownKeys = true }
    fun leer(db: AppDatabase, id: String): AutorizacionUsuario? =
        db.autorizacionLocalQueries.selectUsuario(id).executeAsOneOrNull()?.let { json.decodeFromString<AutorizacionUsuario>(it.documento) }
    fun guardar(db: AppDatabase, a: AutorizacionUsuario) {
        db.transaction {
            db.autorizacionLocalQueries.guardar(a.usuario_id, a.version, json.encodeToString(a))
            db.autorizacionLocalQueries.borrarPantallas(a.usuario_id)
            a.pantallas.forEach { db.autorizacionLocalQueries.asignarPantalla(a.usuario_id, it) }
        }
    }
}
