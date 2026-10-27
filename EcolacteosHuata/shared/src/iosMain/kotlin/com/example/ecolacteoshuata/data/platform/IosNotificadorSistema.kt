package com.example.ecolacteoshuata.data.platform

import com.example.ecolacteoshuata.domain.platform.NotificadorSistema
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/** Notificaciones locales de iOS; también se muestran con la app abierta (como banner). */
class IosNotificadorSistema : NotificadorSistema {
    private val centro = UNUserNotificationCenter.currentNotificationCenter()

    // El centro guarda una referencia débil al delegado: se mantiene aquí.
    private val delegado = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            withCompletionHandler(
                UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound,
            )
        }
    }

    init {
        centro.delegate = delegado
    }

    override fun pedirPermiso() {
        centro.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }

    override fun mostrar(id: String, titulo: String, mensaje: String) {
        val contenido = UNMutableNotificationContent().apply {
            setTitle(titulo)
            setBody(mensaje)
            setSound(UNNotificationSound.defaultSound())
        }
        centro.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, contenido, null)) { _ -> }
    }
}
