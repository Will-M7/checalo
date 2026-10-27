package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.data.remote.datasource.AdministracionRolesRemota
import com.example.ecolacteoshuata.data.repository.AutorizacionLocal
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.repository.SessionRepository

/** Transporte y caché; la validación de privilegios pertenece únicamente a Laravel. */
class AdministrarRolesUseCase(
    private val conexion: ConexionServidorRepository,
    private val api: AdministracionRolesRemota,
    private val db: AppDatabase,
    private val sesion: SessionRepository,
) {
    private suspend fun <T> conectado(accion: suspend (String, String) -> T): AppResult<T> {
        val c = conexion.get()
        if (!c.activa || c.token.isNullOrBlank()) return AppResult.Error("Conecta con Laravel para administrar roles y asignaciones.")
        return try { AppResult.Success(accion(c.host, c.puerto)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { AppResult.Error(e.message ?: "No se pudo consultar la autorización central.") }
    }

    private fun guardarSnapshot(a: AutorizacionUsuario) {
        AutorizacionLocal.guardar(db, a)
        sesion.usuarioActual.value?.takeIf { it.id == a.usuario_id }?.let { sesion.setUsuario(it.copy(autorizacion = a)) }
    }

    suspend fun cargar(): AppResult<CatalogoAdministrativo> = conectado { h, p -> api.catalogoRoles(h, p).also { guardarSnapshot(it.autorizacion) } }
    suspend fun guardar(id: Long?, datos: GuardarRolCentral): AppResult<CatalogoAdministrativo> = conectado { h, p -> api.guardarRol(h, p, id, datos).also { guardarSnapshot(it.autorizacion) } }
    suspend fun asignables(): AppResult<List<RolAsignado>> = conectado { h, p -> api.autorizaciones(h, p).also(::guardarSnapshot).catalogo_roles }
    suspend fun guardarUsuario(id: String?, datos: GuardarUsuarioCentral): AppResult<UsuarioAdministrado> = conectado { h, p -> api.guardarUsuario(h, p, id, datos).also { guardarSnapshot(it.autorizacion) } }
    suspend fun distribuciones(): AppResult<CatalogoDistribuciones> = conectado { h, p -> api.distribuciones(h, p).also { guardarSnapshot(it.autorizacion) } }
    suspend fun guardarDistribucion(datos: GuardarDistribucion): AppResult<CatalogoDistribuciones> = conectado { h, p -> api.guardarDistribucion(h, p, datos).also { guardarSnapshot(it.autorizacion) } }
    suspend fun prepararDistribucion(datos: PrepararDistribucion): AppResult<VistaDistribucion> = conectado { h, p -> api.prepararDistribucion(h, p, datos) }
    suspend fun confirmarDistribucion(datos: ConfirmarDistribucion): AppResult<CatalogoDistribuciones> = conectado { h, p -> api.confirmarDistribucion(h, p, datos).also { guardarSnapshot(it.autorizacion) } }
}
