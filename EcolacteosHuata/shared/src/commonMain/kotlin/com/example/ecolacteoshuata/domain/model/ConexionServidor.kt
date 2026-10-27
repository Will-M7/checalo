package com.example.ecolacteoshuata.domain.model

data class ConexionServidor(
    val host: String = "",
    val puerto: String = "8000",
    val activa: Boolean = false,
    val ultimaSincronizacion: String? = null,
    /** Token de `POST /api/mobile/login`; sin él no se puede descargar desde Laravel. */
    val token: String? = null,
)
