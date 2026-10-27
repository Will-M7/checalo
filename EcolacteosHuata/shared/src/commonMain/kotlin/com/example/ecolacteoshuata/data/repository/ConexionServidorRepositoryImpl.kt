package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.ConexionServidor
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val ID_UNICO = 1L

/**
 * Host, puerto y estado de la sincronización viven en SQLite; el token, en [TokenStorage]
 * (Keystore en Android, Keychain en iOS), nunca en texto plano.
 */
class ConexionServidorRepositoryImpl(
    private val db: AppDatabase,
    private val tokenStorage: TokenStorage,
) : ConexionServidorRepository {
    private val queries = db.conexionServidorQueries

    /** El token en memoria: se carga del almacén seguro la primera vez que se necesita. */
    private val token = MutableStateFlow<String?>(null)
    private var tokenCargado = false
    private val cargando = Mutex()

    private suspend fun tokenSeguro(): String? = cargando.withLock {
        if (!tokenCargado) {
            // Versiones anteriores guardaban el token en SQLite: se pasa al almacén seguro una vez.
            val enBase = queries.selectPrimera().executeAsOneOrNull()
            val antiguo = enBase?.token
            if (enBase != null && antiguo != null) {
                tokenStorage.save(antiguo)
                queries.guardarToken(null, enBase.id)
            }
            token.value = tokenStorage.read()
            tokenCargado = true
        }
        token.value
    }

    private fun conToken(conexion: ConexionServidor) = conexion.copy(token = token.value)

    override fun observe(): Flow<ConexionServidor> = combine(
        queries.selectPrimera().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toDomain() ?: ConexionServidor() },
        flow {
            tokenSeguro()
            emitAll(token)
        },
    ) { conexion, tokenActual -> conexion.copy(token = tokenActual) }

    override suspend fun get(): ConexionServidor {
        tokenSeguro()
        return conToken(queries.selectPrimera().executeAsOneOrNull()?.toDomain() ?: ConexionServidor())
    }

    override suspend fun guardar(conexion: ConexionServidor) {
        val existente = queries.selectPrimera().executeAsOneOrNull()
        if (existente == null) {
            queries.insert(
                id = ID_UNICO,
                host = conexion.host,
                puerto = conexion.puerto,
                activa = if (conexion.activa) 1L else 0L,
                ultima_sincronizacion = conexion.ultimaSincronizacion,
            )
        } else {
            queries.update(
                host = conexion.host,
                puerto = conexion.puerto,
                activa = if (conexion.activa) 1L else 0L,
                id = existente.id,
            )
            // Otro servidor = otros datos: la fecha de la última sincronización del servidor
            // anterior haría que solo se pidan "cambios desde entonces" y faltaría información.
            // El token también es de ese servidor: hay que volver a iniciar sesión en el nuevo.
            if (existente.host.trim() != conexion.host.trim() || existente.puerto.trim() != conexion.puerto.trim()) {
                queries.reiniciarSincronizacion(existente.id)
                guardarToken(null)
            }
        }
    }

    override suspend fun reiniciarSincronizacion() {
        val existente = queries.selectPrimera().executeAsOneOrNull() ?: return
        queries.reiniciarSincronizacion(existente.id)
    }

    override suspend fun guardarToken(token: String?) {
        tokenSeguro()
        if (token == null) tokenStorage.clear() else tokenStorage.save(token)
        this.token.value = token
    }

    override suspend fun actualizarUltimaSincronizacion(fecha: String) {
        val existente = queries.selectPrimera().executeAsOneOrNull() ?: return
        queries.actualizarUltimaSincronizacion(fecha, existente.id)
    }
}
