package com.example.ecolacteoshuata.domain.platform

/**
 * Dónde se guarda el token que Laravel entrega al iniciar sesión con conexión. Nunca en texto
 * plano: Android lo cifra con una clave del Keystore y iOS lo guarda en el Keychain. Cada
 * plataforma aporta su implementación en `platformModule` (Koin).
 */
interface TokenStorage {
    suspend fun save(token: String)
    suspend fun read(): String?
    suspend fun clear()
}
