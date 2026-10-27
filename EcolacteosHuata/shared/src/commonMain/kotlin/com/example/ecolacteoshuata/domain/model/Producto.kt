package com.example.ecolacteoshuata.domain.model

/**
 * Lo que se vende: una producción publicada. Como en Laravel no hay catálogo de productos; el
 * precio es el costo de la receta y el stock, lo producido menos lo vendido.
 */
@kotlinx.serialization.Serializable
data class Producto(
    /** Id de la producción. */
    val id: String,
    val nombre: String,
    val descripcion: String,
    val precio: Double,
    val activo: Boolean,
    val disponible: Double = 0.0,
)
