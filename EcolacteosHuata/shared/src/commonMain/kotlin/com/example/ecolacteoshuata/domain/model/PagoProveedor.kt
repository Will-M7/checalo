package com.example.ecolacteoshuata.domain.model

import kotlinx.datetime.LocalDate

data class PagoProveedor(
    val id: String,
    val proveedorId: String,
    val recojoId: String?,
    val fecha: LocalDate,
    val monto: Double,
    val metodoPago: MetodoPago,
    val periodo: String,
)

data class Comunicado(
    val id: String,
    val tipo: TipoComunicado,
    val titulo: String,
    val descripcion: String,
    val fecha: LocalDate,
    val autorId: String,
    /** Roles que lo ven; `null` = todos los roles (igual que `comunicados.roles` en Laravel). */
    val roles: Set<Rol>? = null,
) {
    fun visibleParaRol(rol: Rol): Boolean = roles == null || rol in roles
}

data class Empresa(
    val id: String,
    val razonSocial: String,
    val ruc: String,
    val telefono: String,
    val direccion: String,
)

data class PrecioLeche(
    val id: String,
    val precioLitro: Double,
    val vigenteDesde: LocalDate,
)
