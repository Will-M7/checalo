package com.example.ecolacteoshuata.data.remote.dto

import kotlinx.serialization.Serializable

// Contrato JSON de `POST /api/mobile/login` (MobileAuthController de Laravel).

@Serializable
data class LoginRequestDto(val usuario: String, val password: String)

@Serializable
data class LoginResponseDto(
    val id: String,
    val usuario: String,
    val nombres: String,
    val apellidos: String,
    val documento: String = "",
    val celular: String = "",
    val rol: String? = null,
    val activo: Boolean = true,
    val toma_asistencia: Boolean = false,
    /** Token del dispositivo: se guarda en TokenStorage (Keystore / Keychain), nunca en SQLite. */
    val token: String? = null,
    val autorizacion: com.example.ecolacteoshuata.domain.model.AutorizacionUsuario? = null,
    val roles: List<String> = emptyList(),
)

@Serializable
data class ErrorResponseDto(val message: String? = null)
