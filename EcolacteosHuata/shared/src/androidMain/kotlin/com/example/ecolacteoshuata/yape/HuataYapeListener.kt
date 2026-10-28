package com.example.ecolacteoshuata.yape

import android.content.ComponentName
import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.ecolacteoshuata.yape.bridge.CanonicalProtocol
import com.example.ecolacteoshuata.yape.bridge.DeviceCredentials
import com.example.ecolacteoshuata.yape.model.CapturedNotification
import com.example.ecolacteoshuata.yape.model.YapeSecurityCodeExtractor

/** Captures only recognized incoming Yape notifications on a configured device.
 * Raw content stays in the encrypted outbox; UI and Logcat receive no security codes.
 * The local SQLite write precedes asynchronous authenticated HTTP dispatch.
 */
class HuataYapeListener : NotificationListenerService() {

    private val runtime: HuataYapeReception get() = org.koin.core.context.GlobalContext.get().get<com.example.ecolacteoshuata.domain.platform.YapeReception>() as HuataYapeReception
    override fun onListenerConnected() {
        runtime.listenerChanged(true)
        // Do not replay active notifications from before consent. Newly posted events retain stable IDs.
    }
    override fun onListenerDisconnected() {
        runtime.listenerChanged(false)
        runCatching { requestRebind(ComponentName(this, HuataYapeListener::class.java)) }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != CanonicalProtocol.EXPECTED_PACKAGE || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        runCatching { if (runtime.mayCapture()) runtime.capture(map(sbn)) }
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

    }
}
