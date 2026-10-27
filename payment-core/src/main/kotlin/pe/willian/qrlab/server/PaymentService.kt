package pe.willian.qrlab.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.willian.qrlab.bridge.BridgeEventView
import pe.willian.qrlab.bridge.BridgeStore
import pe.willian.qrlab.bridge.InMemoryBridgeStore
import pe.willian.qrlab.bridge.PaidPublisher
import pe.willian.qrlab.bridge.RawBridgeNotification
import pe.willian.qrlab.bridge.YapeBridgeConfig
import pe.willian.qrlab.bridge.YapeBridgeService
import pe.willian.qrlab.bridge.YapeStaticQrService
import pe.willian.qrlab.money.MoneyFormatter
import pe.willian.qrlab.payment.PaymentIdempotencyConflictException
import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentMethod
import pe.willian.qrlab.payment.PaymentProvider
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStatus
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.payment.yape.YapeNotificationPaymentProvider
import pe.willian.qrlab.qr.QrImageWriter
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Coordina el engine: [PaymentProvider] sintético, [YapeNotificationPaymentProvider],
 * [PaymentRepository], [SyntheticPaymentSimulator], [YapeBridgeService] y [PaymentEventBus].
 *
 * Las rutas trabajan solo con DTOs; nunca manipulan [PaymentIntent].
 * Los bytes del QR se leen del PNG ya generado (sin regenerar).
 *
 * Los parámetros nuevos ([yapeProvider], [bridgeStore], [bridgeConfig]) tienen
 * defaults para no romper construcciones existentes (tests y demos).
 */
class PaymentService(
    private val provider: PaymentProvider,
    private val repository: PaymentRepository,
    private val simulator: SyntheticPaymentSimulator,
    private val eventBus: PaymentEventBus,
    private val qrDir: Path,
    private val storageName: String = "memory",
    yapeProvider: PaymentProvider? = null,
    bridgeStore: BridgeStore? = null,
    private val bridgeConfig: YapeBridgeConfig = YapeBridgeConfig.fromEnv(),
) {

    private val yapePayments: PaymentProvider = yapeProvider ?: YapeNotificationPaymentProvider(repository)
    private val staticQr = YapeStaticQrService(bridgeConfig.staticQrPayloadFile)
    private val bridge = YapeBridgeService(
        payments = repository,
        events = bridgeStore ?: InMemoryBridgeStore(),
        config = bridgeConfig,
        publisher = PaidPublisher { paymentId ->
            eventBus.publish(PaymentStatusEvent(paymentId, PaymentStatus.PAID))
        },
    )

    /**
     * Crea (idempotente por referencia, monto, moneda y método) y publica el estado.
     * method: SYNTHETIC_QR (default) o YAPE_NOTIFICATION.
     */
    suspend fun create(
        reference: String,
        amountMinor: Long,
        currency: String,
        method: String? = null,
    ): PaymentDto {
        require(reference.isNotBlank()) { "reference no debe estar vacía" }
        require(amountMinor > 0) { "amountMinor debe ser > 0" }
        require(currency.isNotBlank()) { "currency no debe estar vacía" }
        val paymentMethod = parseMethod(method)
        val ref = reference.trim()
        repository.findByReference(ref)?.let { existing ->
            if (existing.amountMinor == amountMinor && existing.currency == currency &&
                existing.paymentMethod == paymentMethod
            ) {
                eventBus.publish(PaymentStatusEvent(existing.id, existing.status))
                return toDto(existing)
            }
            throw PaymentIdempotencyConflictException(
                "reference \"$ref\" ya existe con monto ${existing.amountMinor}, " +
                    "moneda ${existing.currency} y método ${existing.paymentMethod}: " +
                    "no se crea un segundo pago",
            )
        }
        val intent = when (paymentMethod) {
            PaymentMethod.SYNTHETIC_QR -> provider.createPayment(ref, amountMinor, currency)
            PaymentMethod.YAPE_NOTIFICATION -> yapePayments.createPayment(ref, amountMinor, currency)
        }
        eventBus.publish(PaymentStatusEvent(intent.id, intent.status))
        return toDto(intent)
    }

    /** Recupera la vista pública, o null si no existe. */
    fun get(paymentId: String): PaymentDto? =
        repository.findById(paymentId)?.let(::toDto)

    /**
     * Bytes PNG del QR. Si el archivo no existe (p. ej. tras reiniciar
     * en otro directorio), se regenera desde el qrPayload persistido:
     * mismo payload -> PNG, con round-trip verificado. Nunca cambia
     * el payload ni crea otro pago. Los pagos YAPE_NOTIFICATION no tienen
     * QR dinámico: siempre null.
     */
    fun qrBytes(paymentId: String): ByteArray? {
        val intent = repository.findById(paymentId) ?: return null
        val payload = intent.qrPayload ?: return null
        val png = qrDir.resolve("$paymentId.png")
        if (!Files.exists(png)) {
            QrImageWriter.write(payload, png)
            check(QrImageWriter.read(png) == payload) { "round-trip ZXing falló para $paymentId" }
        }
        return Files.readAllBytes(png)
    }

    /** Salud del servicio (sin rutas sensibles). */
    fun health(): HealthDto = HealthDto(status = "ok", mode = "synthetic-lab", storage = storageName)

    /** Cancela (PENDING -> CANCELLED) y publica el cambio. */
    suspend fun cancel(paymentId: String): PaymentDto {
        val current = repository.findById(paymentId)
            ?: throw NoSuchElementException("pago no encontrado: $paymentId")
        val intent = when (current.paymentMethod) {
            PaymentMethod.SYNTHETIC_QR -> provider.cancelPayment(paymentId)
            PaymentMethod.YAPE_NOTIFICATION -> yapePayments.cancelPayment(paymentId)
        }
        eventBus.publish(PaymentStatusEvent(intent.id, intent.status))
        return toDto(intent)
    }

    /** LAB ONLY: confirma vía simulador y publica PAID. */
    suspend fun labConfirm(paymentId: String): PaymentDto =
        publishSimulated(simulator.confirm(paymentId))

    /** LAB ONLY: falla vía simulador y publica FAILED. */
    suspend fun labFail(paymentId: String): PaymentDto =
        publishSimulated(simulator.fail(paymentId))

    /** LAB ONLY: expira vía simulador y publica EXPIRED. */
    suspend fun labExpire(paymentId: String): PaymentDto =
        publishSimulated(simulator.expire(paymentId))

    /** Flujo de estados mínimos de un pago (sin qrPayload). */
    fun events(paymentId: String): Flow<PaymentStatusEventDto>? {
        repository.findById(paymentId) ?: return null
        return eventBus.forPayment(paymentId).map { PaymentStatusEventDto(it.id, it.status.name) }
    }

    // ---- Yape bridge ----

    /** PNG del QR estático del negocio, o null si no está configurado. */
    fun staticQrPng(): ByteArray? = staticQr.pngBytes()

    /** true si hay QR estático configurado (para exponer staticQrUrl). */
    fun hasStaticQr(): Boolean = staticQr.payloadOrNull() != null

    /** Comparación segura del header X-Bridge-Key. Nunca loguea el secreto. */
    fun checkBridgeKey(provided: String?): Boolean {
        if (provided.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            bridgeConfig.bridgeKey.toByteArray(Charsets.UTF_8),
        )
    }

    /** Procesa una notificación del bridge. Devuelve vista + si fue duplicada. */
    suspend fun bridgeReceive(raw: RawBridgeNotification): Pair<BridgeEventDto, Boolean> {
        val result = bridge.receive(raw)
        return toDto(bridge.toView(result.record)) to result.duplicate
    }

    /** LAB ONLY: inyección normalizada sin Android (mismo matcher). */
    suspend fun labInject(request: LabBridgeInjectRequest): BridgeEventDto {
        val result = bridge.inject(request.amountMinor, request.payerName, request.securityCode)
        return toDto(bridge.toView(result.record))
    }

    /** LAB ONLY: eventos del bridge sin secretos ni raw. */
    fun labBridgeEvents(): List<BridgeEventDto> = bridge.listViews().map { toDto(it) }

    /** Resuelve un pago ambiguo con el código de 3 dígitos del comprobante. */
    suspend fun resolveYape(paymentId: String, securityCode: String): PaymentDto {
        bridge.resolve(paymentId, securityCode)
        val updated = repository.findById(paymentId)
            ?: throw NoSuchElementException("pago no encontrado: $paymentId")
        return toDto(updated)
    }

    private fun toDto(view: BridgeEventView): BridgeEventDto = BridgeEventDto(
        id = view.id,
        amountMinor = view.amountMinor,
        amountDisplay = view.amountDisplay,
        payerName = view.payerName,
        receivedAt = view.receivedAt,
        matchStatus = view.matchStatus,
        matchedPaymentId = view.matchedPaymentId,
    )

    private fun parseMethod(method: String?): PaymentMethod {
        if (method.isNullOrBlank()) return PaymentMethod.SYNTHETIC_QR
        return try {
            PaymentMethod.valueOf(method.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException(
                "method inválido: $method (use SYNTHETIC_QR o YAPE_NOTIFICATION)",
            )
        }
    }

    private suspend fun publishSimulated(intent: PaymentIntent): PaymentDto {
        eventBus.publish(PaymentStatusEvent(intent.id, intent.status))
        return toDto(intent)
    }

    private fun toDto(intent: PaymentIntent): PaymentDto {
        val isYape = intent.paymentMethod == PaymentMethod.YAPE_NOTIFICATION
        return PaymentDto(
            id = intent.id,
            reference = intent.reference,
            amountMinor = intent.amountMinor,
            amountDisplay = MoneyFormatter.format(intent.amountMinor),
            currency = intent.currency,
            status = intent.status.name,
            qrUrl = if (isYape) null else "/api/payments/${intent.id}/qr",
            createdAt = intent.createdAt.toString(),
            paidAt = intent.paidAt?.toString(),
            providerOperationReference = intent.providerOperationReference,
            method = intent.paymentMethod.name,
            staticQrUrl = if (isYape && hasStaticQr()) "/api/yape/static-qr" else null,
        )
    }
}
