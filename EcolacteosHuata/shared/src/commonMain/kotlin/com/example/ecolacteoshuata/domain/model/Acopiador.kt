package com.example.ecolacteoshuata.domain.model

data class Acopiador(
    val id: String,
    val usuarioId: String,
    val nombreCompleto: String,
    val rutaNombre: String,
)

data class Turno(
    val id: String,
    val acopiadorId: String,
    val nombre: String,
    val horaInicio: String,
    val horaFin: String,
)

data class Ruta(
    val id: String,
    val acopiadorId: String,
    val nombre: String,
    val activa: Boolean,
    val proveedorIds: List<String> = emptyList(),
    /** Camino que planta trazó para el acopiador, como puntos (latitud, longitud). */
    val trazo: List<PuntoGeo> = emptyList(),
)

data class PuntoGeo(val lat: Double, val lng: Double)
