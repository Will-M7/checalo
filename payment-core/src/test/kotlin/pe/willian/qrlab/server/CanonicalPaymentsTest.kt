package pe.willian.qrlab.server

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.*
import java.nio.file.Path
import java.time.Instant

class CanonicalPaymentsTest {
    @TempDir lateinit var temp: Path
    private var time=Instant.parse("2026-10-26T12:00:00Z")
    private val client=PaymentClient("shop","c".repeat(40),listOf("r1","r2"))
    private val other=PaymentClient("other","o".repeat(40),listOf("r1"))
    private val device=PaymentDevice("device","d".repeat(40),"r1")
    private val config=CommercialConfig("s".repeat(40),listOf(client,other),listOf(
        PaymentReceiver("r1","Receiver","999999999","a".repeat(64),true,true),
        PaymentReceiver("r2","Receiver 2","988888888","b".repeat(64),true,true)
    ),listOf(device,PaymentDevice("d2","e".repeat(40),"r2")),allowLocalHttp=true)
    private fun service()=CanonicalPayments(SqlitePaymentRepository(temp.resolve("commercial.db")),config) { time }
    private fun create(s:CanonicalPayments,ref:String="order_1",amount:Long=700):CanonicalView {
        s.heartbeat(device,true)
        return s.create(client,CanonicalCreate(ref,amount,"r1","customer_1"))
    }
    private fun raw(id:String="event_identity_0001",code:String?="381",amount:String="7.00",receiver:String="r1") = CanonicalRaw(id,receiver,device.packageName,time.toString(),text="Yape! Persona Uno te envió un pago por S/ $amount",securityCode=code)
    @Test fun `creation is idempotent including after restart`() {
        val s=service();val p=create(s);assertEquals(p.id,create(s).id);assertEquals(p.id,create(service()).id)
        assertThrows(PaymentIdempotencyConflictException::class.java) { create(s,amount=800) }
    }
    @Test fun `amount alone never confirms and code alone never confirms`() {
        val s=service();val p=create(s)
        assertEquals("PENDING",s.resolve(client,p.id,"381").status)
        time=time.plusSeconds(1);s.receive(device,raw())
        assertEquals("PAID",s.get(client,p.id).status)
    }
    @Test fun `correct code with no observed incoming payment stays pending`() {
        val s=service();val p=create(s);assertEquals("PENDING",s.resolve(client,p.id,"381").status)
    }
    @Test fun `incoming evidence without submitted code stays pending`() {
        val s=service();val p=create(s);time=time.plusSeconds(1);s.receive(device,raw());assertEquals("PENDING",s.get(client,p.id).status)
    }
    @Test fun `wrong amount or code never pays`() {
        val s=service();val p=create(s);time=time.plusSeconds(1);s.receive(device,raw(amount="8.00"));assertEquals("PENDING",s.resolve(client,p.id,"123").status)
        s.receive(device,raw(id="event_identity_0002"));assertEquals("PENDING",s.resolve(client,p.id,"123").status)
    }
    @Test fun `wrong receiver or package is rejected`() {
        val s=service();create(s);assertThrows(IllegalArgumentException::class.java) { s.receive(device,raw(receiver="r2")) }
        assertThrows(IllegalArgumentException::class.java) { s.receive(device,raw().copy(packageName="fake.app")) }
    }
    @Test fun `unrecognized notification is rejected`() {
        val s=service();create(s);assertEquals("REVIEW_FORMAT_UNRECOGNIZED",s.receive(device,raw().copy(text="Reminder S/ 7.00")).result)
    }
    @Test fun `duplicate callback and retry do not reuse evidence`() {
        val s=service();val p=create(s);time=time.plusSeconds(1);s.receive(device,raw());s.receive(device,raw())
        s.receive(device,raw(id="another_callback_001"));assertEquals("PAID",s.resolve(client,p.id,"381").status)
        val second=create(s,ref="order_2");assertEquals("PENDING",s.resolve(client,second.id,"381").status)
    }
    @Test fun `missing observed code cannot be fabricated by customer`() {
        val s=service();val p=create(s);time=time.plusSeconds(1);s.receive(device,raw(code=null));assertEquals("PENDING",s.resolve(client,p.id,"381").status)
    }
    @Test fun `two observed events with same code are ambiguous`() {
        val s=service();val p=create(s);time=time.plusSeconds(1);s.receive(device,raw());time=time.plusSeconds(1);s.receive(device,raw(id="event_identity_0002"))
        val result=s.resolve(client,p.id,"381");assertEquals("PENDING",result.status);assertEquals("AMBIGUOUS",result.result)
    }
    @Test fun `expiry and late evidence never resurrect payment`() {
        val s=service();val p=create(s);time=time.plusSeconds(301);s.receive(device,raw());assertEquals("EXPIRED",s.resolve(client,p.id,"381").status)
    }
    @Test fun `cancelled intent cannot be paid`() {
        val s=service();val p=create(s);s.cancel(client,p.id);time=time.plusSeconds(1);s.receive(device,raw());assertEquals("CANCELLED",s.resolve(client,p.id,"381").status)
    }
    @Test fun `ownership cannot be changed with id`() {
        val s=service();val p=create(s);assertThrows(NoSuchElementException::class.java) { s.get(other,p.id) }
    }
    @Test fun `five attempts shared across payments and restart`() {
        val s=service();val p=create(s);repeat(5) { s.resolve(client,p.id,"123") }
        val second=create(s,"order_2");assertThrows(AttemptLimit::class.java) { service().resolve(client,second.id,"381") }
        time=time.plusSeconds(61);assertEquals("PENDING",s.resolve(client,p.id,"381").status)
    }
    @Test fun `parallel resolutions produce one persisted application`() = runBlocking {
        val s=service();val p=create(s);time=time.plusSeconds(1);s.receive(device,raw())
        val views=(1..4).map { async(Dispatchers.Default) { s.resolve(client,p.id,"381") } }.awaitAll()
        assertTrue(views.all { it.status=="PAID" });assertEquals("PAID",service().get(client,p.id).status)
    }
    @Test fun `receiver must be enabled and connected`() {
        val s=service();assertThrows(IllegalStateException::class.java) { s.create(client,CanonicalCreate("order_1",700,"r1","customer_1")) }
    }
    @Test fun `http authenticates both principals and excludes lab routes`() = testApplication {
        val s=service();create(s);application { commercialModule(s) }
        assertEquals(HttpStatusCode.Unauthorized,this.client.get("/api/receivers").status)
        assertEquals(HttpStatusCode.OK,this.client.get("/api/receivers") { bearerAuth(this@CanonicalPaymentsTest.client.key) }.status)
        assertEquals(HttpStatusCode.Unauthorized,this.client.post("/api/bridge/notifications") { contentType(ContentType.Application.Json);setBody("{}") }.status)
        for(path in listOf("/demo","/yape-test","/api/yape/waits","/api/lab/yape/inject","/api/lab/payments/PAY-1/confirm")) {
            assertEquals(HttpStatusCode.NotFound,this.client.post(path).status)
        }
    }
    @Test fun `synthetic simulator rejects Yape intents`() {
        val repo=SqlitePaymentRepository(temp.resolve("sim.db"));val p=PaymentIntent("PAY-x","r",700,"604",PaymentStatus.PENDING,null,time,null,null,null,PaymentMethod.YAPE_NOTIFICATION)
        repo.save(p);assertThrows(IllegalStateException::class.java) { pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator(repo).confirm(p.id) }
    }

    @Test fun `commercial and lab storage cannot be interchanged`() {
        val s=service();create(s)
        assertThrows(IllegalStateException::class.java) { SqlitePaymentRepository(temp.resolve("commercial.db")).use { it.requireLaboratoryStorage() } }
        val lab=temp.resolve("lab.db")
        SqlitePaymentRepository(lab).use { it.save(PaymentIntent("PAY-lab","lab",700,"604",PaymentStatus.PENDING,null,time,null,null,null)) }
        assertThrows(IllegalStateException::class.java) { CanonicalPayments(SqlitePaymentRepository(lab),config) { time } }
    }
    @Test fun `event and paid transition roll back together if storage fails`() {
        val repo=SqlitePaymentRepository(temp.resolve("commercial.db"));val s=CanonicalPayments(repo,config) { time };val p=create(s)
        s.resolve(client,p.id,"381")
        repo.atomic { db -> db.exec("CREATE TRIGGER injected_failure BEFORE UPDATE ON payment_intents WHEN NEW.status='PAID' BEGIN SELECT RAISE(ABORT,'injected test failure'); END") }
        time=time.plusSeconds(1)
        assertThrows(java.sql.SQLException::class.java) { s.receive(device,raw()) }
        repo.atomic { db ->
            assertEquals("0",db.rows("SELECT COUNT(*) AS n FROM canonical_evidence").single()["n"])
            db.exec("DROP TRIGGER injected_failure")
        }
        assertEquals("PENDING",s.get(client,p.id).status)
        s.receive(device,raw());assertEquals("PAID",s.get(client,p.id).status)
    }
    @Test fun `unverified receiver evidence stays quarantined`() {
        val s=CanonicalPayments(SqlitePaymentRepository(temp.resolve("disabled.db")),config.copy(receivers=config.receivers.map { it.copy(enabled=false,verified=false) })) { time }
        assertEquals("REVIEW_RECEIVER_UNVERIFIED",s.receive(device,raw()).result)
        s.heartbeat(device,true)
        assertThrows(IllegalStateException::class.java) { s.create(client,CanonicalCreate("order_1",700,"r1","customer_1")) }
    }
    @Test fun `different buyers with equal amounts require their distinct observed code`() {
        val s=service();val a=create(s);val b=s.create(client,CanonicalCreate("order_2",700,"r1","customer_2"))
        time=time.plusSeconds(1);s.receive(device,raw());s.receive(device,raw(id="event_identity_0002",code="492").copy(text="Yape! Persona Dos te envió un pago por S/ 7.00"))
        assertEquals("PAID",s.resolve(client,b.id,"492").status)
        assertEquals("PENDING",s.get(client,a.id).status)
        assertEquals("PAID",s.resolve(client,a.id,"381").status)
        assertNotEquals(s.get(client,a.id).evidenceId,s.get(client,b.id).evidenceId)
    }
    @Test fun `competing same code claims stay ambiguous until reviewed`() {
        val s=service();val a=create(s);val b=create(s,"order_2")
        s.resolve(client,a.id,"381");s.resolve(client,b.id,"381")
        time=time.plusSeconds(1);s.receive(device,raw())
        assertEquals("AMBIGUOUS",s.get(client,a.id).result);assertEquals("PENDING",s.get(client,b.id).status)
    }
    @Test fun `one account cannot be aliased as two independent receivers`() {
        assertThrows(IllegalArgumentException::class.java) { config.copy(receivers=config.receivers+config.receivers.first().copy(id="alias",phone="51"+config.receivers.first().phone)).validate() }
    }
    @Test fun `ambiguous evidence remains quarantined after competing intent is cancelled`() {
        val s=service();val a=create(s);val b=create(s,"order_2")
        s.resolve(client,a.id,"381");s.resolve(client,b.id,"381");time=time.plusSeconds(1);s.receive(device,raw())
        s.cancel(client,b.id)
        assertEquals("PENDING",s.get(client,a.id).status)
        assertEquals("AMBIGUOUS",service().get(client,a.id).result)
    }
    @Test fun `conflicting observed codes or aggregated payers require review`() {
        val s=service();val p=create(s);time=time.plusSeconds(1)
        s.receive(device,raw().copy(title="Código: 381",bigText="Código: 492"))
        assertEquals("PENDING",s.resolve(client,p.id,"381").status)
        assertEquals("AMBIGUOUS",s.get(client,p.id).result)
        val second=create(s,"order_2",800);time=time.plusSeconds(1)
        s.receive(device,raw(id="aggregated_event_0001",amount="8.00").copy(bigText="Yape! Persona Dos te envió un pago por S/ 8.00"))
        assertEquals("PENDING",s.resolve(client,second.id,"381").status)
        assertEquals("AMBIGUOUS",s.get(client,second.id).result)
    }
    @Test fun `application and device credentials cannot impersonate each other`() = testApplication {
        val s=service();application { commercialModule(s) }
        assertEquals(HttpStatusCode.Unauthorized,this.client.post("/api/bridge/notifications") { bearerAuth(this@CanonicalPaymentsTest.client.key);contentType(ContentType.Application.Json);setBody("{}") }.status)
        assertEquals(HttpStatusCode.Unauthorized,this.client.get("/api/receivers") { bearerAuth(device.key) }.status)
        assertEquals(HttpStatusCode.Unauthorized,this.client.get("/api/receivers") { bearerAuth("unknown-credential") }.status)
    }
    @Test fun `plain HTTP and forged forwarded TLS headers are rejected by default`() = testApplication {
        val s=CanonicalPayments(SqlitePaymentRepository(temp.resolve("secure.db")),config.copy(allowLocalHttp=false)) { time }
        application { commercialModule(s) }
        assertEquals(HttpStatusCode.Forbidden,this.client.get("/api/receivers") { bearerAuth(this@CanonicalPaymentsTest.client.key);header("X-Forwarded-Proto","https") }.status)
    }
    @Test fun `outbox replay cannot reactivate a disconnected notification listener`() {
        val s=service();create(s);s.heartbeat(device,false);time=time.plusSeconds(1)
        s.receive(device,raw())
        assertFalse(s.receivers(client).single { it.id=="r1" }.online)
        assertThrows(IllegalStateException::class.java) { createWithoutHeartbeat(s) }
    }
    private fun createWithoutHeartbeat(s:CanonicalPayments)=s.create(client,CanonicalCreate("another_reference",700,"r1","customer_1"))
    @Test fun `observed real confirmation format without code is retained for review and cannot pay`() {
        val s=service(); val p=create(s,amount=100)
        val event=raw().copy(title="Confirmación de Pago",text="Remitente indicado por Yape · S/ 1.00",securityCode=null)
        assertEquals("REVIEW_FORMAT_UNRECOGNIZED",s.receive(device,event).result)
        assertEquals("PENDING",s.resolve(client,p.id,"123").status)
        assertEquals("DUPLICATE",s.receive(device,event).result)
    }
}
