package com.example.ecolacteoshuata.domain.platform

/** Datos del teléfono para "Acerca de este dispositivo" (perfil) y para dar soporte. */
data class DeviceInfo(
    val plataforma: String,
    val modelo: String,
    val versionSistema: String,
) {
    val resumen: String get() = "$modelo — $plataforma $versionSistema"
}

/** expect: cada plataforma aporta su actual (DeviceInfo.android.kt / DeviceInfo.ios.kt). */
expect fun deviceInfo(): DeviceInfo
