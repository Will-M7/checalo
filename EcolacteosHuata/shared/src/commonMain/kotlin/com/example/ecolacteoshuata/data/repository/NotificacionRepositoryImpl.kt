package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.NotificacionUsuario
import com.example.ecolacteoshuata.domain.repository.NotificacionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Las notificaciones las trae el sync; "leída" se marca aquí y se sube a Laravel con la cola. */
class NotificacionRepositoryImpl(private val db: AppDatabase) : NotificacionRepository {
    private val queries = db.notificacionQueries

    override fun observeTodas(): Flow<List<NotificacionUsuario>> =
        queries.selectTodas().asFlow().mapToList(Dispatchers.Default).map { lista ->
            lista.map { NotificacionUsuario(it.id, it.tipo, it.titulo, it.mensaje, it.leida == 1L, it.creada_en) }
        }

    override fun observeNoLeidas(): Flow<Long> = queries.contarNoLeidas().asFlow().mapToOne(Dispatchers.Default)

    override suspend fun marcarLeida(id: String) {
        db.transaction {
            queries.marcarLeida(id)
            db.cambioPendienteQueries.anotar("notificacion", id, "guardar")
        }
    }

    override suspend fun marcarTodasLeidas() {
        db.transaction {
            queries.selectNoLeidasIds().executeAsList().forEach { id ->
                queries.marcarLeida(id)
                db.cambioPendienteQueries.anotar("notificacion", id, "guardar")
            }
        }
    }
}
