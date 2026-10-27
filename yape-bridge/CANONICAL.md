# Yape Bridge → Huata por WiFi

Configura en la tarjeta BACKEND la URL completa del servidor **Laravel Huata**, con protocolo, IP/hostname y puerto. No existe servidor por defecto ni IP compilada de una laptop. Se conserva en SharedPreferences al reiniciar. Puede ser otra computadora o un servidor dedicado siempre que exista una ruta de red hacia él. No se requiere USB ni un túnel ADB.

El build debug permite HTTP para la red de pruebas; release exige HTTPS con certificado válido. No se deshabilita validación TLS. No incluyas credenciales en la URL. El puerto local habitual de Laravel es 8000; el operador puede elegir otro. El Bridge no debe apuntar a Payment Core: 8087 continúa privado para Laravel.

El teléfono se vincula con la referencia del receptor y su credencial **de dispositivo**, emitida en la configuración comercial privada del motor. Una clave de aplicación Laravel no sirve como clave de dispositivo. No se genera ni se habilita una identidad real desde la APK.

## Contrato único

- POST `{baseHuata}/api/bridge/heartbeat`, Bearer de dispositivo y `{listenerConnected: boolean}`. Una prueba exitosa exige `accepted: true`, `coreMode: commercial` y `receiptOnly: true`.
- POST `{baseHuata}/api/bridge/notifications`, mismo Bearer, evento estable ligado al receptor, paquete Yape e instante original. Un envío solo pasa a SENT si recibe un ACK JSON con booleano exacto `accepted: true` y el mismo `eventId`. SENT significa recepción, nunca PAID.
- Laravel usa su URL interna existente de Payment Core y comprueba modo comercial. El Core autentica la clave del dispositivo y aplica receptor, importe, tiempo, código, unicidad y ambigüedad a las mismas intenciones PAY usadas por Huata. Laravel no construye un motor nuevo ni marca pagos por recibir notificaciones. Huata mantiene su consulta canónica autenticada antes de aplicar fondos.
- El flujo antiguo YW/raw-notification de laboratorio no es el destino del Bridge. Su almacenamiento y sus endpoints no confirman intenciones comerciales.

## Persistencia y reconexión

Solo se procesan abonos reconocidos del paquete oficial configurado `com.bcp.innovacion.yapeapp`. Se descartan otras aplicaciones y resúmenes de grupo. Se releen notificaciones activas de Yape cuando se reconecta el listener; callbacks repetidos conservan el mismo ID de receptor/paquete/clave/fecha.

Antes de enviar se inserta en SQLite la evidencia mínima cifrada mediante Android Keystore AES-GCM. No se transmiten extras ni claves de notificación; la UI recibe texto redactado y contadores. La cola se conserva ante WiFi ausente, servidor apagado, error de autenticación, receptor diferente, formato rechazado, timeout o ACK perdido. Un ACK válido vacía el contenido sensible de la fila, conservando la marca de deduplicación. Registros previos de laboratorio permanecen en LEGACY_REVIEW y no se reasignan silenciosamente.

WorkManager utiliza conectividad y backoff exponencial para el envío durable. Una tarea periódica de seguridad de 15 minutos evita que una inserción al terminar otra tarea deje evidencia abandonada. Un callback de red, el listener, los cambios de configuración y el heartbeat de la app despiertan también la cola. Android puede retrasar tareas por Doze o restricciones del fabricante: no se garantiza transmisión instantánea. No se borran datos ni se recrea el esquema de la cola.

La tarjeta muestra conexión autenticada, pendientes, registros de otro receptor/revisión y errores sin contenido sensible. El permiso del listener se concede manualmente en Ajustes; no se concede automáticamente.

## Instalación y validación

Una actualización debe conservar applicationId y firma de la variante ya instalada. Nunca desinstalar ni borrar datos para forzar la instalación. Debug tiene sufijo `.debug`. La compilación no instala ni concede permisos.

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/willian/Android/Sdk ./gradlew --offline :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug`.

Se documentarán las pruebas ejecutadas y las verificaciones físicas pendientes en el informe de entrega de Huata. Las muestras sintéticas no certifican una transferencia bancaria. Mantener deshabilitado cualquier receptor sin verificación física de cuenta/QR/dispositivo. La APK de Yape y el formato de notificaciones reales pueden cambiar; una notificación no reconocida requiere revisar el formato sin inventar códigos.
