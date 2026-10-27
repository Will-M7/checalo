package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ecolacteoshuata.core.util.BCrypt
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.PasswordHasher
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class UsuarioRepositoryImpl(private val db: AppDatabase, private val driver: app.cash.sqldelight.db.SqlDriver) : UsuarioRepository {
    private val queries = db.usuarioQueries
    override suspend fun guardarAutorizacion(a: com.example.ecolacteoshuata.domain.model.AutorizacionUsuario) { AutorizacionLocal.guardar(db, a) }
    override suspend fun guardarDesdeServidor(usuario: Usuario, password: String) {
        val actual = getById(usuario.id) ?: getByUsuario(usuario.usuario)
        if (actual != null) {
            val pendiente = db.cambioPendienteQueries.selectTodos().executeAsList().firstOrNull { it.tabla == "usuario" && it.registro_id == actual.id }
            if (pendiente != null) {
                val datos = mapOf("nombres" to actual.nombres, "apellidos" to actual.apellidos, "documento" to actual.documento,
                    "celular" to actual.celular, "usuario" to actual.usuario, "rol" to actual.rol?.name.orEmpty(),
                    "roles" to Rol.codificarLista(actual.rolesAdicionales), "password_hash" to actual.passwordHash, "activo" to if (actual.activo) "1" else "0")
                db.autorizacionLocalQueries.revisar(pendiente.actor_id ?: actual.id, actual.autorizacion?.version,
                    "usuario", actual.id, pendiente.operacion,
                    kotlinx.serialization.json.Json.encodeToString(com.example.ecolacteoshuata.data.remote.dto.CambioDto("usuario", actual.id, pendiente.operacion, datos)),
                    "Perfil local conservado antes de actualizar el inicio de sesión.", nowLocalDateTime().toString())
            }
        }
        val hash = if (password.isNotBlank()) cifrar(password) else actual?.passwordHash ?: usuario.passwordHash.takeIf { it.isNotBlank() } ?: cifrar(IdGenerator.newId())
        db.transaction {
            db.cambioPendienteQueries.marcarAplicando(1)
            if (actual != null && actual.id != usuario.id) {
                if (actual.id.any { !it.isDigit() } && actual.documento == usuario.documento) {
                    com.example.ecolacteoshuata.data.sync.migrarIdentidadUsuario(driver, actual.id, usuario.id)
                } else {
                    // Un nombre reutilizado no prueba identidad: conserva la cuenta anterior y sus referencias.
                    driver.execute(null, "UPDATE usuario SET usuario = ?, activo = 0 WHERE id = ?", 2) {
                        bindString(0, "${actual.usuario}#anterior#${actual.id}"); bindString(1, actual.id)
                    }
                }
            }
            if (queries.selectById(usuario.id).executeAsOneOrNull() == null) {
                queries.insert(usuario.id, usuario.nombres, usuario.apellidos, usuario.documento, usuario.celular,
                    usuario.rol?.name.orEmpty(), usuario.usuario, hash, if (usuario.activo) 1L else 0L, usuario.creadoEn.toString())
            } else {
                queries.update(usuario.nombres, usuario.apellidos, usuario.documento, usuario.celular,
                    usuario.rol?.name.orEmpty(), usuario.usuario, hash, if (usuario.activo) 1L else 0L, usuario.id)
            }
            queries.actualizarRoles(usuario.rol?.name.orEmpty(), Rol.codificarLista(usuario.rolesAdicionales), usuario.id)
            val toma = if (usuario.tomaAsistencia) 1L else 0L
            queries.actualizarTomaAsistencia(toma, usuario.id, toma)
            db.cambioPendienteQueries.marcarAplicando(0)
        }
    }

    override fun observeAll(): Flow<List<Usuario>> = combine(
        queries.selectAll().asFlow().mapToList(Dispatchers.Default),
        db.autorizacionLocalQueries.selectTodas().asFlow().mapToList(Dispatchers.Default),
    ) { list, _ -> list.map { it.toDomain().let { u -> u.copy(autorizacion = AutorizacionLocal.leer(db, u.id)) } } }

    override suspend fun getAll(): List<Usuario> = queries.selectAll().executeAsList().map { it.toDomain().let { u -> u.copy(autorizacion = AutorizacionLocal.leer(db, u.id)) } }

    override suspend fun getById(id: String): Usuario? = queries.selectById(id).executeAsOneOrNull()?.toDomain()?.let { it.copy(autorizacion = AutorizacionLocal.leer(db, it.id)) }

    override suspend fun getByUsuario(usuario: String): Usuario? =
        queries.selectByUsuario(usuario).executeAsOneOrNull()?.toDomain()?.let { it.copy(autorizacion = AutorizacionLocal.leer(db, it.id)) }

    // Como `User::conAlgunRol` de Laravel: cuenta el rol principal y los adicionales.
    override suspend fun getByRol(rol: Rol): List<Usuario> =
        queries.selectAll().executeAsList().map { it.toDomain().let { u -> u.copy(autorizacion = AutorizacionLocal.leer(db, u.id)) } }.filter { it.tieneRol(rol) }

    /**
     * bcrypt como Laravel: así el usuario creado en el teléfono se sube al servidor y entra con
     * la misma contraseña en la web. Tarda ~1 s en un teléfono: fuera del hilo de la interfaz.
     */
    private suspend fun cifrar(passwordPlano: String): String =
        withContext(Dispatchers.Default) { BCrypt.generar(passwordPlano) }

    override suspend fun crear(usuario: Usuario, passwordPlano: String) {
        val id = usuario.id.ifBlank { IdGenerator.newId() }
        val hash = cifrar(passwordPlano)
        queries.insert(
            id = id,
            nombres = usuario.nombres,
            apellidos = usuario.apellidos,
            documento = usuario.documento,
            celular = usuario.celular,
            rol = usuario.rol?.name.orEmpty(),
            usuario = usuario.usuario,
            password_hash = hash,
            activo = if (usuario.activo) 1L else 0L,
            creado_en = nowLocalDateTime().toString(),
        )
        queries.actualizarRoles(usuario.rol?.name.orEmpty(), Rol.codificarLista(usuario.rolesAdicionales), id)
    }

    override suspend fun actualizar(usuario: Usuario, passwordPlano: String?) {
        val hash = passwordPlano?.let { cifrar(it) } ?: usuario.passwordHash
        queries.update(
            nombres = usuario.nombres,
            apellidos = usuario.apellidos,
            documento = usuario.documento,
            celular = usuario.celular,
            rol = usuario.rol?.name.orEmpty(),
            usuario = usuario.usuario,
            password_hash = hash,
            activo = if (usuario.activo) 1L else 0L,
            id = usuario.id,
        )
        queries.actualizarRoles(usuario.rol?.name.orEmpty(), Rol.codificarLista(usuario.rolesAdicionales), usuario.id)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }

    override fun observeById(id: String): Flow<Usuario?> = combine(
        queries.selectById(id).asFlow().mapToOneOrNull(Dispatchers.Default),
        db.autorizacionLocalQueries.selectUsuario(id).asFlow().mapToOneOrNull(Dispatchers.Default),
    ) { row, _ -> row?.toDomain()?.let { it.copy(autorizacion = AutorizacionLocal.leer(db, id)) } }

    override suspend fun asignarTomaAsistencia(id: String, tomaAsistencia: Boolean) {
        val valor = if (tomaAsistencia) 1L else 0L
        queries.actualizarTomaAsistencia(valor, id, valor)
    }

    override suspend fun olvidarCambiosPendientes(id: String) {
        db.cambioPendienteQueries.olvidar("usuario", id)
    }

    override suspend fun countActivos(): Long = queries.countActivos().executeAsOne()

    override suspend fun countAll(): Long = queries.countAll().executeAsOne()

    override suspend fun verificarCredenciales(usuario: String, passwordPlano: String): Usuario? {
        val encontrado = getByUsuario(usuario) ?: return null
        if (!encontrado.activo) return null
        // El hash bcrypt de Laravel tarda ~1 s en un teléfono: fuera del hilo de la interfaz.
        val valida = withContext(Dispatchers.Default) { PasswordHasher.verify(passwordPlano, encontrado.passwordHash) }
        if (!valida) return null
        // Usuario creado en el teléfono antes de usar bcrypt: su hash viejo no sirve en Laravel.
        // Se aprovecha que se conoce la contraseña para pasarlo a bcrypt y poder subirlo.
        if (!BCrypt.esHash(encontrado.passwordHash)) {
            val hash = cifrar(passwordPlano)
            queries.actualizarPassword(hash, encontrado.id)
            return encontrado.copy(passwordHash = hash)
        }
        return encontrado
    }
}
