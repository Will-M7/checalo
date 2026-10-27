package pe.willian.yapebridge

import org.junit.Assert.*
import org.junit.Test
import pe.willian.yapebridge.bridge.*
import pe.willian.yapebridge.store.BridgeOutboxEvent

class CanonicalDeliveryTest {
    private val event=BridgeOutboxEvent("stable_event_0000001",1,CanonicalProtocol.EXPECTED_PACKAGE,1720000000000,null,"Yape! Persona te envió un pago por S/ 7.00",null,null,null,receiverId="r1",securityCode="381")
    private val key="k".repeat(40)
    @Test fun `ack must contain exact boolean and matching event id`() {
        for(ack in listOf(DeliveryAck(202,true,"other"),DeliveryAck(202,"true",event.eventId),DeliveryAck(202,false,event.eventId),DeliveryAck(401,true,event.eventId))) {
            var sent=false
            assertFalse(CanonicalDelivery.deliver(event,"r1",key,{},{ack},{sent=true}))
            assertFalse(sent)
        }
    }
    @Test fun `authenticated delivery marks sent only after exact ack`() {
        val calls=mutableListOf<String>()
        assertTrue(CanonicalDelivery.deliver(event,"r1",key,{calls+="attempt:$it"},{calls+="send";DeliveryAck(202,true,event.eventId)},{calls+="sent:$it"}))
        assertEquals(listOf("attempt:${event.eventId}","send","sent:${event.eventId}"),calls)
    }
    @Test fun `network loss keeps payload and reconnect retries identical event`() {
        val payloads=mutableListOf<String>();var attempts=0;var sent=0
        assertFalse(CanonicalDelivery.deliver(event,"r1",key,{attempts++},{payloads+=it;throw java.io.IOException("offline")},{sent++}))
        assertEquals(0,sent)
        assertTrue(CanonicalDelivery.deliver(event,"r1",key,{attempts++},{payloads+=it;DeliveryAck(202,true,event.eventId)},{sent++}))
        assertEquals(2,attempts);assertEquals(1,sent);assertEquals(payloads[0],payloads[1])
    }
    @Test fun `wrong receiver or missing device credential never transmits queued event`() {
        for(pair in listOf("r2" to key,"r1" to "", "r1" to "short")) {
            assertFalse(CanonicalDelivery.deliver(event,pair.first,pair.second,{fail("attempt")},{fail("network");DeliveryAck(0,null,null)},{fail("sent")}))
        }
    }
    @Test fun `duplicate sent callback does not transmit or mark twice`() {
        assertTrue(CanonicalDelivery.deliver(event.copy(status=BridgeOutboxEvent.Status.SENT),"r1",key,{fail("attempt")},{fail("network");DeliveryAck(0,null,null)},{fail("sent")}))
    }
    @Test fun `legacy notification without receiver binding stays quarantined`() {
        assertFalse(CanonicalDelivery.deliver(event.copy(status=BridgeOutboxEvent.Status.LEGACY_REVIEW),"r1",key,{fail("attempt")},{fail("network");DeliveryAck(0,null,null)},{fail("sent")}))
    }
    @Test fun `one rejected event never blocks later durable events`() {
        val sent=mutableListOf<String>()
        val next=event.copy(eventId="next_stable_event_001")
        assertFalse(CanonicalDelivery.drain(listOf(event,next)) { value ->
            if(value.eventId==event.eventId) throw java.io.IOException("not acknowledged")
            sent+=value.eventId;true
        })
        assertEquals(listOf(next.eventId),sent)
    }
}
