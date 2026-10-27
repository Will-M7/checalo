package com.example.ecolacteoshuata.data.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.ecolacteoshuata.domain.platform.NotificadorSistema

private const val CANAL = "comunicados"

/** Notificaciones en la barra del sistema; al tocarlas se abre la app. */
class AndroidNotificadorSistema(private val context: Context) : NotificadorSistema {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CANAL, "Comunicados y asesoramiento", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisos que publica la asociación"
                },
            )
        }
    }

    override fun mostrar(id: String, titulo: String, mensaje: String) {
        // Android 13+: sin el permiso no se puede notificar (lo pide MainActivity).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val abrirApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val alTocar = abrirApp?.let {
            PendingIntent.getActivity(context, id.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CANAL) else Notification.Builder(context)
        val notificacion = builder
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(Notification.BigTextStyle().bigText(mensaje))
            .setAutoCancel(true)
            .setContentIntent(alTocar)
            .build()
        manager?.notify(id.hashCode(), notificacion)
    }
}
