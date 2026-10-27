package com.example.ecolacteoshuata.domain.model

data class Proveedor(
    val id: String,
    val usuarioId: String,
    val nombreCompleto: String,
    val fincaUbicacion: String,
    val fincaReferencia: String,
    val latitud: Double?,
    val longitud: Double?,
    val acopiadorId: String?,
    val bloqueado: Boolean,
    val alteraciones: Int,
) {
    companion object {
        const val MAX_ALTERACIONES = 3
    }

    val debeSerBloqueado: Boolean get() = alteraciones >= MAX_ALTERACIONES
}
