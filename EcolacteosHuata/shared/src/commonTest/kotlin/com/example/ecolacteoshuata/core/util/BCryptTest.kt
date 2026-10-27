package com.example.ecolacteoshuata.core.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hashes generados con `password_hash()` de PHP, igual que `Hash::make` de Laravel. */
class BCryptTest {
    private val hashCosto4 = "\$2y\$04\$8vHmZZggOrEm4VRJp6o1Ue2/C2D/hNzYIYSJHgRmNBmmAIWnEQitm"
    private val hashCosto10 = "\$2y\$10\$O0yyC91CfDYnskIPBfYqWufg2wyJRWIrJYa1YLzPMAvFQv.uDSz0W"

    @Test
    fun verificaUnHashDeLaravel() {
        assertTrue(BCrypt.verificar("1234", hashCosto4))
        assertTrue(BCrypt.verificar("clave Ñandú 2026", hashCosto10))
    }

    @Test
    fun rechazaUnaContrasenaIncorrecta() {
        assertFalse(BCrypt.verificar("12345", hashCosto4))
        assertFalse(BCrypt.verificar("clave nandu 2026", hashCosto10))
    }

    @Test
    fun generaUnHashConElFormatoDeLaravel() {
        val hash = BCrypt.generar("clave Ñandú 2026", costo = 4)
        assertTrue(hash.startsWith("\$2y\$04\$"))
        assertTrue(BCrypt.esHash(hash))
        assertTrue(BCrypt.verificar("clave Ñandú 2026", hash))
        assertFalse(BCrypt.verificar("clave nandu 2026", hash))
    }

    @Test
    fun passwordHasherAceptaHashLocalYBcrypt() {
        assertTrue(PasswordHasher.verify("1234", hashCosto4))
        assertTrue(PasswordHasher.verify("1234", PasswordHasher.hash("1234")))
        assertFalse(PasswordHasher.verify("1234", "no-es-un-hash"))
    }
}
