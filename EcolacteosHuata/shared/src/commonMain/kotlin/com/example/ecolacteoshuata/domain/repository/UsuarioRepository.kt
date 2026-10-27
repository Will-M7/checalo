package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.Usuario
import kotlinx.coroutines.flow.Flow

interface UsuarioRepository {
    suspend fun guardarAutorizacion(a: com.example.ecolacteoshuata.domain.model.AutorizacionUsuario) {}
    suspend fun guardarDesdeServidor(usuario: Usuario, password: String) {
        if (getByUsuario(usuario.usuario) == null) crear(usuario, password) else actualizar(usuario, password)
    }
    fun observeAll(): Flow<List<Usuario>>
    suspend fun getAll(): List<Usuario>
    suspend fun getById(id: String): Usuario?
    suspend fun getByUsuario(usuario: String): Usuario?
    suspend fun getByRol(rol: Rol): List<Usuario>
    suspend fun crear(usuario: Usuario, passwordPlano: String)
    suspend fun actualizar(usuario: Usuario, passwordPlano: String?)
    suspend fun eliminar(id: String)
    suspend fun countActivos(): Long
    suspend fun countAll(): Long
    suspend fun verificarCredenciales(usuario: String, passwordPlano: String): Usuario?

    /** El usuario tal como está ahora en el teléfono (cambia si el sync trae otro rol o permiso). */
    fun observeById(id: String): Flow<Usuario?>

    /** "Tomar asistencia" asignado o quitado por el administrador. */
    suspend fun asignarTomaAsistencia(id: String, tomaAsistencia: Boolean)

    /** Lo guardado en este usuario vino del servidor (login con conexión): no hay nada que subir. */
    suspend fun olvidarCambiosPendientes(id: String) {}
}
