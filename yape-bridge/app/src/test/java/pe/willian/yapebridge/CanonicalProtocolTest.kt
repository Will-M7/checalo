package pe.willian.yapebridge

import org.junit.Assert.*
import org.junit.Test
import pe.willian.yapebridge.bridge.CanonicalProtocol
import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.store.BridgeOutboxEvent

class CanonicalProtocolTest {
    @Test fun `only recognized incoming Yape notifications are forwarded`() {
        assertTrue(CanonicalProtocol.incoming(CanonicalProtocol.EXPECTED_PACKAGE,listOf("Yape! Persona Uno te envió un pago por S/ 7.00")))
        assertFalse(CanonicalProtocol.incoming("fake.app",listOf("Yape! Persona Uno te envió un pago por S/ 7.00")))
        assertFalse(CanonicalProtocol.incoming(CanonicalProtocol.EXPECTED_PACKAGE,listOf("Recordatorio S/ 7.00")))
    }
    @Test fun `callback identity is stable but receiver isolated`() {
        val n=CapturedNotification(packageName=CanonicalProtocol.EXPECTED_PACKAGE,notificationKey="key",postedAt=1720000000000,title=null,text=null,bigText=null,subText=null,summaryText=null,extras=emptyMap())
        assertEquals(CanonicalProtocol.eventId("r1",n),CanonicalProtocol.eventId("r1",n))
        assertNotEquals(CanonicalProtocol.eventId("r1",n),CanonicalProtocol.eventId("r2",n))
    }
    @Test fun `canonical payload binds receiver and uses ISO time`() {
        val e=BridgeOutboxEvent("event",1,CanonicalProtocol.EXPECTED_PACKAGE,1720000000000,null,"text",null,null,null,receiverId="r1",securityCode="381")
        val json=CanonicalProtocol.json(e)
        assertTrue(json.contains("\"receiverId\":\"r1\""));assertTrue(json.contains("\"postedAt\":\"2024-"))
        assertFalse(json.contains("extras"));assertFalse(json.contains("notificationKey"))
    }
    @Test fun `transport refuses insecure remote URLs and embedded credentials`() {
        assertNull(CanonicalProtocol.secureUrl("http://192.168.1.1:8080"));assertNull(CanonicalProtocol.secureUrl("https://user:secret@example.com"))
        assertNotNull(CanonicalProtocol.secureUrl("https://example.com"));assertNull(CanonicalProtocol.secureUrl("http://127.0.0.1:8087"))
    }
    @org.junit.Test fun observedPaymentWithoutCodeIsCapturedFromRealPackage() {
        assertTrue(CanonicalProtocol.capture("com.bcp.innovacxion.yapeapp",listOf("Confirmación de Pago","Remitente indicado por Yape · S/ 1.00")))
        assertFalse(CanonicalProtocol.capture("com.bcp.innovacion.yapeapp",listOf("Confirmación de Pago","S/ 1.00")))
        assertFalse(CanonicalProtocol.capture("fake.app",listOf("Confirmación de Pago","S/ 1.00")))
        assertTrue(CanonicalProtocol.capture(CanonicalProtocol.EXPECTED_PACKAGE,listOf(null,null,"S/ 1.00",null)))
        assertFalse(CanonicalProtocol.capture(CanonicalProtocol.EXPECTED_PACKAGE,listOf(null,"")))
    }
}
