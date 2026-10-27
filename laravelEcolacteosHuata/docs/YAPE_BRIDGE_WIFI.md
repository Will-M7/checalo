> Actualización final 27/10/2026: consultar [EJECUCION_FINAL_PAGOS_YAPE.md](EJECUCION_FINAL_PAGOS_YAPE.md) para Core desplegado, APK nuevas, inscripción, identidad y límites operativos. Este informe conserva el estado histórico de la entrega WiFi; sus cifras y afirmaciones sobre Core/configuración corresponden a aquella entrega.

# Yape Bridge por WiFi — entrega 27/10/2026

## 1. Causas corregidas

- Bridge tenía `http://127.0.0.1:8087` como destino inicial: en un teléfono es el propio teléfono. El manifiesto debug permitía HTTP, pero `CanonicalProtocol.secureUrl` rechazaba IP/hostname LAN en HTTP. Una preferencia LAN guardada podía ignorarse al reiniciar y reaparecer el destino de loopback.
- El destino anterior era directamente Payment Core. Laravel no tenía un ingreso autenticado para los eventos del Bridge; por tanto la URL de Laravel en 8000 no podía funcionar.
- El listener y la cola cifrada ya existían. Se conservaron y se completaron recuperación de notificaciones activas, reconexión del listener, despertar por conectividad, respaldo periódico de WorkManager, contadores y mensajes claros. Un evento agregado mientras termina otra tarea ya cuenta con reintentos posteriores persistentes.
- La separación histórica PAY/YW **ya estaba resuelta en el modo comercial vigente**: se comprobó `commercialModule` y `CanonicalPayments`. Se mantuvo ese motor; no se creó otro ni se modificaron sus reglas. Sus rutas de laboratorio no están montadas en modo comercial. El nuevo ingreso rechaza un backend cuyo `/health` declare laboratorio, incluso si ofrece una ruta con el mismo nombre.

## 2. Flujo definitivo y contrato

Yape oficial → NotificationListenerService del receptor → cola SQLite cifrada → Laravel Huata configurado → Payment Core comercial privado → consulta canónica de Huata.

| Operación | Ingreso público de Laravel | Contrato |
|---|---|---|
| Probar conexión / heartbeat | POST `/api/bridge/heartbeat` | Bearer de **dispositivo**, JSON `listenerConnected`; ACK exige booleano `accepted=true`, `coreMode=commercial`, `receiptOnly=true` |
| Transmitir evento | POST `/api/bridge/notifications` | Misma credencial, ID estable, receptor, paquete permitido, instante original y textos mínimos/código observado; solo ACK booleano exacto e ID correspondiente permite marcar SENT |

Laravel valida formato/tamaño, limita solicitudes, descarta campos ajenos y comprueba modo comercial; remite la credencial del dispositivo al Core para autenticar su identidad y vínculo con el receptor. **No sustituye esa clave por la clave de aplicación.** No registra secretos ni devuelve cuerpos/errores privados del motor. Las redirecciones están deshabilitadas en ambos transportes.

El Core exige dispositivo, paquete, receptor, importe exacto, ventana temporal, código observado/introducido, unicidad e idempotencia. Sus estados comerciales siguen siendo PENDING/PAID/EXPIRED/CANCELLED y el resultado AMBIGUOUS mantiene PENDING. Recibir 200/202 o `accepted=true` acredita **recepción de evidencia**, nunca dinero. Huata mantiene su consulta autenticada y validación de referencia, importe, moneda, receptor, QR e identificador PAY antes de aplicar dinero/venta/stock. No se cambió CobrosService ni los servicios comerciales.

## 3. URL, puerto y configuración entre reinicios

La tarjeta BACKEND acepta la URL completa de **Huata**: protocolo, IP o hostname, puerto y prefijo de despliegue si lo hubiera. Se guarda en las preferencias existentes y se lee al enviar/reintentar. No hay servidor predeterminado, IP de una laptop ni puerto comercial directo compilado. Cambiar red, IP o hostname no requiere recompilar. La credencial y el receptor se conservan cifrados en el dispositivo.

Ejemplos, únicamente como ejemplos: `http://10.42.0.1:8000`, `http://192.168.1.70:8000`, `https://huata.example.test`.

Debug admite HTTP para pruebas LAN. Release exige HTTPS con certificado válido; no se desactiva validación TLS. URLs con credenciales, query, fragmento o puertos inválidos se rechazan antes de guardar. Una URL loopback provoca una advertencia de que apunta al propio teléfono.

La pantalla técnica Bridge conserva sus componentes/colores y usa desplazamiento normal para poder alcanzar configuración y permisos en teléfonos pequeños. No cambió ninguna pantalla, color, rol o distribución de Huata web/Android.

## 4. Estado real del servidor observado, sin reiniciar

- Laravel ya escuchaba en `0.0.0.0:8000` (PID 106909 al comprobar).
- Payment Core ya escuchaba únicamente en loopback IPv4 mapeado `[::ffff:127.0.0.1]:8087` (PID 106822). **No se expuso 8087 por LAN.**
- Su GET `/health` operativo respondió HTTP 200 con `mode=commercial`, `status=ok`. Solo se hizo esa consulta de lectura; no se enviaron eventos, credenciales ni heartbeats al motor operativo.
- Direcciones IPv4 locales observadas: Ethernet `192.168.1.70` y WiFi `192.168.1.75`. Pueden cambiar y no se grabaron en el código ni en la APK. El teléfono debe poder alcanzar la dirección elegida, sin aislamiento entre clientes/firewall que lo impida.
- Laravel conserva `PAYMENT_CORE_ENABLED=false`, `PAYMENT_CORE_URL=http://127.0.0.1:8087`, clave de aplicación ausente y `PAYMENT_CORE_ALLOW_LOCAL_HTTP=false`. No hay caché de configuración ni de rutas activa. Las dos rutas nuevas aparecen registradas.

**La integración operativa Laravel–Core todavía no está habilitada.** El ingreso devuelve `503 COMMERCIAL_LINK_PENDING`, no un ACK inventado, cuando se intenta usarlo con esa configuración. Que el Core responda /health no significa que Laravel esté vinculado ni que un receptor esté verificado.

Requisitos pendientes del operador, sin generar ni cambiar claves reales en esta entrega:

1. Reutilizar la clave real del cliente Huata autorizado en el archivo comercial privado del Core para configurar `PAYMENT_CORE_KEY`, y habilitar explícitamente `PAYMENT_CORE_ENABLED`.
2. Conservar Core en loopback y configurar `PAYMENT_CORE_ALLOW_LOCAL_HTTP=true` en Laravel para esa conexión interna HTTP; en el Core su transporte local debe permitir loopback (`allowLocalHttp`). En una topología distinta, usar el transporte privado/TLS correspondiente, no una IP de desarrollo compilada.
3. Vincular el Bridge con **su clave de dispositivo**, diferente de la de aplicación, y la referencia exacta de su receptor. Esos datos deben corresponder a la configuración comercial existente; no se inventaron valores.
4. Verificar físicamente cuenta Yape, QR, teléfono y dispositivo antes de habilitar el receptor en ambos extremos. No se activaron receptores ni se cambió su estado. La configuración operativa concreta de receptores no se leyó ni se certificó.
5. Configurar en el Bridge una URL Huata alcanzable por la red del teléfono. No basta con que las direcciones sean válidas si no hay ruta/red/escucha.

No se modificaron comandos de arranque, unidades systemd, IP, puertos, credenciales, datos operativos o servicios manuales. Las pruebas solo arrancaron procesos temporales propios en puertos efímeros, y los terminaron en `finally`.

## 5. Cola, listener y reintentos

Se conserva SQLite versión 3 y el cifrado Android Keystore AES-GCM. **No hubo migración ni borrado de cola.** Se procesa únicamente `com.bcp.innovacion.yapeapp`, tras reconocer un abono entrante; se ignoran otras aplicaciones y resúmenes de grupo. No se guardan/transmiten extras. Se mantienen instante original e identidad estable al reenviar. Las notificaciones activas de Yape se vuelven a leer al recuperar el listener y se deduplican con esa identidad.

WorkManager conserva tareas con conectividad y backoff exponencial, y una tarea periódica de seguridad de 15 minutos. La reconexión de red, el listener y los cambios de configuración despiertan también el envío. El heartbeat periódico informa del estado real conocido del listener; reenviar una evidencia no finge que este siga conectado. Android/OEM pueden retrasar trabajos por batería/Doze: no se garantiza envío inmediato ni captura mientras el permiso/servicio estén impedidos por el sistema.

Ante servidor apagado, WiFi ausente, timeout, 401/403/409/429/503, formato inválido o ACK incorrecto, el evento permanece pendiente. Un ACK perdido causa reenvío idempotente. Una entrega aceptada limpia el contenido sensible local pero mantiene la marca de deduplicación; el Core conserva la evidencia para conciliación/revisión. Evidencia sin código, antigua, ambigua o de receptor no verificado no equivale a PAID. Registros legados y de otro receptor se conservan para revisión; no se reasignan silenciosamente.

Los contadores muestran pendientes del receptor y registros de otros receptores/legados. “Limpiar registros” limpia solo el historial visual en memoria, no la cola durable. Los datos redacted de la UI no sustituyen el resultado del Core.

## 6. Pruebas ejecutadas

- Bridge: **49 pruebas debug y las mismas 49 release**, cero fallos. Se probaron normalización de IP/hostname/puerto/protocolo, ausencia de destino predeterminado, HTTPS de release, URLs inválidas, transporte HTTP real JVM, cambio entre dos destinos, pérdida de conexión/503 y reintento con el mismo cuerpo, ACK exacto, filtro de origen, código observado y bloqueo de redirecciones con credenciales.
- Laravel: **469 pruebas de regresión aprobadas** (1973 aserciones). La suite general omite deliberadamente las cuatro que requieren Core temporal. Incluye el gestor de distribuciones y los módulos existentes.
- Integración aislada: **esas cuatro pruebas se ejecutaron por separado y aprobaron, 48 aserciones**, con el Core comercial real en SQLite temporal, claves/QR/cuentas ficticios y Laravel en SQLite en memoria. Hubo un PAY real del motor de pruebas, ACK sin acreditar dinero, correlación exacta y una sola venta, repetición sin doble stock, evento sin intención, dos intenciones ambiguas, clave de aplicación rechazada como dispositivo y receptor ficticio no verificado en revisión. Las rutas LAB/YW dieron 404.
- Se ejecutó además **BridgeHttpTransport realmente compilado → dos servidores HTTP Laravel temporales → un único Core temporal**, cambiando hostname/puerto sin recompilar. El mismo evento obtuvo OBSERVED/DUPLICATE y no un pago automático. El runner termina sus procesos y retira las claves ficticias.
- Core: **248 pruebas existentes reejecutadas y aprobadas**, cero fallos; no se modificó su código. `test installDist` y posteriormente `test --rerun-tasks` terminaron correctamente.
- Compilación final Bridge: `:app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**. Firma del APK verificada con apksigner. Pint, sintaxis del runner y `git diff --check`: correctos.

Reproducir la integración acotada: `python3 tools/test_bridge_wifi.py` desde Laravel, después de compilar Bridge y preparar `installDist` del Core. El runner exige loopback/puertos efímeros para las pruebas y no utiliza la base/configuración comercial operativa. Se pueden configurar rutas de proyectos de pruebas mediante `BRIDGE_TEST_CORE_PROJECT` y `BRIDGE_TEST_ANDROID_PROJECT`.

| Escenario solicitado | Evidencia | Validación física pendiente |
|---|---|---|
| A. Receptor/servidor en la misma WiFi | Transporte/ingreso completos por HTTP real; escucha LAN actual comprobada | Usar la nueva APK y WiFi del teléfono |
| B. Otro servidor en la red | Cambio entre dos instancias Laravel temporales y puertos, mismo contrato | Probar una segunda computadora física |
| C. Cambio de IP/hostname sin recompilar | Cliente compilado usado con destinos diferentes; preferencias existentes | Verificar guardado/reinicio en dispositivo |
| D. WiFi desconectada/reconectada | Fallo y reintento conservan ID/cuerpo; WorkManager/callback configurados | Radio WiFi real, Doze y fabricante |
| E. Servidor apagado | ServerSocket cerrado/503 conserva el evento; timeout del ingreso sin ACK | Reiniciar manualmente solo cuando lo decida el operador |
| F. Reenvío sin duplicación | ACK perdido/repetido, DUPLICATE por dos ingresos, una sola venta | Notificación/callback auténtico repetido |
| G. Notificación válida | Payload sintético compatible pasa por el Core real aislado | Notificación auténtica y formato vigente de Yape |
| H. Ninguna intención coincidente | Evidencia recibida, cero ventas/saldo | Verificación física complementaria |
| I. Dos pagos competidores | Ambos PENDING/AMBIGUOUS, sin acreditar dinero | No confirmar arbitrariamente en operación real |

Las pruebas de red usaron loopback aislado; **no se afirma haber probado estos escenarios físicos en los teléfonos ni otra computadora**. No se instaló nada ni se usó un túnel ADB como conexión.

## 7. APK e instalación manual sin borrar datos

- APK: [/home/willian/Desarrollo/laboratorio/yape-bridge/app/build/outputs/apk/debug/app-debug.apk](/home/willian/Desarrollo/laboratorio/yape-bridge/app/build/outputs/apk/debug/app-debug.apk)
- Tamaño: 11924277 bytes.
- SHA-256: `c362afc768a768675a8a4b0088e6a200abc39832ee7bffd9f24259f5587cc730`.
- Variante: `pe.willian.yapebridge.debug`, minSdk 26, targetSdk 36; se conservaron versión/applicationId y firma del proyecto.
- Firma debug comprobada: certificado SHA-256 `53bb3b0f9c147ec628c1111ab06621030e999c1b0d3d58f7a06931e6e60004ed`.

Comprobar primero la variante instalada (diagnóstico opcional, no ejecutado en esta entrega):

```bash
adb -s 115272549K001710 shell pm list packages --show-versioncode pe.willian.yapebridge
```

Para actualizar **la misma variante debug y firma** en el Infinix indicado, sin desinstalar ni borrar datos:

```bash
adb -s 115272549K001710 install -r "/home/willian/Desarrollo/laboratorio/yape-bridge/app/build/outputs/apk/debug/app-debug.apk"
```

El comando se entrega para ejecución manual; **no fue ejecutado**. Si la instalada es `pe.willian.yapebridge` sin `.debug`, este APK es otra variante: no actualiza sus preferencias. Usar un APK de esa variante firmado con su clave original. Si Android rechaza firma/versión, no desinstalar ni borrar datos para forzarlo. No se certificó qué variante/firma tiene instalado actualmente el teléfono.

USB/ADB solo intervienen si el operador decide usar ese comando de instalación. Una vez configurado, el envío opera por la red hacia Huata sin cable ni túnel.

## 8. Permisos manuales y prueba auténtica pendiente

1. Abrir Bridge y configurar/guardar URL del servidor alcanzable y credencial/receptor técnico correctos.
2. Conceder **acceso especial a notificaciones** desde el botón de Ajustes del Bridge y comprobar LISTENER ACTIVE. En versiones que bloquean apps instaladas manualmente, permitir antes “ajustes restringidos” en Información de la app.
3. Mantener WiFi/red con ruta al servidor. Revisar las restricciones de batería/ejecución automática de Infinix si impiden el listener o los trabajos en segundo plano. No se alteraron esos ajustes.
4. Probar manualmente cambio de red, reinicio, servidor temporalmente inaccesible y reenvío; confirmar que los pendientes reaparecen y no se duplican.
5. Verificar cuenta/QR/receptor y una notificación real con su permiso, paquete, importe, instante y código auténticos. Mantener la protección si no hay código o existe ambigüedad. No introducir uno inventado ni usar el teléfono del comprador como prueba bancaria.
6. Solo tras la verificación y configuración comercial autorizadas, comprobar que el PAY correcto cambia por consulta canónica y que la venta/saldo se aplica una vez. Esta entrega **no certifica un pago bancario real**.

## 9. Archivos de esta tarea

Laravel:

- [app/Services/Payments/BridgeIngress.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Services/Payments/BridgeIngress.php)
- [app/Http/Controllers/Api/YapeBridgeController.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Http/Controllers/Api/YapeBridgeController.php)
- [routes/api.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/routes/api.php)
- [tests/Feature/YapeBridgeIngressTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/YapeBridgeIngressTest.php)
- [tests/Feature/YapeBridgeIntegrationTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/YapeBridgeIntegrationTest.php)
- [tools/test_bridge_wifi.py](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tools/test_bridge_wifi.py)
- [docs/YAPE_BRIDGE_WIFI.md](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/docs/YAPE_BRIDGE_WIFI.md)

Bridge:

- [app/src/main/java/pe/willian/yapebridge/bridge/BridgeConfig.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/bridge/BridgeConfig.kt)
- [app/src/main/java/pe/willian/yapebridge/bridge/CanonicalProtocol.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/bridge/CanonicalProtocol.kt)
- [app/src/main/java/pe/willian/yapebridge/bridge/BackendSettings.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/bridge/BackendSettings.kt)
- [app/src/main/java/pe/willian/yapebridge/bridge/BackendBridge.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/bridge/BackendBridge.kt)
- [app/src/main/java/pe/willian/yapebridge/bridge/OutboxWorker.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/bridge/OutboxWorker.kt)
- [app/src/main/java/pe/willian/yapebridge/store/OutboxDatabase.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/store/OutboxDatabase.kt)
- [app/src/main/java/pe/willian/yapebridge/store/BridgeStore.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/store/BridgeStore.kt)
- [app/src/main/java/pe/willian/yapebridge/listener/LabNotificationListener.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/listener/LabNotificationListener.kt)
- [app/src/main/java/pe/willian/yapebridge/MainActivity.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/MainActivity.kt)
- [app/src/main/AndroidManifest.xml](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/AndroidManifest.xml)
- [app/src/debug/AndroidManifest.xml](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/debug/AndroidManifest.xml)
- [app/build.gradle.kts](/home/willian/Desarrollo/laboratorio/yape-bridge/app/build.gradle.kts)
- [app/src/test/java/pe/willian/yapebridge/BridgeConfigTest.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/test/java/pe/willian/yapebridge/BridgeConfigTest.kt)
- [app/src/test/java/pe/willian/yapebridge/CanonicalProtocolTest.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/test/java/pe/willian/yapebridge/CanonicalProtocolTest.kt)
- [CANONICAL.md](/home/willian/Desarrollo/laboratorio/yape-bridge/CANONICAL.md)
- [app/src/main/java/pe/willian/yapebridge/bridge/BridgeHttpTransport.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/main/java/pe/willian/yapebridge/bridge/BridgeHttpTransport.kt)
- [app/src/test/java/pe/willian/yapebridge/BridgeWifiTransportTest.kt](/home/willian/Desarrollo/laboratorio/yape-bridge/app/src/test/java/pe/willian/yapebridge/BridgeWifiTransportTest.kt)

Payment Core: **ningún archivo fuente, configuración o dato operativo modificado**. Solo se compilaron/ejecutaron sus pruebas y una instancia temporal con datos ficticios. La APK Huata/KMP, roles, distribuciones, interfaces y operaciones comerciales no cambiaron. No se realizó commit/push, instalación, borrado de teléfonos ni reinicio de servicios manuales.
