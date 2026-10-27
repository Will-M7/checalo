package com.example.ecolacteoshuata.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AsignacionDistribucion(val codigo: String, val rol_id: Long? = null, val pantallas: List<String>)
@Serializable
data class DistribucionRoles(val id: Long, val codigo: String, val nombre: String, val tipo: String, val version: Int, val predeterminada: Boolean, val asignaciones: List<AsignacionDistribucion>, val origen: JsonObject? = null, val coincide: Boolean = false)
@Serializable
data class UsuarioDistribucion(val id: String, val nombre: String, val roles: List<String>)
@Serializable
data class AplicacionDistribucion(val id: String, val nombre: String, val tipo: String, val fecha: String, val actor_id: Long, val recuperacion_de: String? = null, val recuperable: Boolean)
@Serializable
data class CatalogoDistribuciones(val distribuciones: List<DistribucionRoles>, val roles: List<RolAdministrable>, val pantallas: List<PantallaAsignable>, val historial: List<AplicacionDistribucion>, val usuarios_prueba: List<UsuarioDistribucion>, val rol_prueba_presente: Boolean, val auxiliar_pendiente: Boolean, val autorizacion: AutorizacionUsuario, val guardada_id: Long? = null)
@Serializable
data class DiferenciaDistribucion(val codigo: String, val nombre: String, val activo: Boolean, val actuales: List<String>, val destino: List<String>, val agrega: List<String>, val retira: List<String>, val usuarios: List<UsuarioDistribucion>)
@Serializable
data class VistaDistribucion(val nombre: String, val operacion: String, val roles: List<DiferenciaDistribucion>, val errores: List<String>, val advertencias: List<String>, val no_afectados: List<String>, val token: String? = null)
@Serializable
data class GuardarDistribucion(val modo: String, val id: Long? = null, val version: Int? = null, val nombre: String, val origen: Long? = null, val asignaciones: List<AsignacionDistribucion> = emptyList())
@Serializable
data class ReemplazoRolPrueba(val usuario_id: String, val codigo: String)
@Serializable
data class PrepararDistribucion(val operacion: String, val distribucion_id: Long? = null, val aplicacion_id: String? = null, val reemplazos: List<ReemplazoRolPrueba> = emptyList())
@Serializable
data class ConfirmarDistribucion(val token: String, val confirmar: Boolean)
