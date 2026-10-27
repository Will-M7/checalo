package com.example.ecolacteoshuata.domain.model

import kotlinx.datetime.LocalDateTime

enum class TipoNotificacion {
    SIN_ENTREGA, LABORATORIO_NO_CONFORME, VENTA_REGISTRADA, PAGO_PENDIENTE, COMUNICADO
}

data class Notificacion(
    val id: String,
    val tipo: TipoNotificacion,
    val titulo: String,
    val mensaje: String,
    val fecha: LocalDateTime,
)

/**
 * Notificación de la campana, como `notifications` de Laravel (database): la reciben los
 * usuarios al publicarse un comunicado o asesoramiento, o cuando un proveedor cambia su ubicación.
 */
data class NotificacionUsuario(
    val id: String,
    /** Clase de Laravel: "ComunicadoPublicado", "ProveedorUbicacionActualizada"... */
    val tipo: String,
    val titulo: String,
    val mensaje: String,
    val leida: Boolean,
    /** ISO 8601 con zona, tal como la manda Laravel. */
    val creadaEn: String,
) {
    val esAsesoramiento: Boolean get() = titulo.startsWith("Asesoramiento", ignoreCase = true)
    val esComunicado: Boolean get() = tipo == "ComunicadoPublicado"
}
