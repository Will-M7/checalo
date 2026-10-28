package com.example.ecolacteoshuata.yape.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-scoped secret and outbox encryption. No secrets in APK, logs, or plain preferences. */
object DeviceCredentials {
    private lateinit var context: Context
    fun init(value: Context) { context=value.applicationContext }
    private fun prefs()=context.getSharedPreferences("huata_yape_device",Context.MODE_PRIVATE)
    @Volatile var binding: String = ""
    val receiverId: String get() = prefs().getString("receiver_"+binding, "") ?: ""
    fun receiver(value: String) { prefs().edit().putString("receiver_"+binding, value).commit() }
    val installation: String get() = synchronized(this) {
        prefs().getString("installation", null) ?: java.security.SecureRandom().let { random ->
            ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }.also {
                check(prefs().edit().putString("installation", it).commit())
            }
        }
    }
    fun enabled(scope: String): Boolean = prefs().getBoolean("enabled_"+scope, false)
    fun enabled(scope: String, value: Boolean) { check(prefs().edit().putBoolean("enabled_"+scope, value).commit()) }
    fun cached(scope: String): String? = runCatching { prefs().getString("state_"+scope, null)?.let(::open) }.getOrNull()
    fun cache(scope: String, value: String) { check(prefs().edit().putString("state_"+scope, seal(value)).commit()) }
    @Synchronized private fun secret():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("huata-yape-outbox-v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("huata-yape-outbox-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun seal(value:String):String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,secret()) }
        return Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    fun open(value:String):String {
        val bytes=Base64.decode(value,Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }
}
