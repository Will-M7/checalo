package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.db.Acopiador as AcopiadorEntity
import com.example.ecolacteoshuata.db.Turno as TurnoEntity

fun AcopiadorEntity.toDomain(nombreCompleto: String): Acopiador = Acopiador(
    id = id,
    usuarioId = usuario_id,
    nombreCompleto = nombreCompleto,
    rutaNombre = ruta_nombre,
)

fun TurnoEntity.toDomain(): Turno = Turno(
    id = id,
    acopiadorId = acopiador_id,
    nombre = nombre,
    horaInicio = hora_inicio,
    horaFin = hora_fin,
)
