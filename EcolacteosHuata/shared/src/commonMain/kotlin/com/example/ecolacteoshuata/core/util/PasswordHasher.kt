package com.example.ecolacteoshuata.core.util

object PasswordHasher {
    private const val SALT = "ecolacteoshuata-huata-puno-salt"

    fun hash(password: String): String = sha256((SALT + password).encodeToByteArray()).toHex()

    /** Acepta tanto el hash local como el bcrypt que llega de Laravel en la sincronización. */
    fun verify(password: String, hash: String): Boolean =
        if (BCrypt.esHash(hash)) BCrypt.verificar(password, hash) else hash(password) == hash
}
