package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.Cliente
import com.example.ecolacteoshuata.db.Cliente as ClienteEntity

fun ClienteEntity.toDomain(): Cliente = Cliente(
    id = id,
    nombres = nombres,
    apellidos = apellidos,
    documento = documento,
    comprasCount = compras_count.toInt(),
    favorito = favorito == 1L,
    descuentoFavorito = descuento_favorito,
    mayorista = mayorista == 1L,
    descuentoMayorista = descuento_mayorista,
)
