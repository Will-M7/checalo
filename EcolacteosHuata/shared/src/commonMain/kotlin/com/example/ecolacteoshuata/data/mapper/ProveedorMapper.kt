package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.db.Proveedor as ProveedorEntity

fun ProveedorEntity.toDomain(nombreCompleto: String): Proveedor = Proveedor(
    id = id,
    usuarioId = usuario_id,
    nombreCompleto = nombreCompleto,
    fincaUbicacion = finca_ubicacion,
    fincaReferencia = finca_referencia,
    latitud = latitud,
    longitud = longitud,
    acopiadorId = acopiador_id,
    bloqueado = bloqueado == 1L,
    alteraciones = alteraciones.toInt(),
)
