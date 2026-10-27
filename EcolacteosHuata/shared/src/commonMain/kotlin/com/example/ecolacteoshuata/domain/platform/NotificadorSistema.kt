package com.example.ecolacteoshuata.domain.platform

/**
 * Muestra una notificación del sistema en el celular (barra de notificaciones en Android,
 * centro de notificaciones en iOS). Cada plataforma aporta la suya en `platformModule`.
 */
interface NotificadorSistema {
    /** Pide permiso para notificar si hace falta (Android 13+ / iOS). */
    fun pedirPermiso() {}

    fun mostrar(id: String, titulo: String, mensaje: String)
}
