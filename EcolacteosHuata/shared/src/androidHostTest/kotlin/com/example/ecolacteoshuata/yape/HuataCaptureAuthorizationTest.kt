package com.example.ecolacteoshuata.yape
import kotlin.test.*
class HuataCaptureAuthorizationTest {
    @Test fun soloSesionAutorizadaConConsentimientoYPermisoCaptura() {
        assertTrue(ReceptionCaptureGate.allowed(true,true,true,"servidor|usuario","servidor|usuario",1,2))
        assertFalse(ReceptionCaptureGate.allowed(false,true,true,"s","s",1,2))
        assertFalse(ReceptionCaptureGate.allowed(true,false,true,"s","s",1,2))
        assertFalse(ReceptionCaptureGate.allowed(true,true,false,"s","s",1,2))
    }
    @Test fun cambioDeCuentaOServidorYAutorizacionAntiguaSuspendenCaptura() {
        assertFalse(ReceptionCaptureGate.allowed(true,true,true,"otro-servidor","servidor",1,2))
        assertFalse(ReceptionCaptureGate.allowed(true,true,true,"otro-usuario","usuario",1,2))
        assertFalse(ReceptionCaptureGate.allowed(true,true,true,"s","s",1,ReceptionCaptureGate.MAX_OFFLINE_AGE_MS+2))
        assertFalse(ReceptionCaptureGate.allowed(true,true,true,"s","s",10,9))
    }
}
