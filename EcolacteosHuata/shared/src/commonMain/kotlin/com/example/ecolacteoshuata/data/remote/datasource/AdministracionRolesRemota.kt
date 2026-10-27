package com.example.ecolacteoshuata.data.remote.datasource

import com.example.ecolacteoshuata.domain.model.*

/** Exclusivamente API administrativa autenticada; nunca se envía mediante la cola genérica. */
interface AdministracionRolesRemota {
    suspend fun autorizaciones(host: String, puerto: String): AutorizacionUsuario
    suspend fun asignarRoles(host: String, puerto: String, id: String, roles: Set<String>): AutorizacionUsuario
    suspend fun catalogoRoles(host: String, puerto: String): CatalogoAdministrativo
    suspend fun guardarRol(host: String, puerto: String, id: Long?, datos: GuardarRolCentral): CatalogoAdministrativo
    suspend fun guardarUsuario(host: String, puerto: String, id: String?, datos: GuardarUsuarioCentral): UsuarioAdministrado
    suspend fun distribuciones(host: String, puerto: String): CatalogoDistribuciones
    suspend fun guardarDistribucion(host: String, puerto: String, datos: GuardarDistribucion): CatalogoDistribuciones
    suspend fun prepararDistribucion(host: String, puerto: String, datos: PrepararDistribucion): VistaDistribucion
    suspend fun confirmarDistribucion(host: String, puerto: String, datos: ConfirmarDistribucion): CatalogoDistribuciones
}
