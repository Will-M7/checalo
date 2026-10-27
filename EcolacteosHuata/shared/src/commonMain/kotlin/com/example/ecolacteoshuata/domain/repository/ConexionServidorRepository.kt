package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.ConexionServidor
import kotlinx.coroutines.flow.Flow

interface ConexionServidorRepository {
    fun observe(): Flow<ConexionServidor>
    suspend fun get(): ConexionServidor
    suspend fun guardar(conexion: ConexionServidor)
    suspend fun actualizarUltimaSincronizacion(fecha: String)

    /** Olvida la última sincronización: la próxima trae todo el sistema otra vez. */
    suspend fun reiniciarSincronizacion()

    /** Guarda (o borra, con null) el token que Laravel entregó al iniciar sesión. */
    suspend fun guardarToken(token: String?)
}
