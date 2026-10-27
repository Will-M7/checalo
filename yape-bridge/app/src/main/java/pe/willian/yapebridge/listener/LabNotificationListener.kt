package pe.willian.yapebridge.listener

import android.content.ComponentName
import pe.willian.yapebridge.util.NotificationAccess
import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import pe.willian.yapebridge.bridge.BackendBridge
import pe.willian.yapebridge.bridge.CanonicalProtocol
import pe.willian.yapebridge.bridge.DeviceCredentials
import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.model.YapeSecurityCodeExtractor
import pe.willian.yapebridge.store.NotificationStore
import pe.willian.yapebridge.util.ExtrasConverter

/** Captures only recognized incoming Yape notifications on a configured device.
 * Raw content stays in the encrypted outbox; UI and Logcat receive no security codes.
 * The local SQLite write precedes asynchronous authenticated HTTP dispatch.
 */
class LabNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationStore.listenerConnected.value = true
        BackendBridge.heartbeatAsync()
        BackendBridge.requestDrain()
        // Releer únicamente eventos activos de Yape al recuperar el listener; mismo ID = deduplicado.
        runCatching { activeNotifications?.filter { it.packageName == CanonicalProtocol.EXPECTED_PACKAGE }?.forEach(::onNotificationPosted) }
    }

    override fun onListenerDisconnected() {
        NotificationStore.listenerConnected.value = false
        BackendBridge.heartbeatAsync()
        if (NotificationAccess.isEnabled(this)) runCatching { requestRebind(ComponentName(this, LabNotificationListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            if (sbn.packageName != CanonicalProtocol.EXPECTED_PACKAGE) return
            val notification = sbn.notification ?: return
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            // Log mínimo permitido: solo el package. Nada de contenido textual.
            Log.d(TAG, "notification received from package=" + sbn.packageName)
            val captured = map(sbn)
            // The diagnostic view receives only a redacted summary.
            // Persist every nonempty Yape event. Unknown formats and missing codes are reviewed in Core,
            // never silently discarded or treated as payment just because an amount is present.
            if (!CanonicalProtocol.capture(captured.packageName,listOf(captured.title,captured.text,captured.bigText,captured.subText,captured.summaryText))) return
            NotificationStore.push(captured.copy(title="Abono entrante observado",text="Pendiente de conciliación",bigText=null,subText=null,summaryText=null,textLines=emptyList(),extras=emptyMap(),securityCode=null))
            // Persist the small encrypted record now; network IO is always asynchronous.
            BackendBridge.sendAsync(captured)
        } catch (_: Exception) {
            pe.willian.yapebridge.store.BridgeStore.markSendFailed(null, "No se pudo procesar una notificación de Yape; requiere revisión.")
        }
    }

    companion object {
        private const val TAG = "YapeBridgeLab"

        internal fun map(sbn: StatusBarNotification): CapturedNotification {
            val notification = sbn.notification
            val extras: Bundle = notification.extras
            return CapturedNotification(
                packageName = sbn.packageName,
                notificationKey = sbn.key,
                postedAt = sbn.postTime,
                title = charSequenceOf(extras, Notification.EXTRA_TITLE),
                text = charSequenceOf(extras, Notification.EXTRA_TEXT),
                bigText = charSequenceOf(extras, Notification.EXTRA_BIG_TEXT) ?: charSequenceArrayOf(extras, Notification.EXTRA_TEXT_LINES).joinToString("\n").ifBlank { null },
                subText = charSequenceOf(extras, Notification.EXTRA_SUB_TEXT),
                summaryText = charSequenceOf(extras, Notification.EXTRA_SUMMARY_TEXT),
                textLines = charSequenceArrayOf(extras, Notification.EXTRA_TEXT_LINES),
                extras = emptyMap(),
            ).let { captured ->
                captured.copy(securityCode = YapeSecurityCodeExtractor.extract(captured))
            }
        }

        private fun charSequenceOf(extras: Bundle, key: String): String? {
            return try {
                val cs = extras.getCharSequence(key) ?: return null
                val s = cs.toString()
                if (s.isEmpty()) null else s
            } catch (_: Exception) {
                null
            }
        }

        private fun charSequenceArrayOf(extras: Bundle, key: String): List<String> = try {
            extras.getCharSequenceArray(key)
                ?.mapNotNull { it?.toString()?.takeIf(String::isNotEmpty) }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        internal fun bundleToStringMap(extras: Bundle): Map<String, String> {
            val raw = LinkedHashMap<String, Any?>(extras.size())
            for (key in extras.keySet()) {
                if (key == null) continue
                @Suppress("DEPRECATION")
                val value: Any? = try {
                    extras.get(key)
                } catch (_: Exception) {
                    "<unreadable>"
                }
                // Se omiten iconos/bitmaps: no se muestran ni guardan imágenes.
                if (value is android.graphics.Bitmap ||
                    value is android.graphics.drawable.Icon
                ) {
                    continue
                }
                raw[key] = value
            }
            return ExtrasConverter.sanitizeExtrasMap(raw)
        }
    }
}
