# Yape Bridge de Huata

Fuentes Android nativas. Requiere JDK 21 y Android SDK 36. Configura el SDK con `ANDROID_HOME`
o un `local.properties` privado (no versionado). Ejecuta `./gradlew testDebugUnitTest assembleDebug`.
La APK queda en `app/build/outputs/apk/debug/app-debug.apk`; no se instala automáticamente.

No se incluyen claves de firma privadas ni credenciales de dispositivos. Ambos APK, Huata y
Bridge, deben compartir un certificado controlado para el IPC protegido por firma. Android
puede generar su debug keystore local; nunca desinstales una instalación existente para forzar
una firma incompatible. Lee `CANONICAL.md` y la documentación de pagos Laravel. La inscripción
individual proviene de Configuración de pagos; no se compila una APK por vendedor.
