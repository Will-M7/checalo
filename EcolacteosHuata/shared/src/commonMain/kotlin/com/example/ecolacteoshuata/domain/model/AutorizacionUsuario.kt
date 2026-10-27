package com.example.ecolacteoshuata.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RolAsignado(val id: String, val nombre: String, val pantallas: List<String> = emptyList(), val activo: Boolean = true, val auxiliar: Boolean = false) {
    val etiqueta: String get() = nombre
}

/** Identificadores abiertos enviados por Laravel; Rol permanece solo como categoría de negocio. */
@Serializable
data class AutorizacionUsuario(
    val usuario_id: String,
    val categorias_historicas: List<String>? = null,
    val version: String,
    val roles: List<RolAsignado> = emptyList(),
    val catalogo_roles: List<RolAsignado> = emptyList(),
    val pantallas: List<String> = emptyList(),
    val pantallas_adicionales: List<String> = emptyList(),
    val mapeo_android: Map<String, String> = emptyMap(),
    val politica_offline: String = "ultima_autorizacion_conocida",
) {
    fun permite(id: String): Boolean = id in pantallas
}
