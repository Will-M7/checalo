package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.data.remote.dto.LoginResponseDto
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.UsuarioRemoto

/** Los roles abiertos se conservan en el snapshot. Sin categoría ni contrato de autorización no se inicia una sesión incompleta. */
fun LoginResponseDto.toDomain(): UsuarioRemoto? {
    val categoria = Rol.fromServerValue(rol)
    if (categoria == null && autorizacion == null) return null
    // Sin categoría histórica no se conceden derechos: todos proceden del snapshot central.
    val rol = categoria
    return UsuarioRemoto(
        id = id,
        usuario = usuario,
        nombres = nombres,
        apellidos = apellidos,
        documento = documento,
        celular = celular,
        rol = rol,
        activo = activo,
        tomaAsistencia = toma_asistencia,
        token = token,
        autorizacion = autorizacion,
        roles = roles.mapNotNull(Rol::fromServerValue).toSet(),
    )
}
