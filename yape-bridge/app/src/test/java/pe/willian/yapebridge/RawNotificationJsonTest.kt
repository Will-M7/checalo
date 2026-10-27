package pe.willian.yapebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.willian.yapebridge.bridge.RawNotificationJson
import pe.willian.yapebridge.model.CapturedNotification

class RawNotificationJsonTest {

    private fun sample(
        extras: Map<String, String> = mapOf("android.text" to "S/ 10.00"),
        key: String? = "0|pe.culqi.yape|1|null|1000"
    ) = CapturedNotification(
        packageName = "pe.culqi.yape",
        notificationKey = key,
        postedAt = 1758000000000L,
        title = "Pago recibido",
        text = "S/ 25.00",
        bigText = "Pago recibido de Juan por S/ 25.00",
        subText = "Yape",
        summaryText = "Ayer",
        extras = extras
    )

    @Test
    fun `builds the seven allowed fields in order`() {
        val json = RawNotificationJson.of(sample())
        assertEquals(
            "{" +
                "\"packageName\":\"pe.culqi.yape\"," +
                "\"postedAt\":1758000000000," +
                "\"title\":\"Pago recibido\"," +
                "\"text\":\"S/ 25.00\"," +
                "\"bigText\":\"Pago recibido de Juan por S/ 25.00\"," +
                "\"subText\":\"Yape\"," +
                "\"summaryText\":\"Ayer\",\"securityCode\":null" +
                "}",
            json
        )
    }

    @Test
    fun `null fields become json null keeping all keys`() {
        val json = RawNotificationJson.build(
            packageName = "p",
            postedAt = 1L,
            title = null,
            text = "hola",
            bigText = null,
            subText = null,
            summaryText = null
        )
        assertEquals(
            "{\"packageName\":\"p\",\"postedAt\":1," +
                "\"title\":null,\"text\":\"hola\",\"bigText\":null," +
                "\"subText\":null,\"summaryText\":null,\"securityCode\":null}",
            json
        )
    }

    @Test
    fun `never includes extras or notification key`() {
        val json = RawNotificationJson.of(sample())
        assertFalse(json.contains("extras"))
        assertFalse(json.contains("android.text"))
        assertFalse(json.contains("notificationKey"))
        assertFalse(json.contains("0|pe.culqi.yape"))
    }

    @Test
    fun `sends extracted security code without raw extras`() {
        val json = RawNotificationJson.of(sample(extras = mapOf("android.text" to "Código de seguridad: 381")))
        assertTrue(json.contains("\"securityCode\":\"381\""))
        assertFalse(json.contains("android.text"))
        assertFalse(json.contains("extras"))
    }

    @Test
    fun `escapes quotes and backslashes`() {
        val json = RawNotificationJson.build(
            packageName = "p",
            postedAt = 0L,
            title = "dijo \"hola\"",
            text = "ruta C:\\tmp",
            bigText = null,
            subText = null,
            summaryText = null
        )
        assertTrue(json.contains("\"title\":\"dijo \\\"hola\\\"\""))
        assertTrue(json.contains("\"text\":\"ruta C:\\\\tmp\""))
    }

    @Test
    fun `escapes newlines tabs and control chars`() {
        val json = RawNotificationJson.build(
            packageName = "p",
            postedAt = 0L,
            title = "a\nb\tc\u0001",
            text = null,
            bigText = null,
            subText = null,
            summaryText = null
        )
        assertTrue(json.contains("\"title\":\"a\\nb\\tc\\u0001\""))
        assertFalse(json.contains("\n"))
    }

    @Test
    fun `keeps unicode text as is`() {
        val json = RawNotificationJson.build(
            packageName = "p",
            postedAt = 0L,
            title = "Ñandú ¥ 你好",
            text = null,
            bigText = null,
            subText = null,
            summaryText = null
        )
        assertTrue(json.contains("Ñandú ¥ 你好"))
    }

    @Test
    fun `package name is escaped too`() {
        val json = RawNotificationJson.build(
            packageName = "pa\"ckage",
            postedAt = 5L,
            title = null,
            text = null,
            bigText = null,
            subText = null,
            summaryText = null
        )
        assertTrue(json.startsWith("{\"packageName\":\"pa\\\"ckage\",\"postedAt\":5,"))
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun `handles empty and blank strings without dropping keys`() {
        val json = RawNotificationJson.build(
            packageName = "p",
            postedAt = 0L,
            title = "",
            text = " ",
            bigText = null,
            subText = null,
            summaryText = null
        )
        assertTrue(json.contains("\"title\":\"\""))
        assertTrue(json.contains("\"text\":\" \""))
        assertTrue(json.contains("\"bigText\":null"))
    }
}
