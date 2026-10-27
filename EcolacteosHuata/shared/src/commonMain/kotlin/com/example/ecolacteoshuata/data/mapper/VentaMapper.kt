package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Venta
import com.example.ecolacteoshuata.domain.model.VentaItem
import kotlinx.datetime.LocalDateTime
import com.example.ecolacteoshuata.db.Venta as VentaEntity
import com.example.ecolacteoshuata.db.Venta_item as VentaItemEntity

fun VentaItemEntity.toDomain(): VentaItem = VentaItem(
    id = id,
    ventaId = venta_id,
    produccionId = produccion_id ?: producto_id.takeIf { it.isNotBlank() },
    nombre = nombre,
    precioUnitario = precio_unitario,
    cantidad = cantidad.toInt(),
    subtotal = subtotal,
)

fun VentaEntity.toDomain(items: List<VentaItem> = emptyList()): Venta = Venta(
    id = id,
    numeroBoleta = numero_boleta,
    clienteId = cliente_id,
    clienteNombreLibre = cliente_nombre_libre,
    clienteDocLibre = cliente_doc_libre,
    fecha = LocalDateTime.parse(fecha),
    subtotal = subtotal,
    descuento = descuento,
    descuentoTipo = descuento_tipo,
    total = total,
    metodoPago = MetodoPago.entries.firstOrNull { it.name.equals(metodo_pago, ignoreCase = true) } ?: MetodoPago.OTRO,
    efectivoRecibido = efectivo_recibido,
    vuelto = vuelto,
    items = items,
    vendedorId = vendedor_id,
)
