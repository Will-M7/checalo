package com.example.ecolacteoshuata.domain.model

import kotlinx.serialization.Serializable

/** Local pending basket only: never a receipt, payment confirmation or synchronization row. */
@Serializable
data class BorradorVentaYape(
    val items:List<CarritoItem>,
    val cliente:Cliente?,
    val nombreLibre:String?,
    val documentoLibre:String?,
    val descuentos:List<Descuento> = emptyList(),
)
