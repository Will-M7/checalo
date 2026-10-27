package pe.willian.qrlab.server

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.SqlitePaymentRepository
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class ReceiverAdministrationTest {
    @TempDir lateinit var temp: Path
    private var time=Instant.parse("2026-10-27T12:00:00Z")
    private val admin=PaymentClient("huata","a".repeat(40),listOf("r1"),true)
    private val ordinary=PaymentClient("shop","b".repeat(40),listOf("r1"))
    private val device=PaymentDevice("phone","d".repeat(40),"r1")
    private val wrongDevice=PaymentDevice("other_phone","e".repeat(40),"r2")
    private val config=CommercialConfig("s".repeat(40),listOf(admin,ordinary),listOf(PaymentReceiver("r1","Titular","51999999999","a".repeat(64)),PaymentReceiver("r2","Otro","51988888888","b".repeat(64))),listOf(device,wrongDevice),allowLocalHttp=true)
    private fun repo()=SqlitePaymentRepository(temp.resolve("commercial.db"))
    private fun service()=CanonicalPayments(repo(),config) { time }
    private fun cmd(action:String,version:Long=0)=ReceiverCommand(UUID.randomUUID().toString(),version,"staff_15",action,"Titular","999999999","a".repeat(64),action=="verify")
    private fun view(s:CanonicalPayments)=s.receivers(admin).single()
    private fun verify(s:CanonicalPayments)=s.receiverAdministration.execute(admin,"r1",cmd("verify",view(s).version))
    private fun enable(s:CanonicalPayments):ReceiverView {
        s.heartbeat(device,true); verify(s)
        return s.receiverAdministration.execute(admin,"r1",cmd("enable",view(s).version))
    }
    @Test fun `existing receiver remains unverified without money changes`() {
        val s=service();val v=view(s);assertFalse(v.verified);assertFalse(v.enabled);assertFalse(v.online)
        assertTrue(v.deviceLinked);assertTrue(repo().findAll().isEmpty())
    }
    @Test fun `initial physical verification does not require enabled or online`() {
        val s=service(); val v=verify(s);assertTrue(v.verified);assertFalse(v.enabled);assertFalse(v.online)
        assertEquals("huata:staff_15",v.verifiedBy);assertEquals(time.toString(),v.verifiedAt)
        assertEquals("verify",repo().atomic { it.rows("SELECT action FROM canonical_receiver_audit").single()["action"] })
    }
    @Test fun `ordinary application is not an administrator`() {
        val s=service(); assertThrows(ReceiverAdminDenied::class.java) { s.receiverAdministration.execute(ordinary,"r1",cmd("verify")) }
        assertFalse(view(s).verified)
    }
    @Test fun `QR mismatch rejected`() {
        val s=service();assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("verify").copy(qrSha256="b".repeat(64))) };assertFalse(view(s).verified)
    }
    @Test fun `name mismatch rejected`() {
        val s=service();assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("verify").copy(name="Otro")) };assertFalse(view(s).verified)
    }
    @Test fun `phone mismatch rejected`() {
        val s=service();assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("verify").copy(phone="988888888")) };assertFalse(view(s).verified)
    }
    @Test fun `another receiver heartbeat cannot enable receiver`() {
        val s=service();verify(s);s.heartbeat(wrongDevice,true);assertFalse(view(s).online)
        assertEquals("BRIDGE_OFFLINE",assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("enable",1)) }.reason)
        assertThrows(IllegalArgumentException::class.java) { s.receive(wrongDevice,CanonicalRaw("event_wrong_receiver_01","r1",device.packageName,time.toString(),text="Yape! Prueba Uno te envió un pago por S/ 7.00")) }
    }
    @Test fun `offline and inactive listener are never ready`() {
        val s=service();verify(s);s.heartbeat(device,false)
        assertEquals("BRIDGE_OFFLINE",assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("enable",1)) }.reason)
        s.heartbeat(device,true);time=time.plusSeconds(121);assertFalse(view(s).online)
    }
    @Test fun `physical confirmation required despite online device`() {
        val s=service();s.heartbeat(device,true)
        assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("verify").copy(physicalConfirmed=false)) }
        assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("enable")) };assertFalse(view(s).enabled)
    }
    @Test fun `verified online receiver can enable and create pending PAY`() {
        val s=service();val v=enable(s);assertTrue(v.enabled);assertTrue(v.verified)
        val p=s.create(admin,CanonicalCreate("new_order",700,"r1","customer_1"));assertTrue(p.id.startsWith("PAY-"));assertEquals("PENDING",p.status)
    }
    @Test fun `disable is audited and blocks new PAY`() {
        val s=service();enable(s)
        val v=s.receiverAdministration.execute(admin,"r1",cmd("disable",2).copy(qrSha256="wrong"));assertFalse(v.enabled);assertTrue(v.verified)
        assertThrows(IllegalStateException::class.java) { s.create(admin,CanonicalCreate("blocked_order",700,"r1","customer_1")) }
        assertEquals(3,repo().atomic { it.rows("SELECT * FROM canonical_receiver_audit").size })
    }
    @Test fun `restart persists verification enable and disable rather than reread flags`() {
        val s=service();enable(s);assertTrue(view(service()).enabled)
        service().receiverAdministration.execute(admin,"r1",cmd("disable",2));assertFalse(view(service()).enabled);assertTrue(view(service()).verified)
    }
    @Test fun `duplicates idempotent and stale retry cannot restore later disabled state`() {
        val s=service();s.heartbeat(device,true);val c=cmd("verify");s.receiverAdministration.execute(admin,"r1",c);s.receiverAdministration.execute(admin,"r1",c)
        val on=cmd("enable",1);s.receiverAdministration.execute(admin,"r1",on);s.receiverAdministration.execute(admin,"r1",cmd("disable",2))
        assertFalse(s.receiverAdministration.execute(admin,"r1",on).enabled)
        assertEquals(3,repo().atomic { it.rows("SELECT * FROM canonical_receiver_audit").size })
        assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",c.copy(name="Alterado")) }
    }
    @Test fun `intermediate audit failure rolls back state atomically`() {
        val s=service();repo().atomic { it.exec("CREATE TRIGGER reject_audit BEFORE INSERT ON canonical_receiver_audit BEGIN SELECT RAISE(ABORT,'fixture'); END") }
        assertThrows(Exception::class.java) { verify(s) };assertFalse(view(s).verified);assertFalse(view(s).enabled)
    }
    @Test fun `unverified receiver cannot create PAY even with heartbeat`() {
        val s=service();s.heartbeat(device,true);assertThrows(IllegalStateException::class.java) { s.create(admin,CanonicalCreate("blocked",700,"r1","customer_1")) }
    }
    @Test fun `evidence observed before verification is never promoted after enable`() {
        val s=service();val raw=CanonicalRaw("unverified_event_001","r1",device.packageName,time.toString(),text="Yape! Prueba Uno te envió un pago por S/ 7.00",securityCode="381")
        assertEquals("REVIEW_RECEIVER_UNVERIFIED",s.receive(device,raw).result);enable(s)
        val p=s.create(admin,CanonicalCreate("order_after",700,"r1","customer_1"));assertEquals("PENDING",s.resolve(admin,p.id,"381").status)
        assertEquals("DUPLICATE",s.receive(device,raw).result);assertEquals("PENDING",s.get(admin,p.id).status)
    }
    @Test fun `identity change invalidates persisted approval and disabled config cannot override it`() {
        val s=service();enable(s)
        val changed=config.copy(receivers=config.receivers.map { if(it.id=="r1") it.copy(qrSha256="c".repeat(64),verified=true,enabled=true) else it })
        val restarted=CanonicalPayments(repo(),changed) { time };assertFalse(view(restarted).verified);assertFalse(view(restarted).enabled)
    }
    @Test fun `stale version cannot grant access`() {
        val s=service();verify(s);assertThrows(ReceiverAdminConflict::class.java) { s.receiverAdministration.execute(admin,"r1",cmd("enable",0)) };assertFalse(view(s).enabled)
    }
    @Test fun `HTTP admin requires scoped application and rejects device bearer and LAB routes`() = testApplication {
        val s=service();application { commercialModule(s) }
        val path="/api/admin/receivers/r1/state"
        assertEquals(HttpStatusCode.Unauthorized,client.post(path).status)
        assertEquals(HttpStatusCode.Unauthorized,client.post(path) { bearerAuth(device.key) }.status)
        assertEquals(HttpStatusCode.Forbidden,client.post(path) { bearerAuth(ordinary.key) }.status)
        val c=cmd("verify")
        assertEquals(HttpStatusCode.OK,client.post(path) { bearerAuth(admin.key);contentType(ContentType.Application.Json);setBody(kotlinx.serialization.json.Json.encodeToString(c)) }.status)
        assertTrue(view(s).verified);assertFalse(view(s).enabled)
        for(p in listOf("/api/lab/yape/inject","/api/lab/payments/PAY-1/confirm","/api/yape/waits")) assertEquals(HttpStatusCode.NotFound,client.post(p).status)
    }
}
