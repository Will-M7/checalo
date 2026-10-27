package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.db.Ruta as RutaEntity

fun RutaEntity.toDomain(proveedorIds: List<String> = emptyList()): Ruta = Ruta(
    id = id,
    acopiadorId = acopiador_id,
    nombre = nombre,
    activa = activa == 1L,
    proveedorIds = proveedorIds,
    trazo = decodificarTrazo(trazo),
)

/** El trazo se guarda en SQLite como texto "lat,lng;lat,lng;..." para no agregar otra tabla. */
fun codificarTrazo(puntos: List<PuntoGeo>): String? =
    puntos.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.lat},${it.lng}" }

fun decodificarTrazo(texto: String?): List<PuntoGeo> =
    texto.orEmpty().split(";").mapNotNull { par ->
        val partes = par.split(",")
        val lat = partes.getOrNull(0)?.trim()?.toDoubleOrNull()
        val lng = partes.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (lat != null && lng != null) PuntoGeo(lat, lng) else null
    }
