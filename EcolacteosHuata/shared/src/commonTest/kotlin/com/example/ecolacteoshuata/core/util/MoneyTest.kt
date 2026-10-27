package com.example.ecolacteoshuata.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    @Test
    fun vueltoEsLaDiferenciaCuandoElEfectivoEsMayor() {
        assertEquals(10.0, calcularVuelto(total = 40.0, efectivoRecibido = 50.0))
    }

    @Test
    fun vueltoEsNuloCuandoElPagoEsExacto() {
        assertNull(calcularVuelto(total = 40.0, efectivoRecibido = 40.0))
    }

    @Test
    fun vueltoEsNuloCuandoElEfectivoEsMenorAlTotal() {
        assertNull(calcularVuelto(total = 40.0, efectivoRecibido = 30.0))
    }

    @Test
    fun totalDeRecojoMultiplicaLitrosPorPrecioYRedondea() {
        assertEquals(72.20, calcularTotalRecojo(litros = 38.0, precioLitro = 1.90))
    }

    @Test
    fun totalDeRecojoRedondeaADosDecimales() {
        assertEquals(19.05, calcularTotalRecojo(litros = 10.025, precioLitro = 1.90))
    }
}
