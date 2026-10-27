package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.Usuario
import kotlinx.datetime.LocalDateTime
import com.example.ecolacteoshuata.db.Usuario as UsuarioEntity

fun UsuarioEntity.toDomain(): Usuario = Usuario(
    id = id,
    nombres = nombres,
    apellidos = apellidos,
    documento = documento,
    celular = celular,
    rol = rol.takeIf { it.isNotBlank() }?.let(Rol::fromStoredName),
    usuario = usuario,
    passwordHash = password_hash,
    activo = activo == 1L,
    creadoEn = LocalDateTime.parse(creado_en),
    rolesAdicionales = Rol.decodificarLista(roles),
    tomaAsistencia = toma_asistencia == 1L,
)
