package pe.willian.qrlab.bridge

import pe.willian.qrlab.money.MoneyFormatter
import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentMethod
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStateMachine
import pe.willian.qrlab.payment.PaymentStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Publicador mínimo para avisar PAID al EventBus existente (lo provee el server). */
fun interface PaidPublisher {
    suspend fun publishPaid(paymentId: String)
}

/** Vista administrativa/lab de un evento. Nunca expone código ni HMAC ni raw. */
data class BridgeEventView(
    val id: String,
    val amountMinor: Long?,
    val amountDisplay: String?,
    val payerName: String?,
    val receivedAt: String,
    val matchStatus: String,
    val matchedPaymentId: String?,
)

/**
 * Orquesta recepción, deduplicación, parseo, conciliación y resolución.
 *
 * - Una notificación reenviada varias veces se procesa una sola vez
 *   (bridge_event_id + fingerprint UNIQUE, persistidos en SQLite).
 * - 0 candidatos -> UNMATCHED (no crea venta, no descarta).
 * - 1 candidato -> MATCHED + PaymentIntent PENDING -> PAID con referencia
 *   interna "BRIDGE-EVENT-<id>" (nunca se finge número oficial Yape).
 * - 2+ candidatos -> AMBIGUOUS (ningún pago se marca PAID).
 */
class YapeBridgeService(
    private val payments: PaymentRepository,
    private val events: BridgeStore,
    private val config: YapeBridgeConfig,
    private val publisher: PaidPublisher = PaidPublisher {},
) {

    private val lock = Mutex()
    private val eventSequence = AtomicLong(1)

    /** Resultado del procesamiento: registro + si ya existía (duplicado). */
    data class ProcessResult(val record: IncomingNotificationRecord, val duplicate: Boolean)

    private fun parser(): IncomingNotificationParser =
        ConfigurableNotificationParser(YapeNotificationRules.loadOrNull(config.rulesFile))

    private fun matcher(): YapePaymentMatcher =
        YapePaymentMatcher(payments, config.matchWindowSeconds)

    fun toView(record: IncomingNotificationRecord): BridgeEventView = BridgeEventView(
        id = record.id,
        amountMinor = record.amountMinor,
        amountDisplay = record.amountMinor?.let { runCatching { MoneyFormatter.format(it) }.getOrNull() },
        payerName = record.payerName,
        receivedAt = record.receivedAt.toString(),
        matchStatus = record.matchStatus.name,
        matchedPaymentId = record.matchedPaymentId,
    )

    fun listViews(): List<BridgeEventView> =
        events.findAll().sortedBy { it.receivedAt }.map(::toView)

    /** Entrada del endpoint POST /api/bridge/notifications (ya autenticado). */
    suspend fun receive(raw: RawBridgeNotification, now: Instant = Instant.now()): ProcessResult {
        val bridgeEventId = raw.bridgeEventId.trim()
        require(bridgeEventId.isNotEmpty()) { "bridgeEventId no debe estar vacío" }
        require(raw.packageName.isNotBlank()) { "packageName no debe estar vacío" }
        val postedAt = runCatching { Instant.parse(raw.postedAt.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("postedAt inválido: ${raw.postedAt}")
        val activeParser = parser()
        val fingerprint = BridgeEventFingerprint.compute(
            sourcePackage = raw.packageName,
            notificationKey = raw.notificationKey,
            postedAt = postedAt,
            textParts = listOf(raw.title, raw.text, raw.bigText, raw.subText, raw.summaryText) +
                raw.extras.values.toList(),
        )
        lock.withLock() {
            events.findByBridgeEventId(bridgeEventId)?.let { return ProcessResult(it, true) }
            events.findByFingerprint(fingerprint)?.let { return ProcessResult(it, true) }
            val record = if (!activeParser.isPackageAllowed(raw.packageName)) {
                newRecord(raw, postedAt, now, fingerprint, null, null, MatchStatus.IGNORED, null)
            } else {
                val parsed = activeParser.parse(raw)
                if (parsed == null) {
                    newRecord(raw, postedAt, now, fingerprint, null, null, MatchStatus.IGNORED, null)
                } else {
                    buildMatchedRecord(raw, postedAt, now, fingerprint, parsed, matchCurrencyOf(parsed))
                }
            }
            try {
                events.save(record)
            } catch (e: Exception) {
                // Carrera o reintento concurrente con UNIQUE: devolver el existente.
                events.findByBridgeEventId(bridgeEventId)?.let { return ProcessResult(it, true) }
                events.findByFingerprint(fingerprint)?.let { return ProcessResult(it, true) }
                throw e
            }
            return ProcessResult(record, false)
        }
    }

    /**
     * Inyección LAB ONLY: simula una notificación normalizada sin Android.
     * Pasa por EL MISMO matcher y las mismas reglas de conciliación.
     */
    suspend fun inject(
        amountMinor: Long,
        payerName: String?,
        securityCode: String?,
        now: Instant = Instant.now(),
    ): ProcessResult {
        require(amountMinor > 0) { "amountMinor debe ser > 0" }
        val name = payerName?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null) require(name.length <= 120) { "payerName demasiado largo" }
        if (securityCode != null) require(SecurityCodeMatcher.isValidFormat(securityCode)) {
            "securityCode debe ser exactamente 3 dígitos"
        }
        val bridgeEventId = "LAB-INJECT-${UUID.randomUUID().toString().take(8).uppercase()}"
        val parsed = ParsedIncomingPayment(amountMinor, name, securityCode)
        lock.withLock() {
            val fingerprint = BridgeEventFingerprint.compute(
                sourcePackage = "lab.inject",
                notificationKey = bridgeEventId,
                postedAt = now,
                textParts = listOf("S/ ${(amountMinor / 100)}.${"%02d".format(amountMinor % 100)}", name, securityCode),
            )
            val raw = RawBridgeNotification(
                bridgeEventId = bridgeEventId,
                packageName = "lab.inject",
                notificationKey = bridgeEventId,
                postedAt = now.toString(),
            )
            val record = buildMatchedRecord(raw, now, now, fingerprint, parsed, currencyOfCandidates())
            events.save(record)
            return ProcessResult(record, false)
        }
    }

    /**
     * Resolución por código de 3 dígitos para un pago con candidatos ambiguos.
     * Solo considera eventos AMBIGUOUS del mismo monto dentro de la ventana.
     * Exactamente una coincidencia de HMAC -> MATCHED + PAID; si no, 409.
     */
    suspend fun resolve(paymentId: String, securityCode: String, now: Instant = Instant.now()): BridgeEventView {
        require(SecurityCodeMatcher.isValidFormat(securityCode)) {
            "securityCode debe ser exactamente 3 dígitos"
        }
        lock.withLock() {
            val payment = payments.findById(paymentId)
                ?: throw NoSuchElementException("pago no encontrado: $paymentId")
            require(payment.paymentMethod == PaymentMethod.YAPE_NOTIFICATION) {
                "el pago $paymentId no es YAPE_NOTIFICATION"
            }
            check(payment.status == PaymentStatus.PENDING) {
                "el pago $paymentId no está PENDING (está ${payment.status})"
            }
            val matching = events.findByStatus(MatchStatus.AMBIGUOUS).filter { record ->
                record.amountMinor == payment.amountMinor &&
                    record.securityCodeHmac != null &&
                    withinWindow(payment.createdAt, record.receivedAt) &&
                    SecurityCodeMatcher.matches(record.securityCodeHmac, securityCode, config.matchSecret)
            }
            check(matching.size == 1) {
                if (matching.isEmpty()) "ninguna notificación compatible con ese código"
                else "más de una notificación coincide; no se puede resolver automáticamente"
            }
            val event = matching.single()
            val updated = event.copy(matchStatus = MatchStatus.MATCHED, matchedPaymentId = payment.id)
            events.save(updated)
            markPaid(payment, event.bridgeEventId, updated.receivedAt)
            return toView(updated)
        }
    }

    // ---- internos (siempre bajo lock) ----

    /** Moneda de los candidatos: la de pagos YAPE pendientes, default 604. */
    private fun currencyOfCandidates(): String = "604"

    private fun matchCurrencyOf(parsed: ParsedIncomingPayment): String = "604"

    private fun newRecord(
        raw: RawBridgeNotification,
        postedAt: Instant,
        now: Instant,
        fingerprint: String,
        amountMinor: Long?,
        parsed: ParsedIncomingPayment?,
        status: MatchStatus,
        matchedPaymentId: String?,
    ): IncomingNotificationRecord {
        val codeHmac = parsed?.securityCode
            ?.takeIf(SecurityCodeMatcher::isValidFormat)
            ?.let { SecurityCodeMatcher.hmacHex(it, config.matchSecret) }
        return IncomingNotificationRecord(
            id = nextFreeEventId(),
            bridgeEventId = raw.bridgeEventId.trim(),
            sourcePackage = raw.packageName.trim(),
            notificationKey = raw.notificationKey?.trim()?.takeIf { it.isNotEmpty() },
            postedAt = postedAt,
            receivedAt = now,
            amountMinor = amountMinor,
            payerName = parsed?.payerName?.trim()?.takeIf { it.isNotEmpty() },
            securityCodeHmac = codeHmac,
            matchStatus = status,
            matchedPaymentId = matchedPaymentId,
            rawFingerprint = fingerprint,
        )
    }

    private suspend fun buildMatchedRecord(
        raw: RawBridgeNotification,
        postedAt: Instant,
        now: Instant,
        fingerprint: String,
        parsed: ParsedIncomingPayment,
        currency: String,
    ): IncomingNotificationRecord {
        if (parsed.amountMinor == null || parsed.amountMinor <= 0) {
            return newRecord(raw, postedAt, now, fingerprint, null, parsed, MatchStatus.UNPARSED, null)
        }
        val candidates = matcher().findCandidates(parsed.amountMinor, currency, now)
        return when (candidates.size) {
            0 -> newRecord(raw, postedAt, now, fingerprint, parsed.amountMinor, parsed, MatchStatus.UNMATCHED, null)
            1 -> {
                val payment = candidates.single()
                markPaid(payment, raw.bridgeEventId.trim(), now)
                newRecord(
                    raw, postedAt, now, fingerprint, parsed.amountMinor, parsed,
                    MatchStatus.MATCHED, payment.id,
                )
            }
            else -> newRecord(
                raw, postedAt, now, fingerprint, parsed.amountMinor, parsed,
                MatchStatus.AMBIGUOUS, null,
            )
        }
    }

    private suspend fun markPaid(payment: PaymentIntent, bridgeEventId: String, now: Instant) {
        val current = payments.findById(payment.id) ?: return
        if (current.status != PaymentStatus.PENDING) return
        PaymentStateMachine.validate(current.status, PaymentStatus.PAID)
        payments.save(
            current.copy(
                status = PaymentStatus.PAID,
                paidAt = now,
                providerOperationReference = "BRIDGE-EVENT-$bridgeEventId",
            ),
        )
        publisher.publishPaid(current.id)
    }

    private fun withinWindow(paymentCreatedAt: Instant, eventReceivedAt: Instant): Boolean {
        if (eventReceivedAt.isBefore(paymentCreatedAt)) return false
        return Duration.between(paymentCreatedAt, eventReceivedAt).seconds <= config.matchWindowSeconds
    }

    private fun nextFreeEventId(): String {
        while (true) {
            val id = "EVT-%06d".format(eventSequence.getAndIncrement())
            if (events.findById(id) == null) return id
        }
    }
}
