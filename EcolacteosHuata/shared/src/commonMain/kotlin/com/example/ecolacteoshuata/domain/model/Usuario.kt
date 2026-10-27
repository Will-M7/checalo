package com.example.ecolacteoshuata.domain.model

import kotlinx.datetime.LocalDateTime

data class Usuario(
    val id: String,
    val nombres: String,
    val apellidos: String,
    val documento: String,
    val celular: String,
    val rol: Rol?,
    val usuario: String,
    val passwordHash: String,
    val activo: Boolean,
    val creadoEn: LocalDateTime,
    /** Roles además del principal (`users.roles` en Laravel). */
    val rolesAdicionales: Set<Rol> = emptySet(),
    /** El administrador le asignó "Tomar asistencia" (`users.toma_asistencia`). */
    val tomaAsistencia: Boolean = false,
    val autorizacion: AutorizacionUsuario? = null,
) {
    /** Como `puedeTomarAsistencia()` de Laravel: el administrador siempre la toma. */
    val puedeTomarAsistencia: Boolean get() = autorizacion?.permite("asistencia.tomar") == true

    /** Los acopiadores marcan su asistencia por horario, no en la tabla general. */
    val esAcopiador: Boolean get() = tieneRol(Rol.ACOPIADOR)

    /** Como `participaEnAsistenciaGeneral()`: ni acopiadores ni quienes solo son proveedores. */
    val participaEnAsistenciaGeneral: Boolean get() = !esAcopiador && rolesAsignados.any { it != Rol.PROVEEDOR }

    /** Todos los roles del usuario, con el principal incluido, en el orden de Laravel. */
    val rolesAsignados: List<Rol> get() = autorizacion?.categorias_historicas?.mapNotNull(Rol::fromServerValue) ?: Rol.ordenLaravel.filter { it == rol || it in rolesAdicionales }

    fun tieneRol(r: Rol): Boolean = r in rolesAsignados

    val nombreCompleto: String get() = "$nombres $apellidos"
    val iniciales: String get() = buildString {
        nombres.firstOrNull()?.let { append(it) }
        apellidos.firstOrNull()?.let { append(it) }
    }.uppercase()
}
