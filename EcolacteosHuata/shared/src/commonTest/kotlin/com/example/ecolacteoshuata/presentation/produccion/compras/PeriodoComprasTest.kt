package com.example.ecolacteoshuata.presentation.produccion.compras

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodoComprasTest {
    // Lunes 28 de setiembre de 2026.
    private val hoy = LocalDate(2026, 9, 28)

    @Test
    fun hoyYAyerSonUnSoloDia() {
        assertEquals(hoy to hoy, PeriodoCompras.HOY.rango(hoy))
        assertEquals(LocalDate(2026, 9, 27) to LocalDate(2026, 9, 27), PeriodoCompras.AYER.rango(hoy))
    }

    @Test
    fun semanaPasadaVaDeLunesADomingo() {
        assertEquals(LocalDate(2026, 9, 21) to LocalDate(2026, 9, 27), PeriodoCompras.SEMANA_PASADA.rango(hoy))
        assertEquals(hoy to hoy, PeriodoCompras.ESTA_SEMANA.rango(hoy))
    }

    @Test
    fun mesPasadoEsElMesCalendarioCompleto() {
        assertEquals(LocalDate(2026, 8, 1) to LocalDate(2026, 8, 31), PeriodoCompras.MES_PASADO.rango(hoy))
        assertEquals(LocalDate(2026, 9, 1) to hoy, PeriodoCompras.ESTE_MES.rango(hoy))
    }
}
