package com.example.ecolacteoshuata.domain.model

import kotlinx.datetime.LocalDateTime

data class VentaItem(
    val id: String,
    val ventaId: String,
    /** Producción de la que salió lo vendido (null en ventas antiguas). */
    val produccionId: String?,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val subtotal: Double,
)

data class Venta(
    val id: String,
    val numeroBoleta: String,
    val clienteId: String?,
    val clienteNombreLibre: String?,
    val clienteDocLibre: String?,
    val fecha: LocalDateTime,
    val subtotal: Double,
    val descuento: Double,
    /** Nombres de los descuentos aplicados, separados por coma (vacío = sin descuento), como en Laravel. */
    val descuentoTipo: String,
    val total: Double,
    val metodoPago: MetodoPago,
    val efectivoRecibido: Double?,
    val vuelto: Double?,
    val items: List<VentaItem> = emptyList(),
    val vendedorId: String? = null,
) {
    val nombreClienteMostrar: String
        get() = clienteNombreLibre?.takeIf { it.isNotBlank() } ?: "Cliente varios"
}

@kotlinx.serialization.Serializable
data class CarritoItem(
    val producto: Producto,
    val cantidad: Int,
) {
    val subtotal: Double get() = producto.precio * cantidad
}

data class TotalesCarrito(
    val subtotal: Double,
    val descuento: Double,
    val descuentoTipo: String,
    val total: Double,
)
