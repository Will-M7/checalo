package com.example.ecolacteoshuata.data.remote.dto

import kotlinx.serialization.Serializable

// Contrato JSON de `POST /api/mobile/cambios` (MobileCambiosController de Laravel).

/** Un alta, cambio o baja hecho en el teléfono (`POST /api/mobile/cambios`). */
@Serializable
data class CambioDto(
    /** Tabla local ("recojo", "usuario"...). */
    val tabla: String,
    /** Id local: numérico si vino de Laravel, UUID si se creó en el teléfono. */
    val id: String,
    /** "guardar" o "eliminar". */
    val operacion: String,
    /** La fila completa del SQLite (valores como texto); vacía al eliminar. */
    val datos: Map<String, String?>? = null,
)

@Serializable
data class SubidaCambiosRequest(val cambios: List<CambioDto>)

/** Id que Laravel le asignó a un registro creado en el teléfono. */
@Serializable
data class IdAsignadoDto(val tabla: String, val id_local: String, val id: String)

@Serializable
data class ErrorCambioDto(val tabla: String, val id: String, val message: String)

@Serializable
data class SubidaCambiosResponse(
    val ids: List<IdAsignadoDto> = emptyList(),
    val errores: List<ErrorCambioDto> = emptyList(),
)
