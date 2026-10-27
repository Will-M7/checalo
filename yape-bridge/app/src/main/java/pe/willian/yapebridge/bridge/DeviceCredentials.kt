package pe.willian.yapebridge.bridge

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
    private fun prefs()=context.getSharedPreferences("canonical_device",Context.MODE_PRIVATE)
    val deviceId:String get()=prefs().getString("device","") ?: ""
    val mode:String get()=prefs().getString("mode","direct") ?: "direct"
    fun metadata(device:String,mode:String) { require(mode in listOf("personal","shared","direct"));prefs().edit().putString("device",device).putString("mode",mode).commit() }
    val receiverId: String get()=prefs().getString("receiver","") ?: ""
    val key: String get()=runCatching { prefs().getString("credential",null)?.let { open(it) } ?: "" }.getOrDefault("")
    fun save(receiver:String,token:String):Boolean {
        if(!receiver.matches(Regex("[a-zA-Z0-9_-]{1,100}")) || token.length<32) return false
        prefs().edit().putString("receiver",receiver).putString("credential",seal(token)).apply()
        return true
    }
    @Synchronized private fun secret():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("canonical-bridge",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("canonical-bridge",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
