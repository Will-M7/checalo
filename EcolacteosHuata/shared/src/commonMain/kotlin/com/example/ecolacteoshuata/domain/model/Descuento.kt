package com.example.ecolacteoshuata.domain.model

/** Descuento del punto de venta; [activo] = "Disponible para el vendedor". */
@kotlinx.serialization.Serializable
data class Descuento(
    val id: String,
    val nombre: String,
    val porcentaje: Double,
    val activo: Boolean,
)
