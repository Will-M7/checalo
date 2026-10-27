package pe.willian.yapebridge.bridge

import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.model.YapeSecurityCodeExtractor
import pe.willian.yapebridge.store.BridgeOutboxEvent

/**
 * Construcción del JSON enviado al backend.
 *
 * Kotlin puro (sin dependencias de Android) para poder probarse con
 * `./gradlew test` sin Robolectric.
 *
 * Privacidad (Fase 2):
 * - Solo los 7 campos permitidos: packageName, postedAt, title, text,
 *   bigText, subText, summaryText.
 * - NUNCA se envían extras completos, imágenes, iconos ni notificationKey.
 * - No se acumula histórico: cada envío es un payload aislado.
 */
object RawNotificationJson {

    /** Construye el payload de una notificación capturada. */
    fun of(notification: CapturedNotification): String = build(
        packageName = notification.packageName,
        postedAt = notification.postedAt,
        title = notification.title,
        text = notification.text,
        bigText = notification.bigText,
        subText = notification.subText,
        summaryText = notification.summaryText,
        securityCode = notification.securityCode ?: YapeSecurityCodeExtractor.extract(notification),
    )

    fun of(event: BridgeOutboxEvent): String = buildEvent(
        bridgeEventId = event.eventId, packageName = event.packageName, postedAt = event.postedAt,
        title = event.title, text = event.text, bigText = event.bigText,
        subText = event.subText, summaryText = event.summaryText, securityCode = event.securityCode,
    )

    fun buildEvent(
        bridgeEventId: String, packageName: String, postedAt: Long, title: String?, text: String?,
        bigText: String?, subText: String?, summaryText: String?, securityCode: String? = null,
    ): String {
        val base = build(packageName, postedAt, title, text, bigText, subText, summaryText, securityCode)
        return base.dropLast(1) + ",\"bridgeEventId\":" + quote(bridgeEventId) + "}"
    }

    fun build(
        packageName: String,
        postedAt: Long,
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
        summaryText: String?,
        securityCode: String? = null,
    ): String {
        val out = StringBuilder(256)
        out.append('{')
        // Primer campo sin coma inicial; los demás se antepone la coma.
        out.append(quote("packageName")).append(':').append(quote(packageName))
        out.append(",\"postedAt\":").append(postedAt)
        appendNullableField(out, "title", title)
        appendNullableField(out, "text", text)
        appendNullableField(out, "bigText", bigText)
        appendNullableField(out, "subText", subText)
        appendNullableField(out, "summaryText", summaryText)
        appendNullableField(out, "securityCode", securityCode)
        out.append('}')
        return out.toString()
    }

    private fun appendNullableField(out: StringBuilder, key: String, value: String?) {
        out.append(',').append(quote(key)).append(':')
        out.append(if (value == null) "null" else quote(value))
    }

    /** Escapado JSON estricto (comillas, barra invertida y caracteres de control). */
    internal fun quote(raw: String): String {
        val sb = StringBuilder(raw.length + 2)
        sb.append('"')
        for (ch in raw) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u").append(String.format("%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
