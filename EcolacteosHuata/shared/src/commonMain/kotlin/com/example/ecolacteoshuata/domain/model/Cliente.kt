package com.example.ecolacteoshuata.domain.model

@kotlinx.serialization.Serializable
data class Cliente(
    val id: String,
    val nombres: String,
    val apellidos: String,
    val documento: String,
    val comprasCount: Int,
    val favorito: Boolean,
    val descuentoFavorito: Double,
    val mayorista: Boolean,
    val descuentoMayorista: Double,
) {
    val nombreCompleto: String get() = if (apellidos.isBlank()) nombres else "$nombres $apellidos"
}
