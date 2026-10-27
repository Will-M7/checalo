package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.UsuarioRemoto

/**
 * Valida credenciales contra el servidor Laravel (MySQL) de la asociación por la IP / host y
 * puerto que se configuran junto al login, para las instalaciones que ya tienen ese servidor
 * corriendo en la red local.
 */
interface AuthRemoteDataSource {
    suspend fun login(host: String, puerto: String, usuario: String, password: String): AppResult<UsuarioRemoto>
}
