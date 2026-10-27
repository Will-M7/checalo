package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.domain.model.CarritoItem
import com.example.ecolacteoshuata.domain.model.Descuento
import com.example.ecolacteoshuata.domain.model.Producto
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Los descuentos del catálogo (como en Laravel): se suman sus porcentajes sobre el subtotal. */
class CalcularTotalesCarritoUseCaseTest {
    private val useCase = CalcularTotalesCarritoUseCase()
    private val producto = Producto(id = "p1", nombre = "Leche fresca 1 L", descripcion = "", precio = 4.2, activo = true)

    private fun descuento(nombre: String, porcentaje: Double) = Descuento(id = nombre, nombre = nombre, porcentaje = porcentaje, activo = true)

    @Test
    fun sinDescuentosElTotalEsElSubtotal() {
        val totales = useCase(listOf(CarritoItem(producto, 3)), emptyList())

        assertCercano(12.6, totales.subtotal)
        assertEquals(0.0, totales.descuento)
        assertEquals(totales.subtotal, totales.total)
        assertEquals("", totales.descuentoTipo)
    }

    @Test
    fun unDescuentoSeAplicaSobreElSubtotal() {
        val totales = useCase(listOf(CarritoItem(producto, 10)), listOf(descuento("Mayorista", 6.0)))

        assertCercano(42.0, totales.subtotal)
        assertCercano(2.52, totales.descuento)
        assertCercano(39.48, totales.total)
        assertEquals("Mayorista", totales.descuentoTipo)
    }

    @Test
    fun variosDescuentosSumanSusPorcentajesYSeNombranTodos() {
        val totales = useCase(listOf(CarritoItem(producto, 10)), listOf(descuento("Mayorista", 6.0), descuento("Temporada", 4.0)))

        assertCercano(4.2, totales.descuento)
        assertCercano(37.8, totales.total)
        assertEquals("Mayorista, Temporada", totales.descuentoTipo)
    }

    private fun assertCercano(esperado: Double, actual: Double) {
        assertTrue(abs(esperado - actual) <= 0.01, "Se esperaba <$esperado> pero fue <$actual>")
    }
}
