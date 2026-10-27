package pe.willian.qrlab.bridge

import kotlinx.serialization.Serializable

/**
 * DTO de lo recibido desde Android por el bridge.
 *
 * Representa ÚNICAMENTE el contenido crudo reenviado por el teléfono del
 * negocio. Todavía NO es una notificación Yape interpretada: el formato
 * exacto de la notificación real aún se desconoce y se configurará después
 * vía [YapeNotificationRules] sin reconstruir la arquitectura.
 */
@Serializable
data class RawBridgeNotification(
    val bridgeEventId: String,
    val packageName: String,
    val notificationKey: String? = null,
    /** Instant en ISO-8601 (momento en que Android publicó la notificación). */
    val postedAt: String,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val extras: Map<String, String> = emptyMap(),
)
