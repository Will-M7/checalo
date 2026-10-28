package com.example.ecolacteoshuata.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RolAdministrable(val registro_id: Long, val codigo: String, val nombre: String, val activo: Boolean, val protegido: Boolean, val pantallas: List<String>)

@Serializable
data class PantallaAsignable(val id: String, val nombre: String, val area: String, val android: String? = null, val delegable: Boolean)

@Serializable
data class CatalogoAdministrativo(val roles: List<RolAdministrable>, val pantallas: List<PantallaAsignable>, val autorizacion: AutorizacionUsuario)

@Serializable
data class GuardarRolCentral(val nombre: String, val codigo: String, val activo: Boolean, val pantallas: List<String>)

@Serializable
data class GuardarUsuarioCentral(
    val nombres: String, val apellidos: String, val documento: String, val celular: String,
    val usuario: String, val password: String, val activo: Boolean, val rolesSeleccionados: List<String>,
    val finca_ubicacion: String = "", val finca_referencia: String = "",
    val proveedorAcopiadorId: String? = null, val latitud: Double? = null, val longitud: Double? = null,
)

@Serializable
data class UsuarioAdministrado(val id: String, val autorizacion: AutorizacionUsuario, val creado_en: String? = null)
