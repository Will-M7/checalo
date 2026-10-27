package pe.willian.yapebridge

import org.junit.Assert.*
import org.junit.Test
import pe.willian.yapebridge.bridge.BridgeConfig
import pe.willian.yapebridge.bridge.CanonicalProtocol

class BridgeConfigTest {
    @Test fun `fresh install has no server or laptop assumed`() { assertEquals("", BridgeConfig.DEFAULT_BASE_URL) }
    @Test fun `LAN address hostname port and scheme are editable without recompiling`() {
        for (value in listOf("http://10.42.0.1:8000", "http://192.168.1.70:8000", "http://huata.local:9000", "https://pagos.example.test", "https://servidor.local:8443/huata")) {
            assertEquals(value, BridgeConfig.normalizeBaseUrl("  $value///  "))
            assertEquals(value, CanonicalProtocol.secureUrl(value, allowHttp = true))
        }
    }
    @Test fun `release transport never allows cleartext including loopback`() {
        for (value in listOf("http://10.42.0.1:8000", "http://huata.local:8000", "http://127.0.0.1:8000")) assertNull(CanonicalProtocol.secureUrl(value))
        assertEquals("https://huata.example.test", CanonicalProtocol.secureUrl("https://huata.example.test"))
    }
    @Test fun `invalid ports credentials and URL decorations are rejected before persistence`() {
        for (value in listOf("", "  ", "192.168.1.70:8000", "http://", "ftp://huata", "https:///path", "http://huata:0", "http://huata:65536", "https://user:password@huata", "https://huata?token=secret", "https://huata#fragment", "https://huata/a/../b")) assertNull(value, BridgeConfig.normalizeBaseUrl(value))
    }
    @Test fun `only canonical Huata paths are generated`() {
        assertEquals("http://huata.local:8000/api/bridge/notifications", BridgeConfig.rawNotificationUrl("http://huata.local:8000/"))
        assertEquals("http://huata.local:8000/api/bridge/heartbeat", BridgeConfig.healthUrl("http://huata.local:8000/"))
    }
}
