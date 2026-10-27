package pe.willian.yapebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.model.YapeSecurityCodeExtractor

class YapeSecurityCodeExtractorTest {
    private fun notification(vararg fields: String?): CapturedNotification = CapturedNotification(
        packageName = "pe.culqi.yape",
        notificationKey = null,
        postedAt = 1L,
        title = fields.getOrNull(0),
        text = fields.getOrNull(1),
        bigText = fields.getOrNull(2),
        subText = fields.getOrNull(3),
        summaryText = fields.getOrNull(4),
    )

    @Test fun `codigo de seguridad con acento`() = assertEquals("381", YapeSecurityCodeExtractor.extract(notification("Código de seguridad: 381")))
    @Test fun `codigo con cero inicial`() = assertEquals("007", YapeSecurityCodeExtractor.extract(notification("Código: 007")))
    @Test fun `codigo sin acento`() = assertEquals("999", YapeSecurityCodeExtractor.extract(notification("codigo de seguridad 999")))
    @Test fun `monto no es codigo`() = assertNull(YapeSecurityCodeExtractor.extract(notification("S/ 381.00")))
    @Test fun `operacion no es codigo`() = assertNull(YapeSecurityCodeExtractor.extract(notification("Operación 381")))
    @Test fun `sin codigo`() = assertNull(YapeSecurityCodeExtractor.extract(notification("Pago recibido")))

    @Test fun `codigos contradictorios en textLines y extras requieren revision`() {
        val value = notification("Pago").copy(
            textLines = listOf("Código: 123"),
            extras = mapOf("custom" to "Código de seguridad: 456"),
        )
        assertNull(YapeSecurityCodeExtractor.extract(value))
    }
    @Test fun `codigo identico repetido en campos autenticos sigue siendo unico`() {
        assertEquals("381",YapeSecurityCodeExtractor.extract(notification("Código: 381","Código de seguridad: 381")))
        assertNull(YapeSecurityCodeExtractor.extract(notification("Código: 381 Código: 492")))
    }
}
