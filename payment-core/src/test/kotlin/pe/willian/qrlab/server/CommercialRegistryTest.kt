package pe.willian.qrlab.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.SqlitePaymentRepository
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class CommercialRegistryTest {
    @TempDir lateinit var temp:Path
    private var time=Instant.parse("2026-10-27T15:00:00Z")
    private val client=PaymentClient("shop","a".repeat(40),listOf("r1"),true)
    private val other=PaymentClient("other","b".repeat(40),listOf("r1"))
    private val config=CommercialConfig("s".repeat(40),listOf(client,other),listOf(PaymentReceiver("r1","Existing","51999999999","a".repeat(64))),emptyList(),allowLocalHttp=true)
    private fun service()=CanonicalPayments(SqlitePaymentRepository(temp.resolve("commercial.db")),config) { time }
    private fun op()=UUID.randomUUID().toString()
    private fun register(s:CanonicalPayments,mode:String="personal"):PaymentDevice {
        s.registry.registerReceiver(client,RegisterReceiver(op(),"staff_15","r2","New account","51988888888","b".repeat(64)))
        s.registry.registerDevice(client,RegisterDevice(op(),"staff_15","phone2","r2","k".repeat(40),mode,"staff_15"))
        return s.registry.device("k".repeat(40))!!
    }
    private fun enable(s:CanonicalPayments,d:PaymentDevice) {
        s.receiverAdministration.execute(client,"r2",ReceiverCommand(op(),0,"staff_15","verify","New account","51988888888","b".repeat(64),true))
        s.heartbeat(d,true)
        s.receiverAdministration.execute(client,"r2",ReceiverCommand(op(),1,"staff_15","enable","New account","51988888888","b".repeat(64)))
    }
    private fun raw()=CanonicalRaw("real_yape_event_001","r2","com.bcp.innovacxion.yapeapp",time.toString(),text="Yape! Persona Prueba te envió un pago por S/ 1.00",securityCode="123")
    @Test fun `registered account persists closed and cannot escape owning client`() {
        val s=service();register(s)
        assertFalse(s.receivers(client).single { it.id=="r2" }.verified)
        assertFalse(service().receivers(client).single { it.id=="r2" }.enabled)
        assertTrue(s.receivers(other).none { it.id=="r2" })
        assertThrows(ReceiverAdminDenied::class.java) { s.registry.registerReceiver(other,RegisterReceiver(op(),"staff_15","r3","Other","51977777777","c".repeat(64))) }
    }
    @Test fun `personal raw cannot bypass server session grant even with correct device key`() {
        val s=service();val d=register(s);enable(s,d)
        assertThrows(ReceiverAdminDenied::class.java) { s.receive(d,raw()) }
        val grant=s.registry.grant(client,d.id,GrantRequest("staff_15",raw().bridgeEventId))
        assertEquals("OBSERVED",s.receive(d,raw().copy(authorizationGrant=grant.token)).result)
        assertEquals("DUPLICATE",s.receive(d,raw()).result)
    }
    @Test fun `grant cannot authorize another event another owner or expired event`() {
        val s=service();val d=register(s);enable(s,d)
        assertThrows(ReceiverAdminDenied::class.java) { s.registry.grant(client,d.id,GrantRequest("staff_99",raw().bridgeEventId)) }
        val g=s.registry.grant(client,d.id,GrantRequest("staff_15",raw().bridgeEventId))
        assertThrows(ReceiverAdminDenied::class.java) { s.receive(d,raw().copy(bridgeEventId="different_event_001",authorizationGrant=g.token)) }
        time=time.plusSeconds(31)
        assertThrows(ReceiverAdminDenied::class.java) { s.receive(d,raw().copy(authorizationGrant=g.token)) }
    }
    @Test fun `suspension blocks device authentication and quarantines unbound evidence`() {
        val s=service();val d=register(s);enable(s,d)
        val p=s.create(client,CanonicalCreate("order",100,"r2","customer_1"))
        val g=s.registry.grant(client,d.id,GrantRequest("staff_15",raw().bridgeEventId))
        s.receive(d,raw().copy(authorizationGrant=g.token))
        s.registry.changeDevice(client,d.id,ChangeDevice(op(),"staff_15",false))
        assertNull(s.registry.device(d.key));assertEquals("PENDING",s.resolve(client,p.id,"123").status)
        assertFalse(service().receivers(client).single { it.id=="r2" }.online)
    }
    @Test fun `shared receiver does not require vendor session and never reuses evidence`() {
        val s=service();val d=register(s,"shared");enable(s,d)
        val p=s.create(client,CanonicalCreate("first_seller",100,"r2","staff_91"))
        s.receive(d,raw());assertEquals("PAID",s.resolve(client,p.id,"123").status)
        val second=s.create(client,CanonicalCreate("second_seller",100,"r2","staff_92"))
        assertEquals("PENDING",s.resolve(client,second.id,"123").status)
    }
    @Test fun `rotating device credential invalidates static or previous credential without changing receiver`() {
        val s=service();val d=register(s)
        val command=RegisterDevice(op(),"staff_15",d.id,"r2","n".repeat(40),"personal","staff_15")
        s.registry.registerDevice(client,command);s.registry.registerDevice(client,command)
        assertNull(s.registry.device(d.key));assertEquals("r2",service().registry.device("n".repeat(40))!!.receiverId)
    }
}
