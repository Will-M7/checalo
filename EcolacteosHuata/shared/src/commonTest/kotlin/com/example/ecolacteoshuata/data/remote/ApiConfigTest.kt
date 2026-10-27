package com.example.ecolacteoshuata.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiConfigTest {
    private val config = ApiConfig()

    @Test
    fun armaLaUrlBaseDeLaApiMovil() {
        assertEquals("http://192.168.1.50:8000/api/mobile/", config.baseUrl("192.168.1.50", "8000"))
    }

    @Test
    fun limpiaUnHostPegadoDesdeElNavegador() {
        assertEquals("http://10.0.2.2:8000/api/mobile/", config.baseUrl("  http://10.0.2.2:8000/login/ ", " 8000 "))
    }

    @Test
    fun rechazaPuertoOHostInvalidos() {
        assertFailsWith<IllegalArgumentException> { config.baseUrl("192.168.1.50", "abc") }
        assertFailsWith<IllegalArgumentException> { config.baseUrl("192.168.1.50", "70000") }
        assertFailsWith<IllegalArgumentException> { config.baseUrl("   ", "8000") }
    }
}
