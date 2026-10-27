package com.example.ecolacteoshuata.domain.platform

import android.os.Build

actual fun deviceInfo(): DeviceInfo = DeviceInfo(
    plataforma = "Android",
    modelo = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
    versionSistema = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
)
