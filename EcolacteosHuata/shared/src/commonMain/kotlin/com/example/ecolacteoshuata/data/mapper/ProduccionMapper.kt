package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.Compra
import com.example.ecolacteoshuata.domain.model.CompraDetalle
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.model.ProduccionRegistro
import com.example.ecolacteoshuata.domain.model.Receta
import com.example.ecolacteoshuata.domain.model.RecetaDetalle
import com.example.ecolacteoshuata.domain.model.TurnoProduccion
import com.example.ecolacteoshuata.domain.model.UnidadMedida
import kotlinx.datetime.LocalDate
import com.example.ecolacteoshuata.db.Unidad_medida as UnidadMedidaEntity
import com.example.ecolacteoshuata.db.Insumo as InsumoEntity
import com.example.ecolacteoshuata.db.Receta as RecetaEntity
import com.example.ecolacteoshuata.db.Receta_detalle as RecetaDetalleEntity
import com.example.ecolacteoshuata.db.Compra as CompraEntity
import com.example.ecolacteoshuata.db.Compra_detalle as CompraDetalleEntity
import com.example.ecolacteoshuata.db.Produccion as ProduccionEntity
import com.example.ecolacteoshuata.db.Turno_produccion as TurnoProduccionEntity

fun UnidadMedidaEntity.toDomain(): UnidadMedida = UnidadMedida(id = id, nombre = nombre, simbolo = simbolo)

fun InsumoEntity.toDomain(unidadSimbolo: String = ""): Insumo = Insumo(
    id = id,
    nombre = nombre,
    unidadMedidaId = unidad_medida_id,
    unidadMedidaSimbolo = unidadSimbolo,
    stockMinimo = stock_minimo,
    costoUnitario = costo_unitario,
    stockActual = stock_actual,
)

fun RecetaDetalleEntity.toDomain(insumoNombre: String = ""): RecetaDetalle = RecetaDetalle(
    id = id,
    recetaId = receta_id,
    insumoId = insumo_id,
    insumoNombre = insumoNombre,
    cantidad = cantidad,
)

fun RecetaEntity.toDomain(detalle: List<RecetaDetalle> = emptyList()): Receta = Receta(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    productoId = producto_id,
    detalle = detalle,
)

fun CompraDetalleEntity.toDomain(insumoNombre: String = ""): CompraDetalle = CompraDetalle(
    id = id,
    compraId = compra_id,
    insumoId = insumo_id,
    insumoNombre = insumoNombre,
    cantidad = cantidad,
    costoUnitario = costo_unitario,
    subtotal = subtotal,
)

fun CompraEntity.toDomain(detalle: List<CompraDetalle> = emptyList()): Compra = Compra(
    id = id,
    fecha = LocalDate.parse(fecha),
    proveedorInsumo = proveedor_insumo,
    descripcion = descripcion,
    total = total,
    detalle = detalle,
)

fun ProduccionEntity.toDomain(recetaNombre: String = ""): ProduccionRegistro = ProduccionRegistro(
    id = id,
    fecha = LocalDate.parse(fecha),
    recetaId = receta_id,
    recetaNombre = recetaNombre,
    productoId = producto_id,
    cantidadLotes = cantidad_lotes,
    cantidadProducida = cantidad_producida,
    observaciones = observaciones,
    turnoId = turno_id,
    publicada = estado == "COMPLETADA",
    nombre = nombre.ifBlank { recetaNombre },
    lote = lote,
    cantidadVendida = cantidad_vendida,
    responsableId = responsable_id,
)

fun TurnoProduccionEntity.toDomain(): TurnoProduccion = TurnoProduccion(id = id, nombre = nombre, horaInicio = hora_inicio, horaFin = hora_fin)
