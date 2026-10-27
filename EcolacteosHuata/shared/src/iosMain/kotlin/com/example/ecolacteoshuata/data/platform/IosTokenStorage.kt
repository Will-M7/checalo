package com.example.ecolacteoshuata.data.platform

import com.example.ecolacteoshuata.domain.platform.TokenStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

private const val SERVICIO = "com.example.ecolacteoshuata"
private const val CUENTA = "token_api_laravel"

/**
 * Token de Laravel en el Keychain de iOS (kSecClassGenericPassword), accesible solo en este
 * dispositivo: no se copia a otros equipos ni a respaldos de iCloud.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosTokenStorage : TokenStorage {

    /** Arma la consulta del Keychain; [extra] agrega pares propios de cada operación. */
    private fun <T> conConsulta(extra: List<Pair<CFTypeRef?, CFTypeRef?>>, bloque: (CFMutableDictionaryRef?) -> T): T {
        val servicio = CFBridgingRetain(NSString.create(string = SERVICIO))
        val cuenta = CFBridgingRetain(NSString.create(string = CUENTA))
        val consulta = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(consulta, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(consulta, kSecAttrService, servicio)
        CFDictionaryAddValue(consulta, kSecAttrAccount, cuenta)
        extra.forEach { (clave, valor) -> CFDictionaryAddValue(consulta, clave, valor) }
        return try {
            bloque(consulta)
        } finally {
            CFRelease(consulta)
            CFRelease(servicio)
            CFRelease(cuenta)
        }
    }

    override suspend fun save(token: String) {
        clear()
        val bytes = token.encodeToByteArray()
        val datos = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val valor = CFBridgingRetain(datos)
        try {
            conConsulta(
                listOf(
                    kSecValueData to valor,
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                ),
            ) { SecItemAdd(it, null) }
        } finally {
            CFRelease(valor)
        }
    }

    override suspend fun read(): String? = conConsulta(
        listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne),
    ) { consulta ->
        memScoped {
            val resultado = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(consulta, resultado.ptr) != errSecSuccess) return@memScoped null
            val datos = CFBridgingRelease(resultado.value) as? NSData ?: return@memScoped null
            val bytes = ByteArray(datos.length.toInt())
            if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), datos.bytes, datos.length) }
            bytes.decodeToString()
        }
    }

    override suspend fun clear() {
        conConsulta(emptyList()) { SecItemDelete(it) }
    }
}
