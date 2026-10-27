package com.example.ecolacteoshuata.domain.model

import kotlinx.datetime.LocalDate

data class UnidadMedida(
    val id: String,
    val nombre: String,
    val simbolo: String,
)

data class Insumo(
    val id: String,
    val nombre: String,
    val unidadMedidaId: String,
    val unidadMedidaSimbolo: String = "",
    val stockMinimo: Double,
    val costoUnitario: Double = 0.0,
    /** Stock con el que se registró el insumo; el inventario le suma compras y le resta consumos. */
    val stockActual: Double = 0.0,
)

data class RecetaDetalle(
    val id: String,
    val recetaId: String,
    val insumoId: String,
    val insumoNombre: String = "",
    val cantidad: Double,
)

data class Receta(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val productoId: String?,
    val detalle: List<RecetaDetalle> = emptyList(),
)

data class CompraDetalle(
    val id: String,
    val compraId: String,
    val insumoId: String,
    val insumoNombre: String = "",
    val cantidad: Double,
    val costoUnitario: Double,
    val subtotal: Double,
)

data class Compra(
    val id: String,
    val fecha: LocalDate,
    val proveedorInsumo: String,
    val descripcion: String,
    val total: Double,
    val detalle: List<CompraDetalle> = emptyList(),
)

data class ProduccionRegistro(
    val id: String,
    val fecha: LocalDate,
    val recetaId: String,
    val recetaNombre: String = "",
    val productoId: String?,
    val cantidadLotes: Double,
    val cantidadProducida: Double,
    val observaciones: String,
    val turnoId: String? = null,
    /** true = publicada (estado "completada" en Laravel); false = planificada. */
    val publicada: Boolean = false,
    /** Nombre con el que se vende (el de la receta al producirla). */
    val nombre: String = "",
    val lote: String = "",
    val cantidadVendida: Double = 0.0,
    val responsableId: String? = null,
) {
    /** Lo que queda para vender, como `cantidadDisponible()` en Laravel. */
    val disponible: Double get() = (cantidadProducida - cantidadVendida).coerceAtLeast(0.0)
}

data class TurnoProduccion(
    val id: String,
    val nombre: String,
    val horaInicio: String,
    val horaFin: String,
)

data class InventarioInsumoItem(
    val insumo: Insumo,
    val comprado: Double,
    val consumido: Double,
    val stock: Double,
)
