package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.EntregaPlanta
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Recojo
import kotlinx.datetime.LocalDate
import com.example.ecolacteoshuata.db.Recojo as RecojoEntity
import com.example.ecolacteoshuata.db.Entrega_planta as EntregaPlantaEntity

fun RecojoEntity.toDomain(proveedorNombre: String = ""): Recojo = Recojo(
    id = id,
    proveedorId = proveedor_id,
    proveedorNombre = proveedorNombre,
    acopiadorId = acopiador_id,
    turnoId = turno_id,
    fecha = LocalDate.parse(fecha),
    litros = litros,
    litrosEntregados = litros_entregados,
    estadoLeche = EstadoLeche.valueOf(estado_leche),
    estadoOtro = estado_otro,
    precioLitro = precio_litro,
    total = total,
    pagado = pagado == 1L,
    metodoPago = metodo_pago?.let { MetodoPago.valueOf(it) },
    entregado = entregado == 1L,
    sincronizado = sincronizado == 1L,
)

fun EntregaPlantaEntity.toDomain(): EntregaPlanta = EntregaPlanta(
    id = id,
    acopiadorId = acopiador_id,
    turnoId = turno_id,
    fecha = LocalDate.parse(fecha),
    litrosTotal = litros_total,
    totalPagado = total_pagado,
    totalDeuda = total_deuda,
    proveedoresNoRecogidos = proveedores_no_recogidos.toInt(),
    guia = guia,
)
