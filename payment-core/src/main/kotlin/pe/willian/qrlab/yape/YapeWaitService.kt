package pe.willian.qrlab.yape

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.willian.qrlab.bridge.SecurityCodeMatcher
import pe.willian.qrlab.money.MoneyFormatter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * MODELO 2 - detección Yape con múltiples ventas pendientes.
 *
 * Reglas:
 * - Pueden existir VARIAS ventas WAITING simultáneamente (incluso con el
 *   mismo importe). Ya no se cancela la espera anterior al crear una nueva.
 * - Matching por notificación (solo package permitido, monto exacto,
 *   dentro de YAPE_MATCH_WINDOW_SECONDS):
 *     0 candidatos  -> notificación UNMATCHED (sin cambios en ventas),
 *     1 candidato   -> AUTO MATCH: venta WAITING -> PAID + SSE (igual que
 *                      el Modelo 1: comportamiento actual preservado),
 *     2+ candidatos -> AMBIGUOUS: NINGUNA venta se marca PAID; se resuelve
 *                      explícitamente por código HMAC, por nombre exacto
 *                      normalizado o (LAB) selección manual del evento.
 * - Sin YAPE_ALLOWED_PACKAGE sigue en MODO DIAGNÓSTICO: jamás marca PAID.
 *
 * PRIVACIDAD: nunca se guarda raw text ni código en plano. El store solo
 * recibe amount, payerName normalizado, HMAC-SHA256 (YAPE_MATCH_SECRET),
 * timestamps, package y estado de match. La consola imprime únicamente
 * packageName, montos parseados y resultado.
 *
 * Persistencia ([YapeLabStore]): un reinicio conserva ventas y eventos
 * ambiguos, por lo que la resolución sigue funcionando después.
 *
 * Separado del motor de pagos existente: no rompe Payment Engine.
 */
class YapeWaitService(
    /** Package exacto aceptado; null/blank -> modo diagnóstico (PACKAGE_NOT_CONFIGURED). */
    private val allowedPackage: String? = YapeModelConfig.allowedPackageFromEnv(),
    /** Store de ventas/notificaciones (in-memory en tests, SQLite en producción). */
    private val store: YapeLabStore = InMemoryYapeLabStore(),
    /** Secreto HMAC del código de 3 dígitos (YAPE_MATCH_SECRET). */
    private val matchSecret: String = YapeModelConfig.matchSecretFromEnv(),
    /** Ventana venta<->notificación en segundos (YAPE_MATCH_WINDOW_SECONDS). */
    private val matchWindowSeconds: Long = YapeModelConfig.matchWindowSecondsFromEnv(),
    private val waitTtlSeconds: Long = YapeModelConfig.waitTtlSecondsFromEnv(),
    private val clock: () -> Instant = Instant::now,
    /** Patrones del parser: configurables/testeables, default = formato observado. */
    private val payerPattern: Regex = YapeNotificationParser.DEFAULT_PAYER_PATTERN,
    private val securityCodePattern: Regex = YapeNotificationParser.DEFAULT_SECURITY_CODE_PATTERN,
) {

    private val lock = Any()
    private val paymentSequence = AtomicLong(maxSuffix(store.findAllWaits().map { it.id }, "YW-"))
    private val notificationSequence = AtomicLong(
        maxSuffix(store.findAllNotifications().map { it.id }, "YN-"),
    )
    private val waits = LinkedHashMap<String, YapeWaitingPayment>().apply {
        store.findAllWaits().forEach { put(it.id, it) }
    }
    private val notifications = LinkedHashMap<String, IncomingPaymentNotification>().apply {
        store.findAllNotifications().forEach { put(it.id, it) }
    }
    private var latestId: String? = waits.keys.maxOrNull()
    private var lastNotification: YapeNotificationDebugDto? = null

    /** Conteo interno de eventos descartados por resultado (nunca se publica). */
    private val ignored = linkedMapOf<String, Long>()

    private val _state = MutableStateFlow(buildStateLocked())

    /** Estado actual para SSE/pantallas (mismo patrón que PaymentEventBus). */
    val state: StateFlow<YapeStateDto> = _state.asStateFlow()

    // ------------------------------------------------------------------ ventas

    /**
     * Crea UNA venta WAITING adicional (NO cancela las anteriores:
     * el Modelo 2 permite varias pendientes, incluso con el mismo importe).
     * Valida amountMinor > 0.
     */
    fun createWait(amountMinor: Long, reference: String? = null): YapeWaitCreatedDto {
        require(amountMinor > 0) { "amountMinor debe ser > 0, fue $amountMinor" }
        synchronized(lock) {
            val createdAt = clock()
            val wait = YapeWaitingPayment(
                id = "YW-%06d".format(paymentSequence.incrementAndGet()),
                reference = reference?.trim()?.takeIf { it.isNotEmpty() },
                amountMinor = amountMinor,
                createdAt = createdAt,
                expiresAt = createdAt.plusSeconds(waitTtlSeconds),
                status = YapeWaitStatus.WAITING,
            )
            waits[wait.id] = wait
            store.saveWait(wait)
            latestId = wait.id
            publishLocked()
            return YapeWaitCreatedDto(
                id = wait.id,
                amountMinor = wait.amountMinor,
                amountDisplay = MoneyFormatter.format(wait.amountMinor),
                status = wait.status.name,
                reference = wait.reference,
            )
        }
    }

    /** Última venta creada (WAITING, PAID o CANCELLED). */
    fun current(): YapeWaitViewDto? = synchronized(lock) { latestLocked()?.toViewDto() }

    /** Venta por id. */
    fun find(id: String): YapeWaitViewDto? = synchronized(lock) { waits[id]?.toViewDto() }

    /** Todas las ventas en orden de creación (tabla de la UI). */
    fun listWaits(): List<YapeWaitViewDto> =
        synchronized(lock) { waits.values.sortedBy { it.id }.map { it.toViewDto() } }

    fun expireDue(now: Instant = clock()): List<YapeWaitViewDto> = synchronized(lock) {
        val expired = waits.values.filter {
            (it.status == YapeWaitStatus.WAITING || it.status == YapeWaitStatus.AMBIGUOUS) &&
                !now.isBefore(it.expiresAt)
        }.map { it.copy(status = YapeWaitStatus.EXPIRED) }
        expired.forEach { waits[it.id] = it; store.saveWait(it) }
        if (expired.isNotEmpty()) publishLocked()
        expired.map { it.toViewDto() }
    }

    /**
     * Cancela una venta WAITING (manual/ LAB). PAID no se puede cancelar.
     * Una venta CANCELLED ya no participa como candidata.
     */
    fun cancelWait(id: String): YapeWaitViewDto = synchronized(lock) {
        val wait = waits[id] ?: throw NoSuchElementException("venta no encontrada: $id")
        if (wait.status != YapeWaitStatus.WAITING) {
            throw IllegalStateException("solo se puede cancelar una venta WAITING: $id está ${wait.status}")
        }
        val cancelled = wait.copy(status = YapeWaitStatus.CANCELLED)
        waits[id] = cancelled
        store.saveWait(cancelled)
        publishLocked()
        cancelled.toViewDto()
    }

    // ----------------------------------------------------------- notificaciones

    /**
     * Procesa una notificación cruda del bridge (LAB ONLY).
     *
     * Regla del match (parser y motor de pagos intactos):
     * 1. sin YAPE_ALLOWED_PACKAGE -> MODO DIAGNÓSTICO (jamás PAID).
     * 2. package distinto del permitido -> PACKAGE_NOT_ALLOWED, DESCARTADO.
     * 3. sin venta alguna -> NO_ACTIVE_WAIT, DESCARTADO.
     * 4. sin montos candidatos -> AMOUNT_NOT_FOUND, DESCARTADO.
     * 5. ningún monto encaja con ninguna venta -> AMOUNT_MISMATCH, DESCARTADO.
     * 6. UN candidato -> AUTO MATCH (WAITING -> PAID, PAYMENT_MATCHED, SSE).
     * 7. 2+ candidatos -> AMBIGUOUS: nada se marca PAID (panel de resolución).
     *
     * Los descartados NO tocan el estado visible: solo [ignored] + log mínimo.
     */
    fun receiveRaw(raw: YapeRawNotificationRequest): YapeNotificationDebugDto {
        val parsed = YapeNotificationParser.parse(
            texts = YapeNotificationParser.NotificationTexts(
                title = raw.title,
                text = raw.text,
                bigText = raw.bigText,
                subText = raw.subText,
                summaryText = raw.summaryText,
            ),
            payerPattern = payerPattern,
            securityCodePattern = securityCodePattern,
            securityCodeOverride = raw.securityCode,
        )
        return process(packageName = raw.packageName, parsed = parsed, bridgeEventId = null, receivedAt = clock())
    }

    fun receiveRawAck(raw: YapeRawNotificationRequest): YapeBridgeAckDto {
        val eventId = raw.bridgeEventId?.trim().takeIf { !it.isNullOrEmpty() }
            ?: throw IllegalArgumentException("bridgeEventId no debe estar vacío")
        val parsed = YapeNotificationParser.parse(
            YapeNotificationParser.NotificationTexts(raw.title, raw.text, raw.bigText, raw.subText, raw.summaryText),
            payerPattern, securityCodePattern, raw.securityCode,
        )
        val receivedAt = runCatching { Instant.parse(raw.postedAt) }.getOrElse {
            raw.postedAt.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: clock()
        }
        val debug = process(raw.packageName, parsed, eventId, receivedAt)
        return YapeBridgeAckDto(true, eventId, debug.result, debug.notificationId)
    }

    /**
     * Inyección LAB ONLY con campos estructurados (amount/payerName/
     * securityCode). Pasa por EXACTAMENTE el mismo matcher real que una
     * notificación del bridge (mismo match, mismo HMAC, misma ambigüedad).
     */
    fun labInject(request: YapeLabInjectRequest): YapeNotificationDebugDto {
        require(request.amountMinor > 0) { "amountMinor debe ser > 0, fue ${request.amountMinor}" }
        val code = request.securityCode?.trim()?.takeIf { it.isNotEmpty() }
        if (code != null && !SecurityCodeMatcher.isValidFormat(code)) {
            throw IllegalArgumentException("securityCode debe ser exactamente 3 dígitos")
        }
        val payerName = request.payerName?.trim()?.takeIf { it.isNotEmpty() }
        val packageName = allowedPackage?.takeIf { it.isNotBlank() } ?: "lab.inject"
        val parsed = YapeNotificationParser.ParsedNotification(
            amounts = listOf(request.amountMinor),
            payerName = payerName,
            securityCode = code,
            sourceType = if (code != null || payerName != null) {
                YapeSourceType.YAPE_NATIVE
            } else {
                YapeSourceType.UNKNOWN
            },
        )
        return process(packageName = packageName, parsed = parsed, bridgeEventId = null, receivedAt = clock())
    }

    /** Vista LAB de las notificaciones recibidas (diagnóstico admin). */
    fun notifications(): List<YapeNotificationViewDto> = synchronized(lock) {
        notifications.values.sortedBy { it.id }.map { it.toViewDto() }
    }

    // --------------------------------------------------------------- resolución

    /**
     * Resuelve una venta ambigua (online-ready: usable desde checkout web).
     *
     * - [securityCode]: exactamente 3 dígitos -> HMAC -> notificación
     *   AMBIGUOUS del mismo importe/ventana con HMAC coincidente.
     * - [payerName]: solo notificaciones AMBIGUOUS con nombre y SIN código;
     *   comparación EXACTA tras normalizar (sin fuzzy/contains).
     *
     * Exactamente 1 -> venta PAID + notificación MATCHED.
     * 0 o 2+ -> 409 (sin revelar nunca el código esperado).
     * Un nombre NUNCA crea pagos: siempre contrasta contra notificaciones
     * reales ya recibidas.
     */
    fun resolve(paymentId: String, securityCode: String?): YapeWaitViewDto {
        val code = securityCode?.trim()?.takeIf { it.isNotEmpty() }
        require(code != null) { "se requiere securityCode (3 dígitos)" }
        if (!SecurityCodeMatcher.isValidFormat(code)) {
            throw IllegalArgumentException("securityCode debe ser exactamente 3 dígitos")
        }
        return synchronized(lock) { expireDueLocked(clock()); resolveByCodeLocked(paymentId, code) }
    }

    /** Compatibilidad interna con clientes LAB anteriores; la UI nueva no lo ofrece. */
    @Deprecated("YAPE SECURITY CODE MATCH no usa nombres")
    fun resolve(paymentId: String, securityCode: String?, payerName: String?): YapeWaitViewDto {
        if (!securityCode.isNullOrBlank()) return resolve(paymentId, securityCode)
        val name = payerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("se requiere securityCode (3 dígitos)")
        return synchronized(lock) {
            expireDueLocked(clock())
            val payment = waits[paymentId] ?: throw NoSuchElementException("venta no encontrada: $paymentId")
            requireMatchingWait(payment)
            val normalized = YapePayerNames.normalize(name)
            val matches = ambiguousCandidatesLocked(payment).filter { candidate ->
                candidate.securityCodeHmac == null && candidate.payerName != null &&
                    YapePayerNames.normalize(candidate.payerName) == normalized
            }
            if (matches.size != 1) throw IllegalStateException("resolución no única para la venta $paymentId: ${matches.size} coincidencias")
            applyResolutionLocked(payment, matches.single())
        }
    }

    /**
     * Fallback LAB (§9): selección manual explícita de un evento ambiguo
     * SIN código ni nombre. Solo LAB, nunca automático, y siempre exige una
     * notificación AMBIGUOUS real e incompatible con auto-resolución.
     */
    fun manualResolve(paymentId: String, notificationId: String): YapeWaitViewDto = synchronized(lock) {
        val payment = waits[paymentId] ?: throw NoSuchElementException("venta no encontrada: $paymentId")
        requireMatchingWait(payment)
        val notification = notifications[notificationId]
            ?: throw NoSuchElementException("notificación no encontrada: $notificationId")
        if (notification.matchStatus != YapeMatchStatus.AMBIGUOUS) {
            throw IllegalStateException("la notificación no está AMBIGUOUS: $notificationId")
        }
        if (notification.amountMinor != payment.amountMinor ||
            !inWindow(payment.createdAt, notification.receivedAt)
        ) {
            throw IllegalStateException("la notificación no corresponde a la venta $paymentId")
        }
        applyResolutionLocked(payment, notification)
    }

    /** Conteo interno de eventos descartados por resultado (diagnóstico, sin UI). */
    fun ignoredCounts(): Map<String, Long> = synchronized(lock) { ignored.toMap() }

    // ------------------------------------------------------------------ internos

    private fun resolveByCodeLocked(paymentId: String, securityCode: String): YapeWaitViewDto {
        val payment = waits[paymentId] ?: throw NoSuchElementException("venta no encontrada: $paymentId")
        requireMatchingWait(payment)
        val candidates = ambiguousCandidatesLocked(payment).filter { it.securityCodeHmac != null }
        val matches = candidates.filter {
            SecurityCodeMatcher.matches(it.securityCodeHmac!!, securityCode, matchSecret)
        }
        if (matches.size != 1) {
            // 409: jamás se revela el código esperado ni cuántos dígitos faltan.
            throw IllegalStateException("No se encontró un pago compatible con el código ingresado.")
        }
        return applyResolutionLocked(payment, matches.single())
    }

    private fun requireMatchingWait(payment: YapeWaitingPayment) {
        if (payment.status != YapeWaitStatus.WAITING && payment.status != YapeWaitStatus.AMBIGUOUS) {
            throw IllegalStateException("la venta ${payment.id} no está WAITING: ${payment.status}")
        }
        if (!clock().isBefore(payment.expiresAt)) throw IllegalStateException("la venta ${payment.id} está expirada")
    }

    private fun expireDueLocked(now: Instant) {
        val expired = waits.values.filter {
            (it.status == YapeWaitStatus.WAITING || it.status == YapeWaitStatus.AMBIGUOUS) &&
                !now.isBefore(it.expiresAt)
        }.map { it.copy(status = YapeWaitStatus.EXPIRED) }
        expired.forEach { waits[it.id] = it; store.saveWait(it) }
        if (expired.isNotEmpty()) publishLocked()
    }

    /** Candidatas a resolución: AMBIGUOUS + mismo importe + ventana. */
    private fun ambiguousCandidatesLocked(payment: YapeWaitingPayment): List<IncomingPaymentNotification> =
        notifications.values.filter {
            it.matchStatus == YapeMatchStatus.AMBIGUOUS &&
                it.amountMinor == payment.amountMinor &&
                inWindow(payment.createdAt, it.receivedAt)
        }

    /**
     * Aplica la resolución (debe ir con [lock] tomado y tras validar el
     * conteo exacto): venta -> PAID, notificación -> MATCHED. Revalida el
     * estado para que concurrencia/duplicados jamás generen doble PAID.
     */
    private fun applyResolutionLocked(
        payment: YapeWaitingPayment,
        notification: IncomingPaymentNotification,
    ): YapeWaitViewDto {
        val freshPayment = waits[payment.id]
            ?: throw NoSuchElementException("venta no encontrada: ${payment.id}")
        requireMatchingWait(freshPayment)
        val freshNotification = notifications[notification.id]
            ?: throw IllegalStateException("notificación no disponible: ${notification.id}")
        if (freshNotification.matchStatus != YapeMatchStatus.AMBIGUOUS) {
            throw IllegalStateException("la notificación ya fue utilizada: ${notification.id}")
        }
        val now = clock()
        val paid = freshPayment.copy(
            status = YapeWaitStatus.PAID,
            receivedAt = now,
            sourcePackage = freshNotification.sourcePackage,
            matchedNotificationId = freshNotification.id,
        )
        val matched = freshNotification.copy(
            matchStatus = YapeMatchStatus.MATCHED,
            matchedPaymentId = freshPayment.id,
        )
        waits[paid.id] = paid
        store.saveWait(paid)
        notifications[matched.id] = matched
        store.saveNotification(matched)
        publishLocked()
        return paid.toViewDto()
    }

    /** Pipeline común de toda notificación (bridge e inyección LAB). */
    private fun process(
        packageName: String,
        parsed: YapeNotificationParser.ParsedNotification,
        bridgeEventId: String?,
        receivedAt: Instant,
    ): YapeNotificationDebugDto {
        val (debug, shown) = synchronized(lock) {
            if (bridgeEventId != null) {
                notifications.values.firstOrNull { it.bridgeEventId == bridgeEventId }?.let { return it.toDebugDto() }
            }
            val evaluated = evaluateLocked(packageName, parsed, bridgeEventId, receivedAt)
            if (shownOnScreen(evaluated.result)) {
                // Únicos casos que mueven la pantalla: match exacto,
                // ambigüedad (panel de resolución) o diagnóstico.
                lastNotification = evaluated
                publishLocked()
                evaluated to true
            } else {
                ignored.merge(evaluated.result, 1L) { a, b -> a + b }
                evaluated to false
            }
        }
        // Log mínimo permitido: package + montos parseados + resultado.
        // NUNCA se imprimen textos completos, nombres ni códigos.
        println(logLine(debug, shown))
        return debug
    }

    /**
     * Solo tres motivos llegan a la pantalla:
     * - PAYMENT_MATCHED (auto match con exactamente un candidato),
     * - AMBIGUOUS (2+ candidatos: panel de resolución, nada PAID aún),
     * - PACKAGE_NOT_CONFIGURED (MODO DIAGNÓSTICO, nunca marca PAID).
     */
    private fun shownOnScreen(result: String): Boolean =
        result == YapeMatchResult.PAYMENT_MATCHED ||
            result == YapeMatchResult.AMBIGUOUS ||
            (allowedPackage.isNullOrBlank() && result == YapeMatchResult.PACKAGE_NOT_CONFIGURED)

    /**
     * Línea de log del evento. Formato fijo y mínima: package, montos
     * parseados y resultado. No contiene texto, nombre ni código.
     */
    internal fun logLine(debug: YapeNotificationDebugDto, shown: Boolean): String {
        if (shown) {
            return "YAPE BRIDGE | packageName=${debug.packageName}" +
                " | parsed=${debug.detectedAmountsMinor} | result=${debug.result}"
        }
        val expected = debug.expectedAmountMinor
        return when (debug.result) {
            YapeMatchResult.PACKAGE_NOT_ALLOWED ->
                "YAPE BRIDGE | ignored package=${debug.packageName}"
            YapeMatchResult.AMOUNT_NOT_FOUND ->
                "YAPE BRIDGE | ignored amount=none expected=$expected"
            YapeMatchResult.AMOUNT_MISMATCH ->
                "YAPE BRIDGE | ignored amount=${debug.detectedAmountsMinor.joinToString(",")}" +
                    " expected=$expected"
            YapeMatchResult.NO_ACTIVE_WAIT ->
                "YAPE BRIDGE | ignored wait=none package=${debug.packageName}"
            YapeMatchResult.ALREADY_PAID ->
                "YAPE BRIDGE | ignored already-paid package=${debug.packageName}"
            else ->
                "YAPE BRIDGE | ignored result=${debug.result} package=${debug.packageName}"
        }
    }

    /**
     * Evalúa y aplica el match. Debe llamarse con [lock] tomado.
     * Ver doc de [receiveRaw] para el orden exacto de las reglas.
     */
    private fun evaluateLocked(
        packageName: String,
        parsed: YapeNotificationParser.ParsedNotification,
        bridgeEventId: String?,
        receivedAt: Instant,
    ): YapeNotificationDebugDto {
        val now = receivedAt
        val amounts = parsed.amounts
        val allWaits = waits.values.toList()
        val expectedDefault = allWaits.lastOrNull { it.status == YapeWaitStatus.WAITING }?.amountMinor
            ?: allWaits.lastOrNull()?.amountMinor
        val recordAmount = amounts.firstOrNull { amount -> allWaits.any { it.amountMinor == amount } }
            ?: amounts.firstOrNull()

        fun debug(result: String, expected: Long?, notification: IncomingPaymentNotification?) =
            YapeNotificationDebugDto(
                packageName = packageName,
                detectedAmountsMinor = amounts,
                detectedAmountsDisplay = amounts.map { MoneyFormatter.format(it) },
                expectedAmountMinor = expected,
                result = result,
                receivedAt = now.toString(),
                notificationId = notification?.id,
                sourceType = parsed.sourceType.name,
                matchStatus = notification?.matchStatus?.name,
            )

        // 1. Modo diagnóstico: primero descubriremos el package real.
        if (allowedPackage.isNullOrBlank()) {
            val record = persistLocked(parsed, recordAmount, YapeMatchStatus.IGNORED, now, packageName, bridgeEventId)
            return debug(YapeMatchResult.PACKAGE_NOT_CONFIGURED, expectedDefault, record)
        }
        // 2. Solo se acepta el package configurado, exacto.
        if (packageName != allowedPackage) {
            val record = persistLocked(parsed, recordAmount, YapeMatchStatus.IGNORED, now, packageName, bridgeEventId)
            return debug(YapeMatchResult.PACKAGE_NOT_ALLOWED, expectedDefault, record)
        }
        // 3. Sin ventas no hay matching que hacer (0 candidatos).
        if (allWaits.isEmpty()) {
            val record = persistLocked(parsed, recordAmount, YapeMatchStatus.UNMATCHED, now, packageName, bridgeEventId)
            return debug(YapeMatchResult.NO_ACTIVE_WAIT, null, record)
        }
        // 4. Sin montos candidatos no hay nada que comparar (no se persiste).
        if (amounts.isEmpty()) return debug(YapeMatchResult.AMOUNT_NOT_FOUND, expectedDefault, null)
        // 5. ¿Algún monto encaja con alguna venta?
        val matchingAmount = amounts.firstOrNull { amount -> allWaits.any { it.amountMinor == amount } }
        if (matchingAmount == null) {
            val record = persistLocked(parsed, recordAmount, YapeMatchStatus.UNMATCHED, now, packageName)
            return debug(YapeMatchResult.AMOUNT_MISMATCH, expectedDefault, record)
        }
        // 6. Candidatas: WAITING + mismo importe + dentro de la ventana.
        val candidates = allWaits.filter {
            it.status == YapeWaitStatus.WAITING &&
                it.amountMinor == matchingAmount &&
                inWindow(it.createdAt, now)
        }
        if (candidates.isEmpty()) {
            val record = persistLocked(parsed, matchingAmount, YapeMatchStatus.UNMATCHED, now, packageName, bridgeEventId)
            val amountWaits = allWaits.filter { it.amountMinor == matchingAmount }
            val result = if (amountWaits.any { it.status == YapeWaitStatus.PAID }) {
                YapeMatchResult.ALREADY_PAID
            } else {
                YapeMatchResult.NO_ACTIVE_WAIT // cancelada o fuera de ventana
            }
            return debug(result, matchingAmount, record)
        }
        // 7a. UN candidato -> AUTO MATCH: WAITING -> PAID + SSE.
        if (candidates.size == 1) {
            val wait = candidates.single()
            val record = persistLocked(
                parsed, matchingAmount, YapeMatchStatus.MATCHED, now, packageName, bridgeEventId,
                matchedPaymentId = wait.id,
            )
            val paid = wait.copy(
                status = YapeWaitStatus.PAID,
                receivedAt = now,
                sourcePackage = packageName,
                matchedNotificationId = record?.id,
            )
            waits[paid.id] = paid
            store.saveWait(paid)
            return debug(YapeMatchResult.PAYMENT_MATCHED, matchingAmount, record)
        }
        // 7b. 2+ candidatos -> AMBIGUOUS: NINGUNA venta se marca PAID.
        val record = persistLocked(parsed, matchingAmount, YapeMatchStatus.AMBIGUOUS, now, packageName, bridgeEventId)
        return debug(YapeMatchResult.AMBIGUOUS, matchingAmount, record)
    }

    /**
     * Persiste la notificación (SOLO metadatos permitidos; jamás raw text
     * ni código: únicamente su HMAC). Devuelve el registro o null si no
     * hay monto que guardar.
     */
    private fun persistLocked(
        parsed: YapeNotificationParser.ParsedNotification,
        amountMinor: Long?,
        status: YapeMatchStatus,
        receivedAt: Instant,
        sourcePackage: String,
        bridgeEventId: String? = null,
        matchedPaymentId: String? = null,
    ): IncomingPaymentNotification? {
        if (amountMinor == null) return null
        val normalizedPayerName = parsed.payerName
            ?.let { YapePayerNames.normalize(it) }
            ?.takeIf { it.isNotEmpty() }
        val notification = IncomingPaymentNotification(
            id = "YN-%06d".format(notificationSequence.incrementAndGet()),
            bridgeEventId = bridgeEventId,
            amountMinor = amountMinor,
            payerName = normalizedPayerName,
            securityCodeHmac = parsed.securityCode?.let { SecurityCodeMatcher.hmacHex(it, matchSecret) },
            receivedAt = receivedAt,
            sourcePackage = sourcePackage,
            sourceType = parsed.sourceType,
            matchStatus = status,
            matchedPaymentId = matchedPaymentId,
        )
        notifications[notification.id] = notification
        store.saveNotification(notification)
        return notification
    }

    /** ¿La venta cae dentro de la ventana razonable respecto del evento? */
    private fun inWindow(createdAt: Instant, eventAt: Instant): Boolean =
        !createdAt.isAfter(eventAt) && Duration.between(createdAt, eventAt).seconds <= matchWindowSeconds

    private fun latestLocked(): YapeWaitingPayment? = latestId?.let { waits[it] }

    /** Caso ambiguo actual para la UI (el grupo más reciente con candidatas). */
    private fun ambiguousLocked(): YapeAmbiguousDto? {
        val groups = notifications.values
            .filter { it.matchStatus == YapeMatchStatus.AMBIGUOUS }
            .groupBy { it.amountMinor }
            .values
        for (group in groups.sortedByDescending { it.maxOf { n -> n.receivedAt } }) {
            val amount = group.first().amountMinor
            val eventAt = group.maxOf { it.receivedAt }
            val candidates = waits.values
                .filter {
                    it.status == YapeWaitStatus.WAITING &&
                        it.amountMinor == amount &&
                        inWindow(it.createdAt, eventAt)
                }
                .sortedBy { it.id }
            if (candidates.isEmpty()) continue
            return YapeAmbiguousDto(
                amountMinor = amount,
                amountDisplay = MoneyFormatter.format(amount),
                paymentIds = candidates.map { it.id },
                requiresSecurityCode = group.any { it.securityCodeHmac != null },
                securityCodeUnavailable = group.any { it.securityCodeHmac == null },
                // Se conserva en el DTO solo por compatibilidad con tests/clientes LAB
                // anteriores; yape-test no lo renderiza.
                requiresPayerName = group.any { it.securityCodeHmac == null && it.payerName != null },
                notificationCount = group.size,
            )
        }
        return null
    }

    /** Estado visible construido desde las ventas/notificaciones en memoria. */
    private fun buildStateLocked() = YapeStateDto(
        wait = latestLocked()?.toViewDto(),
        waits = waits.values.sortedBy { it.id }.map { it.toViewDto() },
        lastNotification = lastNotification,
        ambiguous = ambiguousLocked(),
    )

    private fun publishLocked() {
        _state.value = buildStateLocked()
    }

    private fun YapeWaitingPayment.toViewDto() = YapeWaitViewDto(
        id = id,
        amountMinor = amountMinor,
        amountDisplay = MoneyFormatter.format(amountMinor),
        status = status.name,
        createdAt = createdAt.toString(),
        expiresAt = expiresAt.toString(),
        receivedAt = receivedAt?.toString(),
        sourcePackage = sourcePackage,
        reference = reference,
        matchedNotificationId = matchedNotificationId,
    )

    private fun IncomingPaymentNotification.toViewDto() = YapeNotificationViewDto(
        id = id,
        amountMinor = amountMinor,
        amountDisplay = MoneyFormatter.format(amountMinor),
        payerName = payerName,
        hasSecurityCode = securityCodeHmac != null,
        receivedAt = receivedAt.toString(),
        sourcePackage = sourcePackage,
        sourceType = sourceType.name,
        matchStatus = matchStatus.name,
        matchedPaymentId = matchedPaymentId,
    )

    private fun IncomingPaymentNotification.toDebugDto() = YapeNotificationDebugDto(
        packageName = sourcePackage,
        detectedAmountsMinor = listOf(amountMinor),
        detectedAmountsDisplay = listOf(MoneyFormatter.format(amountMinor)),
        expectedAmountMinor = amountMinor,
        result = when (matchStatus) {
            YapeMatchStatus.MATCHED -> YapeMatchResult.PAYMENT_MATCHED
            YapeMatchStatus.AMBIGUOUS -> YapeMatchResult.AMBIGUOUS
            else -> YapeMatchResult.NO_ACTIVE_WAIT
        },
        receivedAt = receivedAt.toString(),
        notificationId = id,
        sourceType = sourceType.name,
        matchStatus = matchStatus.name,
    )

    companion object {
        /** Siguiente secuencia a partir de ids existentes (sobrevive reinicios). */
        internal fun maxSuffix(ids: List<String>, prefix: String): Long =
            ids.filter { it.startsWith(prefix) }
                .mapNotNull { it.removePrefix(prefix).toLongOrNull() }
                .maxOrNull() ?: 0L
    }
}
