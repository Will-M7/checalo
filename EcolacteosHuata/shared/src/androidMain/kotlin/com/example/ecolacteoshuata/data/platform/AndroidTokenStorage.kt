package com.example.ecolacteoshuata.data.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val KEYSTORE = "AndroidKeyStore"
private const val ALIAS = "ecolacteos_token_api"
private const val PREFERENCIAS = "ecolacteos_seguro"
private const val CLAVE_TOKEN = "token_cifrado"
private const val TRANSFORMACION = "AES/GCM/NoPadding"

/**
 * Token de Laravel cifrado con una clave AES que se genera dentro del Android Keystore (no sale
 * del dispositivo). En las preferencias solo queda el texto cifrado, nunca el token en claro.
 */
class AndroidTokenStorage(context: Context) : TokenStorage {
    private val preferencias = context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

    private fun clave(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    override suspend fun save(token: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave()) }
        val cifrado = cipher.doFinal(token.encodeToByteArray())
        // IV (12 bytes) + texto cifrado, en Base64.
        val guardado = Base64.encodeToString(cipher.iv + cifrado, Base64.NO_WRAP)
        preferencias.edit().putString(CLAVE_TOKEN, guardado).apply()
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val guardado = preferencias.getString(CLAVE_TOKEN, null) ?: return@withContext null
        try {
            val bytes = Base64.decode(guardado, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMACION)
            cipher.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, bytes, 0, 12))
            cipher.doFinal(bytes, 12, bytes.size - 12).decodeToString()
        } catch (e: Exception) {
            // Clave borrada o datos dañados (p. ej. restauración de respaldo): hay que volver a iniciar sesión.
            preferencias.edit().remove(CLAVE_TOKEN).apply()
            null
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferencias.edit().remove(CLAVE_TOKEN).apply()
    }
}
