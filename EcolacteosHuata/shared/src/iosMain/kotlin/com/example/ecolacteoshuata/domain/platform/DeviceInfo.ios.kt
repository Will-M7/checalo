package com.example.ecolacteoshuata.domain.platform

import platform.UIKit.UIDevice

actual fun deviceInfo(): DeviceInfo {
    val dispositivo = UIDevice.currentDevice
    return DeviceInfo(
        plataforma = dispositivo.systemName,
        modelo = dispositivo.model,
        versionSistema = dispositivo.systemVersion,
    )
}
