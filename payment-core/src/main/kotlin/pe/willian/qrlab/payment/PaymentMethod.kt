package pe.willian.qrlab.payment

/**
 * Método de pago explícito del laboratorio.
 *
 * - SYNTHETIC_QR: QR sintético generado por el laboratorio (flujo original).
 * - YAPE_NOTIFICATION: pago recibido en el teléfono del negocio, detectado
 *   vía notificaciones Android reenviadas por el bridge (sin QR dinámico,
 *   sin payload EMV, sin contacto con Yape).
 */
enum class PaymentMethod {
    SYNTHETIC_QR,
    YAPE_NOTIFICATION,
}
