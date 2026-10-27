package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.domain.model.CarritoItem
import com.example.ecolacteoshuata.domain.model.Descuento
import com.example.ecolacteoshuata.domain.model.TotalesCarrito

/**
 * Como la venta de Laravel: se suman los porcentajes de los descuentos marcados y el tipo de
 * descuento guarda sus nombres separados por coma.
 */
class CalcularTotalesCarritoUseCase {
    operator fun invoke(items: List<CarritoItem>, descuentos: List<Descuento>): TotalesCarrito {
        val subtotal = redondear2(items.sumOf { it.subtotal })
        val porcentaje = descuentos.sumOf { it.porcentaje }
        val descuento = redondear2(subtotal * porcentaje / 100.0)
        return TotalesCarrito(
            subtotal = subtotal,
            descuento = descuento,
            descuentoTipo = descuentos.joinToString(", ") { it.nombre },
            total = redondear2(subtotal - descuento),
        )
    }
}
