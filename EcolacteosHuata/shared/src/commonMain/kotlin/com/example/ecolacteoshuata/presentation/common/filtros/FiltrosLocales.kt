package com.example.ecolacteoshuata.presentation.common.filtros

import com.example.ecolacteoshuata.domain.model.Usuario

fun Usuario.rolesParaFiltro(): List<Pair<String, String>> = autorizacion?.roles?.map { it.id to it.nombre }
    ?: rolesAsignados.map { it.valorServidor to it.etiqueta }

fun filtrarUsuarios(usuarios: List<Usuario>, texto: String, rol: String = "", estado: String = "", cantidad: String = ""): List<Usuario> {
    val consulta = texto.trim()
    return usuarios.filter { u ->
        val roles = u.rolesParaFiltro()
        (consulta.isEmpty() || listOf(u.nombres, u.apellidos, u.nombreCompleto, u.usuario, u.documento).any { it.contains(consulta, ignoreCase = true) }) &&
            (rol.isEmpty() || roles.any { it.first == rol }) &&
            (estado.isEmpty() || u.activo == (estado == "1")) &&
            (cantidad.isEmpty() || (cantidad == "varios" && roles.size > 1) || (cantidad == "ninguno" && roles.isEmpty()))
    }
}

fun coincideStock(disponible: Double, filtro: String): Boolean = when (filtro) {
    "disponible" -> disponible > 0
    "bajo" -> disponible > 0 && disponible <= 5
    "agotado" -> disponible <= 0
    "normal" -> disponible > 5
    else -> true
}

fun coincideFecha(fecha: String, desde: String, hasta: String): Boolean = runCatching {
    val actual = kotlinx.datetime.LocalDate.parse(fecha.take(10))
    (desde.isBlank() || actual >= kotlinx.datetime.LocalDate.parse(desde)) &&
        (hasta.isBlank() || actual <= kotlinx.datetime.LocalDate.parse(hasta))
}.getOrDefault(false)
