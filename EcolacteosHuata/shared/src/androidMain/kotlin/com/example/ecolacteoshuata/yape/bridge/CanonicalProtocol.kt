package com.example.ecolacteoshuata.yape.bridge

import com.example.ecolacteoshuata.yape.model.CapturedNotification
import com.example.ecolacteoshuata.yape.store.BridgeOutboxEvent
import java.security.MessageDigest
import java.time.Instant

/** Pure contract utilities shared by capture, persistence and transport tests. */
object CanonicalProtocol {
    const val EXPECTED_PACKAGE="com.bcp.innovacxion.yapeapp"
    fun capture(packageName:String,texts:List<String?>)=packageName==EXPECTED_PACKAGE && texts.any { !it.isNullOrBlank() }
    fun incoming(packageName:String,texts:List<String?>)=packageName==EXPECTED_PACKAGE &&
        texts.filterNotNull().any { Regex("Yape!\\s+.+?\\s+te envi[oó] un pago por\\s+S/",RegexOption.IGNORE_CASE).containsMatchIn(it) }
    fun eventId(receiver:String,n:CapturedNotification):String = MessageDigest.getInstance("SHA-256")
        .digest("$receiver|${n.packageName}|${n.notificationKey}|${n.postedAt}".toByteArray()).joinToString("") { "%02x".format(it) }
    fun json(e:BridgeOutboxEvent):String {
        val q=RawNotificationJson::quote
        val fields=linkedMapOf("bridgeEventId" to e.eventId,"receiverId" to e.receiverId,"packageName" to e.packageName,
            "postedAt" to Instant.ofEpochMilli(e.postedAt).toString(),"title" to e.title,"text" to e.text,
            "bigText" to e.bigText,"subText" to e.subText,"summaryText" to e.summaryText,"securityCode" to e.securityCode)
        return fields.entries.joinToString(",","{","}") { q(it.key)+":"+(it.value?.let(q) ?: "null") }
    }
    /** HTTP se habilita explícitamente solo en el build de pruebas; release conserva HTTPS. */
    fun secureUrl(raw: String, allowHttp: Boolean = false): String? {
        val value = BridgeConfig.normalizeBaseUrl(raw) ?: return null
        val uri = java.net.URI(value)
        return value.takeIf { uri.scheme == "https" || (allowHttp && uri.scheme == "http") }
    }
}
