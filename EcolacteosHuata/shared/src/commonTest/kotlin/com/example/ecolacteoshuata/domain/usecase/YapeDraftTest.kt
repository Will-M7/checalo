package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class YapeDraftTest {
    @Test fun pendingBasketRoundTripsWithoutFinancialReceiptOrCode() {
        val value=BorradorVentaYape(listOf(CarritoItem(Producto("p1","Queso","",7.0,true,10.0),2)),null,"Cliente de prueba","",listOf(Descuento("d1","Promoción",5.0,true)))
        val encoded=Json.encodeToString(value)
        assertEquals(value,Json.decodeFromString<BorradorVentaYape>(encoded))
        for(field in listOf("numeroBoleta","paidAt","securityCode","codigoYape")) assertFalse(encoded.contains(field))
        assertEquals(14.0,value.items.sumOf { it.subtotal })
    }
}
