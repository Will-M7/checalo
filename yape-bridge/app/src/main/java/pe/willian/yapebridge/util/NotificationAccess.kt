package pe.willian.yapebridge.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import pe.willian.yapebridge.listener.LabNotificationListener

/**
 * Verifica si el usuario concedió Notification Access a esta app.
 * Se lee el setting seguro ENABLED_NOTIFICATION_LISTENERS; no se
 * intenta conceder el permiso programáticamente.
 */
object NotificationAccess {

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        if (flat.isEmpty()) return false
        val expected = ComponentName(context, LabNotificationListener::class.java)
        for (entry in flat.split(":")) {
            if (TextUtils.isEmpty(entry)) continue
            val cn = try {
                ComponentName.unflattenFromString(entry)
            } catch (_: Exception) {
                null
            } ?: continue
            if (cn.packageName == expected.packageName &&
                cn.className == expected.className
            ) {
                return true
            }
        }
        return false
    }
}
