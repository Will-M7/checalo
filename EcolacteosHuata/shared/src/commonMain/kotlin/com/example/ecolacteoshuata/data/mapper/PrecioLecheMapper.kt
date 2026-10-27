package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.PrecioLeche
import kotlinx.datetime.LocalDate
import com.example.ecolacteoshuata.db.Precio_leche as PrecioLecheEntity

fun PrecioLecheEntity.toDomain(): PrecioLeche = PrecioLeche(
    id = id,
    precioLitro = precio_litro,
    vigenteDesde = LocalDate.parse(vigente_desde),
)
