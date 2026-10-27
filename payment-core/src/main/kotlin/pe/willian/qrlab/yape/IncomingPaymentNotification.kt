package pe.willian.qrlab.yape

import java.time.Instant

/**
 * Origen declarado de una notificación (MODELO 2).
 *
 * NO se inventan formatos: hasta tener evidencia de una transferencia
 * bancaria real, todo lo que no encaje en el formato Yape observado queda
 * en UNKNOWN. BANK_TRANSFER está reservado (aún sin evidencia).
 */
enum class YapeSourceType {
    /** Formato Yape observado o código de seguridad de 3 dígitos presente. */
    YAPE_NATIVE,
    /** Reservado: no se implementa sin evidencia de un formato real. */
    BANK_TRANSFER,
    /** Formato no reconocido: se conserva sin asumir nada. */
    UNKNOWN,
}

/** Estado de match de una notificación entrante (MODELO 2). */
enum class YapeMatchStatus {
    /** 0 candidatos compatibles. */
    UNMATCHED,
    /** 2+ candidatos: no se marca nada PAID sin resolución explícita. */
    AMBIGUOUS,
    /** Exactamente 1 candidato (auto match o resolución). */
    MATCHED,
    /** Descartada antes del matching (package no permitido / diagnóstico). */
    IGNORED,
}

/**
 * Notificación de pago entrante, normalizada (MODELO 2).
 *
 * PRIVACIDAD: aquí NUNCA existe raw text ni código en plano.
 * - [payerName] se guarda ya normalizado (minúsculas, sin acentos,
 *   espacios simples) y únicamente si el formato Yape lo reveló.
 * - [securityCodeHmac] es HMAC-SHA256 con YAPE_MATCH_SECRET; jamás el
 *   código de 3 dígitos.
 *
 * Persistencia permitida (máximo): amount, payerName, HMAC, timestamps,
 * package y estado de match.
 */
data class IncomingPaymentNotification(
    val id: String,
    val bridgeEventId: String? = null,
    val amountMinor: Long,
    /** Nombre normalizado del pagador (null si el formato no lo reveló). */
    val payerName: String? = null,
    /** HMAC-SHA256 del código de 3 dígitos (null si no había código). */
    val securityCodeHmac: String? = null,
    val receivedAt: Instant,
    val sourcePackage: String,
    val sourceType: YapeSourceType,
    val matchStatus: YapeMatchStatus,
    /** Venta pagada por esta notificación (solo MATCHED). */
    val matchedPaymentId: String? = null,
)
