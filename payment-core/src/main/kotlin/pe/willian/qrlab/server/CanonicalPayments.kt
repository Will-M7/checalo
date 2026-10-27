package pe.willian.qrlab.server

import kotlinx.serialization.Serializable
import pe.willian.qrlab.bridge.SecurityCodeMatcher
import pe.willian.qrlab.payment.*
import pe.willian.qrlab.yape.YapeNotificationParser
import java.sql.Connection
import java.time.Instant
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class CanonicalCreate(val reference: String, val amountMinor: Long, val receiverId: String,
                           val subject: String, val currency: String = "604", val method: String = "YAPE_NOTIFICATION")
@Serializable
data class CanonicalView(val id: String, val reference: String, val amountMinor: Long, val currency: String,
                         val receiverId: String, val status: String, val expiresAt: String,
                         val qrSha256: String, val evidenceId: String? = null, val result: String = "WAITING_PAYMENT")
@Serializable
data class CanonicalRaw(val bridgeEventId: String, val receiverId: String, val packageName: String,
                        val postedAt: String, val title: String? = null, val text: String? = null,
                        val bigText: String? = null, val subText: String? = null, val summaryText: String? = null,
                        val securityCode: String? = null, val authorizationGrant: String? = null)
@Serializable
data class CanonicalAck(val accepted: Boolean = true, val eventId: String, val result: String, val reviewRequired:Boolean = false)
@Serializable
data class ReceiverView(val id: String, val name: String, val phone: String, val qrSha256: String,
                        val verified: Boolean, val enabled: Boolean, val online: Boolean, val version: Long = 0,
                        val deviceLinked: Boolean = false, val verifiedBy: String? = null, val verifiedAt: String? = null)
@Serializable
data class BridgeHeartbeat(val listenerConnected: Boolean)
class AttemptLimit : RuntimeException()

/** Canonical PaymentIntent authority. All evidence and financial transitions share one SQLite transaction. */
class CanonicalPayments(private val repo: SqlitePaymentRepository, val config: CommercialConfig,
                        private val clock: () -> Instant = Instant::now) {
    init {
        config.validate()
        repo.atomic { db ->
            listOf(
                "CREATE TABLE IF NOT EXISTS canonical_bindings(payment_id TEXT PRIMARY KEY REFERENCES payment_intents(id), client_id TEXT NOT NULL, reference TEXT NOT NULL, subject TEXT NOT NULL, receiver_id TEXT NOT NULL, qr_sha256 TEXT NOT NULL, claim_hmac TEXT, UNIQUE(client_id,reference))",
                "CREATE TABLE IF NOT EXISTS canonical_evidence(id TEXT PRIMARY KEY, device_id TEXT NOT NULL, receiver_id TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL, amount_minor INTEGER NOT NULL, code_hmac TEXT, posted_at TEXT NOT NULL, received_at TEXT NOT NULL, result TEXT NOT NULL, payment_id TEXT UNIQUE)",
                "CREATE TABLE IF NOT EXISTS canonical_attempts(key TEXT PRIMARY KEY, started INTEGER NOT NULL, attempts INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS canonical_devices(id TEXT PRIMARY KEY, seen INTEGER NOT NULL, listener INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS canonical_evidence_match ON canonical_evidence(receiver_id,amount_minor,code_hmac,payment_id)",
            ).forEach { db.createStatement().use { s -> s.execute(it) } }
            if (db.rows("PRAGMA table_info(canonical_devices)").none { it["name"]=="receiver_id" })
                db.exec("ALTER TABLE canonical_devices ADD COLUMN receiver_id TEXT NOT NULL DEFAULT ''")
            check(db.rows("SELECT id FROM payment_intents WHERE id NOT IN (SELECT payment_id FROM canonical_bindings) LIMIT 1").isEmpty()) { "Use a separate empty commercial database; existing LAB data cannot be adopted" }
        }
    }
    val registry = CommercialRegistry(repo,config,clock)
    val receiverAdministration = ReceiverAdministration(repo,config,clock,registry)
    fun heartbeat(device: PaymentDevice, connected: Boolean) = repo.atomic { db ->
        db.exec("INSERT INTO canonical_devices(id,seen,listener,receiver_id) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET seen=excluded.seen,listener=excluded.listener,receiver_id=excluded.receiver_id",
            device.id, clock().epochSecond, if (connected) 1 else 0, device.receiverId)
    }
    fun receivers(client: PaymentClient): List<ReceiverView> = repo.atomic { db ->
        registry.receivers(db).filter { registry.allowed(db,client,it.id) }.map { receiverAdministration.view(db,it) }
    }
    fun create(client: PaymentClient, request: CanonicalCreate): CanonicalView {
        require(request.reference.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        require(request.subject.matches(Regex("[a-zA-Z0-9:_-]{1,100}")))
        require(request.amountMinor in 1..99_999_999 && request.currency == "604" && request.method == "YAPE_NOTIFICATION")
        // Idempotent recovery must remain possible even if the receiver subsequently goes offline.
        return repo.atomic { db ->
            require(registry.allowed(db,client,request.receiverId))
            val previous = db.rows("SELECT payment_id FROM canonical_bindings WHERE client_id=? AND reference=?", client.id, request.reference).singleOrNull()
            if (previous != null) {
                val p = repo.findById(previous["payment_id"]!!)!!
                val binding = binding(db,client,p.id)
                if (p.amountMinor != request.amountMinor || p.currency != request.currency || binding["receiver_id"] != request.receiverId || binding["subject"] != request.subject)
                    throw PaymentIdempotencyConflictException("Reference already belongs to another immutable payment")
                return@atomic view(db,client,p.id)
            }
            val ready=registry.receivers(db).filter { it.id==request.receiverId }.map { receiverAdministration.view(db,it) }.any { it.enabled && it.verified && it.online }
            check(ready) { "Receiver unavailable or not physically verified" }
            val now = clock()
            val receiver = registry.receiver(db,request.receiverId)
            val p = PaymentIntent("PAY-${UUID.randomUUID()}", "${client.id}:${request.reference}", request.amountMinor,
                request.currency, PaymentStatus.PENDING, null, now, now.plusSeconds(config.ttlSeconds), null, null, PaymentMethod.YAPE_NOTIFICATION)
            repo.save(p)
            db.exec("INSERT INTO canonical_bindings(payment_id,client_id,reference,subject,receiver_id,qr_sha256) VALUES(?,?,?,?,?,?)",
                p.id,client.id,request.reference,request.subject,request.receiverId,receiver.qrSha256)
            view(db,client,p.id)
        }
    }
    fun get(client: PaymentClient,id: String): CanonicalView = repo.atomic { db -> binding(db,client,id); reconcile(db); view(db,client,id) }
    fun cancel(client: PaymentClient,id: String): CanonicalView = repo.atomic { db ->
        binding(db,client,id)
        val p = repo.findById(id) ?: throw NoSuchElementException()
        if (p.status == PaymentStatus.PENDING) repo.save(p.copy(status=PaymentStatus.CANCELLED))
        else check(p.status == PaymentStatus.CANCELLED) { "Terminal payment cannot be cancelled" }
        view(db,client,id)
    }
    fun resolve(client: PaymentClient,id: String,code: String): CanonicalView {
        require(SecurityCodeMatcher.isValidFormat(code)) { "securityCode must contain exactly three digits" }
        var limited = false
        val result = repo.atomic { db ->
            val b = binding(db,client,id)
            val keys = listOf("payment:$id", "subject:${client.id}:${b["subject"]}", "receiver:${b["receiver_id"]}")
            val now=clock().epochSecond
            limited = keys.any { key -> db.rows("SELECT * FROM canonical_attempts WHERE key=?",key).any { now-it["started"]!!.toLong() < 60 && it["attempts"]!!.toInt() >= 5 } }
            if (limited) return@atomic view(db,client,id)
            keys.forEach { key ->
                db.exec("INSERT INTO canonical_attempts VALUES(?,?,1) ON CONFLICT(key) DO UPDATE SET attempts=CASE WHEN ?-started>=60 THEN 1 ELSE attempts+1 END,started=CASE WHEN ?-started>=60 THEN ? ELSE started END",key,now,now,now,now)
            }
            val p=repo.findById(id)!!
            if (p.status == PaymentStatus.PENDING) db.exec("UPDATE canonical_bindings SET claim_hmac=? WHERE payment_id=?", SecurityCodeMatcher.hmacHex(code,config.matchSecret),id)
            reconcile(db)
            view(db,client,id)
        }
        if (limited) throw AttemptLimit()
        return result
    }
    fun receive(device: PaymentDevice,raw: CanonicalRaw): CanonicalAck {
        require(raw.bridgeEventId.matches(Regex("[a-zA-Z0-9_-]{16,128}")))
        require(device.receiverId == raw.receiverId) { "Wrong receiver" }
        require(raw.packageName == device.packageName) { "Wrong source application" }
        require(listOf(raw.title,raw.text,raw.bigText,raw.subText,raw.summaryText).sumOf { it?.length ?: 0 } <= 8192)
        val texts=YapeNotificationParser.NotificationTexts(raw.title,raw.text,raw.bigText,raw.subText,raw.summaryText)
        val combined=texts.asList().filterNotNull().joinToString("\n")
        val incoming=Regex("Yape!\\s+.+?\\s+te envi[oó] un pago por\\s+S/",RegexOption.IGNORE_CASE).containsMatchIn(combined)
        val parsed=YapeNotificationParser.parse(texts,securityCodeOverride=raw.securityCode)
        val codes=(texts.asList().filterNotNull().flatMap { field -> YapeNotificationParser.DEFAULT_SECURITY_CODE_PATTERN.findAll(field).map { it.groupValues[1] }.toList() } + listOfNotNull(raw.securityCode?.takeIf(SecurityCodeMatcher::isValidFormat))).distinct()
        val names=YapeNotificationParser.DEFAULT_PAYER_PATTERN.findAll(combined).map { it.groupValues[1].trim().lowercase() }.distinct().toList()
        val ambiguousFormat=codes.size>1 || names.size>1
        val amounts=parsed.amounts.distinct()
        val recognized=incoming && amounts.size==1 && amounts.single()>0 && parsed.payerName != null
        val posted=Instant.parse(raw.postedAt)
        require(!posted.isAfter(clock().plusSeconds(15))) { "Future event rejected" }
        val amount=amounts.singleOrNull()?.takeIf { it>0 } ?: 0L
        val fingerprint=if(recognized) sha256("${raw.receiverId}|${raw.packageName}|$posted|${parsed.payerName!!.trim().lowercase()}|$amount")
            else sha256("${raw.receiverId}|${raw.packageName}|$posted|review|${sha256(combined)}")
        val hmac=codes.singleOrNull()?.let { SecurityCodeMatcher.hmacHex(it,config.matchSecret) }
        return repo.atomic { db ->
            val existing=db.rows("SELECT * FROM canonical_evidence WHERE id=? OR fingerprint=?",raw.bridgeEventId,fingerprint)
            if (existing.isNotEmpty()) {
                val e=existing.first()
                check(e["receiver_id"]==raw.receiverId && e["fingerprint"]==fingerprint && e["code_hmac"]==hmac) { "Event identity conflict" }
                return@atomic CanonicalAck(eventId=raw.bridgeEventId,result="DUPLICATE",reviewRequired=e["result"]?.startsWith("REVIEW_")==true)
            }
            registry.consumeGrant(db,device,raw)
            val effective=receiverAdministration.view(db,registry.receiver(db,device.receiverId))
            val result=if (!effective.enabled || !effective.verified) "REVIEW_RECEIVER_UNVERIFIED" else if (!recognized) "REVIEW_FORMAT_UNRECOGNIZED" else if (ambiguousFormat) "REVIEW_AMBIGUOUS" else if (hmac==null) "REVIEW_CODE_UNAVAILABLE" else if(clock().epochSecond-posted.epochSecond>config.ttlSeconds) "REVIEW_LATE" else "OBSERVED"
            db.exec("INSERT INTO canonical_evidence VALUES(?,?,?,?,?,?,?,?,?,NULL)",raw.bridgeEventId,device.id,raw.receiverId,fingerprint,amount,hmac,posted.toString(),clock().toString(),result)
            // Replayed outbox evidence does not prove that notification permission is currently active.
            reconcile(db)
            CanonicalAck(eventId=raw.bridgeEventId,result=result,reviewRequired=result.startsWith("REVIEW_"))
        }
    }
    private fun binding(db: Connection,client: PaymentClient,id: String): Map<String,String?> =
        db.rows("SELECT * FROM canonical_bindings WHERE payment_id=? AND client_id=?",id,client.id).singleOrNull()
            ?: throw NoSuchElementException("Payment not found")
    private fun reconcile(db: Connection) {
        val now=clock()
        repo.findAll().filter { it.paymentMethod==PaymentMethod.YAPE_NOTIFICATION && it.status==PaymentStatus.PENDING && it.expiresAt!=null && !now.isBefore(it.expiresAt) }
            .forEach { p -> if(db.rows("SELECT payment_id FROM canonical_bindings WHERE payment_id=?",p.id).isNotEmpty()) repo.save(p.copy(status=PaymentStatus.EXPIRED)) }
        val claims=db.rows("SELECT * FROM canonical_bindings WHERE claim_hmac IS NOT NULL")
        val pending=claims.mapNotNull { b -> repo.findById(b["payment_id"]!!)?.takeIf { it.status==PaymentStatus.PENDING }?.let { b to it } }
        for ((b,p) in pending) {
            val candidates=db.rows("SELECT * FROM canonical_evidence WHERE receiver_id=? AND amount_minor=? AND code_hmac=? AND payment_id IS NULL AND result='OBSERVED'",b["receiver_id"],p.amountMinor,b["claim_hmac"])
                .filter { registry.enabled(db,it["device_id"]!!) && Instant.parse(it["posted_at"]) >= p.createdAt && Instant.parse(it["posted_at"]) <= p.expiresAt!! }
            if(candidates.size>1) {
                candidates.forEach { e -> db.exec("UPDATE canonical_evidence SET result='REVIEW_AMBIGUOUS' WHERE id=? AND payment_id IS NULL",e["id"]) }
                continue
            }
            if(candidates.isEmpty()) continue
            val e=candidates.single()
            val competing=pending.count { (other,op) -> other["receiver_id"]==b["receiver_id"] && other["claim_hmac"]==b["claim_hmac"] && op.amountMinor==p.amountMinor && Instant.parse(e["posted_at"]) >= op.createdAt && Instant.parse(e["posted_at"]) <= op.expiresAt!! }
            if(competing!=1) {
                db.exec("UPDATE canonical_evidence SET result='REVIEW_AMBIGUOUS' WHERE id=? AND payment_id IS NULL",e["id"])
                continue
            }
            db.exec("UPDATE canonical_evidence SET payment_id=?,result='MATCHED' WHERE id=? AND payment_id IS NULL",p.id,e["id"])
            PaymentStateMachine.validate(p.status,PaymentStatus.PAID)
            repo.save(p.copy(status=PaymentStatus.PAID,paidAt=now,providerOperationReference="BRIDGE-EVENT-${e["id"]}"))
        }
    }
    private fun view(db: Connection,client: PaymentClient,id: String): CanonicalView {
        val b=binding(db,client,id); val p=repo.findById(id)!!
        val event=db.rows("SELECT id FROM canonical_evidence WHERE payment_id=?",id).singleOrNull()?.get("id")
        val n=if(b["claim_hmac"]==null) 0 else db.rows("SELECT id FROM canonical_evidence WHERE receiver_id=? AND amount_minor=? AND code_hmac=? AND payment_id IS NULL AND result='OBSERVED'",b["receiver_id"],p.amountMinor,b["claim_hmac"]).size
        val observed=db.rows("SELECT * FROM canonical_evidence WHERE receiver_id=? AND amount_minor=? AND payment_id IS NULL AND result IN ('OBSERVED','REVIEW_CODE_UNAVAILABLE','REVIEW_AMBIGUOUS','REVIEW_FORMAT_UNRECOGNIZED')",b["receiver_id"],p.amountMinor)
            .filter { Instant.parse(it["posted_at"]) >= p.createdAt && Instant.parse(it["posted_at"]) <= p.expiresAt!! }
        val competing=if(b["claim_hmac"]==null) 0 else db.rows("SELECT payment_id FROM canonical_bindings WHERE receiver_id=? AND claim_hmac=?",b["receiver_id"],b["claim_hmac"])
            .count { repo.findById(it["payment_id"]!!)?.let { other -> other.status==PaymentStatus.PENDING && other.amountMinor==p.amountMinor } == true }
        val result=when { p.status==PaymentStatus.PAID -> "CONFIRMED"; p.status!=PaymentStatus.PENDING -> p.status.name; n>1 || competing>1 || observed.any { it["result"]=="REVIEW_AMBIGUOUS" && (b["claim_hmac"]==null || it["code_hmac"]==null || it["code_hmac"]==b["claim_hmac"]) } -> "AMBIGUOUS"; observed.any { it["result"]=="REVIEW_FORMAT_UNRECOGNIZED" } -> "REVIEW_FORMAT_UNRECOGNIZED"; observed.any { it["result"]=="REVIEW_CODE_UNAVAILABLE" } -> "REVIEW_CODE_UNAVAILABLE"; b["claim_hmac"]!=null && observed.isNotEmpty() && n==0 -> "CODE_NOT_MATCHED"; b["claim_hmac"]!=null -> "PAYMENT_NOT_FOUND"; else -> "WAITING_PAYMENT" }
        return CanonicalView(id,b["reference"]!!,p.amountMinor,p.currency,b["receiver_id"]!!,p.status.name,p.expiresAt!!.toString(),b["qr_sha256"]!!,event,result)
    }
    companion object { fun sha256(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) } }
}
internal fun Connection.exec(sql:String,vararg args:Any?) = prepareStatement(sql).use { s -> args.forEachIndexed { i,v -> s.setObject(i+1,v) }; s.executeUpdate() }
internal fun Connection.rows(sql:String,vararg args:Any?):List<Map<String,String?>> = prepareStatement(sql).use { s ->
    args.forEachIndexed { i,v -> s.setObject(i+1,v) }
    s.executeQuery().use { r -> buildList { while(r.next()) add((1..r.metaData.columnCount).associate { r.metaData.getColumnName(it) to r.getString(it) }) } }
}
