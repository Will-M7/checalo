package com.example.ecolacteoshuata.core.ocr

import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultadoLaboratorioOcrParserTest {

    @Test
    fun extraeDensidadAcidezYTemperaturaDeLasLineasReconocidas() {
        val borrador = ResultadoLaboratorioOcrParser.parsear(
            listOf("Resultado de laboratorio", "Densidad: 1.030", "Acidez: 16.5", "Temperatura: 4.2 C", "Conforme"),
        )
        assertEquals(1.030, borrador.densidad)
        assertEquals(16.5, borrador.acidez)
        assertEquals(4.2, borrador.temperatura)
        assertEquals(ResultadoTipo.CONFORME, borrador.resultado)
    }

    @Test
    fun extraeElAnalisisCompletoDelTicketDelAnalizador() {
        val borrador = ResultadoLaboratorioOcrParser.parsear(
            listOf("FAT 3.52", "SNF 8,61", "DEN 29.4", "PRO 3.15", "LAC 4.72", "SALT 0.70", "TS 12.13", "W 0.0", "FP -0.531", "T 18.2", "pH 6.68"),
        )
        assertEquals(3.52, borrador.grasa)
        assertEquals(8.61, borrador.sng)
        assertEquals(29.4, borrador.densidad)
        assertEquals(3.15, borrador.proteina)
        assertEquals(4.72, borrador.lactosa)
        assertEquals(0.70, borrador.sales)
        assertEquals(12.13, borrador.totalSolidos)
        assertEquals(0.0, borrador.aguaAnadida)
        assertEquals(-0.531, borrador.puntoCongelacion)
        assertEquals(18.2, borrador.temperatura)
        assertEquals(6.68, borrador.ph)
    }

    @Test
    fun leeElTicketRealDelSmartScan() {
        // Texto tal como sale en la foto de ejemplo (Ejemplo/resultados del Laboratorio.jpeg).
        val borrador = ResultadoLaboratorioOcrParser.parsear(
            listOf(
                "Manuel", "Analiz. de Leche SmartScan AI", "SN: 48359 Mode: 1", "Temp. 15.9 C",
                "Grasa............ 4.5%", "SNG.............. 8.7%", "Densidad.........31.5", "Proteina......... 3.4%",
                "Lactosa.......... 4.7%", "Sales............ 0.7%", "Total solidos.....13.2%", "Agua anadida...... 0.0%",
                "Punto cong......-0.551 C", "pH..............11.0", "09:42 06/06/26",
            ),
        )
        assertEquals("48359", borrador.sn)
        assertEquals("1", borrador.modo)
        assertEquals(15.9, borrador.temperatura)
        assertEquals(4.5, borrador.grasa)
        assertEquals(8.7, borrador.sng)
        assertEquals(31.5, borrador.densidad)
        assertEquals(3.4, borrador.proteina)
        assertEquals(4.7, borrador.lactosa)
        assertEquals(0.7, borrador.sales)
        assertEquals(13.2, borrador.totalSolidos)
        assertEquals(0.0, borrador.aguaAnadida)
        assertEquals(-0.551, borrador.puntoCongelacion)
        assertEquals(11.0, borrador.ph)
        assertEquals("09:42", borrador.hora)
        assertEquals("06/06/26", borrador.fecha)
    }

    @Test
    fun reconoceResultadoNoConforme() {
        assertEquals(ResultadoTipo.NO_CONFORME, ResultadoLaboratorioOcrParser.parsear(listOf("Resultado: no conforme")).resultado)
    }

    @Test
    fun reconoceResultadoAcidificadoPorPalabraClave() {
        val borrador = ResultadoLaboratorioOcrParser.parsear(listOf("Leche acidificada detectada"))
        assertEquals(ResultadoTipo.ACIDIFICADO, borrador.resultado)
    }

    @Test
    fun devuelveCamposNulosCuandoNoHayCoincidencias() {
        val borrador = ResultadoLaboratorioOcrParser.parsear(listOf("Texto sin datos reconocibles"))
        assertNull(borrador.densidad)
        assertNull(borrador.acidez)
        assertNull(borrador.temperatura)
        assertNull(borrador.resultado)
    }
}
