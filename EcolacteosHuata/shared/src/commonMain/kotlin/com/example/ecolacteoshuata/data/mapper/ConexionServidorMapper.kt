package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.ConexionServidor
import com.example.ecolacteoshuata.db.Conexion_servidor as ConexionServidorEntity

fun ConexionServidorEntity.toDomain(): ConexionServidor = ConexionServidor(
    host = host,
    puerto = puerto,
    activa = activa == 1L,
    ultimaSincronizacion = ultima_sincronizacion,
    token = token,
)
