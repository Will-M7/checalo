# Recepción Yape integrada en Huata — 27/10/2026

> Actualización posterior: [Recepción Yape con administración central y solicitud Android](SIMPLIFICACION_RECEPCION_YAPE.md). Sustituye la activación por iniciativa del vendedor, Configuración de pagos y los controles de pausa del vendedor descritos en este informe histórico.

## Resultado

Una sola APK Huata contiene el listener Yape, captura filtrada, cola local cifrada y reintentos persistentes. La recepción requiere autorización administrativa de la instalación, sesión Huata compatible y consentimiento local explícito. El permiso del sistema, por sí solo, no concede autorización del backend.

El paquete continúa siendo `com.example.ecolacteoshuata.huata`. No se incorporaron claves privadas ni credenciales permanentes de Core en la APK. La credencial de dispositivo Core permanece cifrada en Laravel; cada envío Android utiliza la sesión móvil habitual y una identidad aleatoria de instalación aprobada por administración.

Se conservaron todos los avances de Finanzas, filtros, disponibilidad comercial, usuarios, roles y distribuciones. No hubo commits, pushes, instalaciones, reinicios de servicios ni transferencias monetarias reales.

## Reutilización y trayecto

Se adaptaron desde el código fuente de Bridge, sin modificar el proyecto original:

- `CapturedNotification`, `YapeSecurityCodeExtractor` y el mapeo de campos de `NotificationListenerService`.
- `CanonicalProtocol`: paquete real permitido `com.bcp.innovacxion.yapeapp`, identificación estable por receptor/notificación/fecha y formato canónico. Un importe no se interpreta como código.
- `RawNotificationJson`, `CanonicalDelivery` y `BridgeOutboxEvent`: contenido fiel, deduplicación y comprobación del ACK exacto.
- `OutboxDatabase` y cifrado AES-GCM con Android Keystore. Se usa un archivo nuevo de Huata en `noBackupFilesDir`; no se importa ni intenta descifrar el almacenamiento de otra APK.

No se copió la interfaz de Bridge ni su configuración manual. Los textos e importes originales se entregan al parser existente de Core; Android no añade una segunda lógica de importes conciliados, pagos o saldos.

Trayecto: **Huata Android → API móvil autenticada Laravel → BridgeIngress/BridgeProvisioning existentes → Payment Core comercial**. Laravel crea las pruebas/grants personales existentes y utiliza la credencial de dispositivo únicamente en su tramo hacia Core. Se conservan importe, receptor, tiempo, evidencia única, revisión por ambigüedad/sin código y consulta canónica existentes. Un ACK de recepción no acredita dinero.

## Componentes Android y ciclo de vida

Servicio integrado:

`EcolacteosHuata/shared/src/androidMain/kotlin/com/example/ecolacteoshuata/yape/HuataYapeListener.kt`

Está declarado en el manifiesto de la aplicación con `BIND_NOTIFICATION_LISTENER_SERVICE` y la acción oficial `android.service.notification.NotificationListenerService`. Solo Android puede vincularlo bajo ese permiso. El manifiesto compilado fue inspeccionado.

`HuataYapeReception` reutiliza conexión, sesión y cliente HTTP de Huata, con redirecciones deshabilitadas para estos envíos. No solicita servidor, IP, códigos de inscripción, contraseña Yape ni credenciales de dispositivo al usuario. `HuataYapeWorker` combina trabajo inmediato con reintento exponencial y un trabajo periódico único cada 15 minutos con restricción de red. No depende de una pantalla en primer plano.

El listener comprueba autorización local/sesión/consentimiento antes de mapear campos y de nuevo antes de escribir la cola. Descarta otras aplicaciones y resúmenes de grupos. No relee notificaciones anteriores al consentimiento. Guarda primero el evento cifrado y después solicita envío.

La cola se separa por servidor y usuario; cada registro conserva además su receptor original. No se atribuyen eventos de otra cuenta al cambiar de sesión o servidor. Se conservan eventos sin código y formatos desconocidos para revisión. Un rechazo definitivo de formato queda en revisión local cifrada; los errores temporales mantienen el evento pendiente. Un ACK válido de revisión conserva el contenido cifrado; un ACK normal elimina el texto local confirmado, conservando la identidad de deduplicación.

Sin conexión se permite captura local con la última autorización compatible durante un máximo de 24 horas. No se transmiten eventos sin validación real del backend. Una revocación conocida o una sesión incompatible suspende la captura y el envío. No es posible prometer revocación instantánea mientras el teléfono permanece sin red; el backend rechaza cada transmisión revocada al reconectar.

Android controla el ciclo del listener y WorkManager. Doze, restricciones del fabricante y una detención forzada pueden retrasar o detener trabajo; no se fuerza su ejecución ni se eluden las restricciones del sistema.

## Autorización y aprovisionamiento automático

Única tabla nueva: `huata_yape_installations`, creada por `2026_10_27_220000_add_huata_yape_installations.php`. Vincula instalación aleatoria, usuario autorizado, sesión actual, receptor, modalidad, aprobador, estado y dispositivo Bridge existente. Conserva auditoría y referencias; el rollback no elimina registros.

La sesión crea una solicitud pendiente con la marca/modelo del teléfono. No se recopilan IMEI, serial ni identificadores publicitarios. El administrador autoriza una solicitud en Configuración de pagos → Dispositivos Huata autorizados. No se concede autorización automáticamente por ser vendedor o receptor financiero.

Tras la autorización y el consentimiento/permiso de Android, Huata reclama la inscripción automáticamente. Laravel reutiliza las operaciones de alta existentes, mantiene la misma clave/referencia al reintentar un alta y recupera un ACK perdido incluso después de vencer la invitación original. Las claves nunca se devuelven al teléfono. La verificación financiera del titular, cuenta y QR continúa siendo explícita; la inscripción no verifica ni habilita dinero por sí sola.

Las nuevas referencias técnicas se generan automáticamente cuando faltan; no se cambian las ya existentes. Se bloquean dos instalaciones Huata autorizadas simultáneamente para el mismo receptor y los colectores anteriores sin correspondencia única.

Endpoints nuevos, bajo el middleware móvil existente:

- `POST /api/mobile/yape/status`
- `POST /api/mobile/yape/activate`
- `POST /api/mobile/yape/heartbeat`
- `POST /api/mobile/yape/evidence`

La revocación administrativa se aplica primero en Laravel y rechaza nuevos envíos; conserva registros y cola. La autorización de dispositivos exige el alcance administrativo existente de configuración de pagos. El propietario/usuario autorizado para escuchar no recibe capacidad comercial adicional.

## Modalidades y configuración Web

| Selección | Quién escucha |
|---|---|
| Mi receptor | El titular autorizado, con sesión personal compatible |
| Receptor de otro usuario autorizado | El responsable del receptor compartido; los vendedores que lo usan no necesitan listener |
| Receptor central | El dispositivo autorizado de la cuenta central; su propietario no necesita ser vendedor |

La pantalla mantiene su diseño y presenta un selector principal. Los campos propios o compartidos aparecen según la modalidad. El vendedor puede cambiar únicamente entre receptores/modalidades previamente autorizados; no puede autorizarse otra cuenta. Administración puede conservar permiso personal adicional al autorizar una cuenta compartida. Las asignaciones previas siguen funcionando y los pedidos/intenciones históricos no cambian.

Las descargas/enlaces de Bridge externo se retiraron del flujo del vendedor. Se conservan en una sección administrativa plegada de mantenimiento para instalaciones anteriores. El administrador puede seleccionar propietarios financieros aunque no tengan capacidad de venta.

## Uso en el teléfono y transferencia del Bridge anterior

1. Actualizar Huata conservando sus datos e iniciar sesión con la conexión de Huata ya configurada. Al abrir la configuración o ejecutarse la sincronización de la recepción aparece la solicitud del teléfono en Laravel.
2. El administrador abre Configuración de pagos para el propietario del receptor, conserva/guarda titular, número y QR, selecciona el teléfono correcto y autoriza la modalidad personal o central/compartida. Para una cuenta central puede autorizarse al administrador o a otro responsable activo, sin asignarle funciones de venta.
3. En la configuración existente de Huata aparece RECEPCIÓN YAPE solo para el usuario/instalación autorizados. Pulsar Activar recepción, leer y aceptar la explicación visible y conceder el acceso en el diálogo de Ajustes de Android. No se concede silenciosamente.
4. La sección muestra receptor, estado, última comunicación y cantidades pendientes/para revisión. No presenta claves, referencias técnicas ni códigos de seguridad. Desactivar recepción conserva la cola y comunica la pausa al backend.
5. Si no hay colector anterior, el alta continúa automáticamente. Después se realiza la verificación financiera física y habilitación habituales del receptor desde Laravel.
6. Si ya existe Bridge, el alta integrada queda bloqueada antes de rotar su credencial. Huata autorizada y con consentimiento puede conservar eventos localmente para validar su listener, pero no transmite por la vinculación nueva hasta la transferencia.
7. El operador comprueba físicamente la captura de Huata, revisa la cola de la aplicación anterior desde su propia interfaz y detiene coordinadamente ese listener. Solo entonces marca en Laravel la confirmación de transferencia y vuelve a autorizar ese teléfono. Se exige que el permiso/listener de Huata ya se hayan comunicado. La siguiente activación reutiliza la identidad compatible del dispositivo y rota su credencial, rechazando los envíos antiguos. No se realiza esta transferencia automáticamente en esta entrega.
8. Si existen varios colectores o uno sin correspondencia única, mantenimiento debe resolver la asociación; no se fabrica otro colector ni se borran los anteriores. La identidad estable del evento y las reglas canónicas siguen protegiendo frente a reenvíos duplicados.

## APK, firma y actualización del Infinix

APK generada:

`/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk`

- Paquete: `com.example.ecolacteoshuata.huata`.
- `versionCode=1`, `versionName=1.0`, min SDK 24 y target SDK 36, conservados.
- Certificado SHA-256: `53bb3b0f9c147ec628c1111ab06621030e999c1b0d3d58f7a06931e6e60004ed`.
- Es el mismo certificado de la APK Huata local anterior, conservada para comparación en `/tmp/huata-antes-yape-integrado.apk`.
- APK SHA-256: `7a54de0317f13bbf7b30434d2fba57045838568992c8842bb0d809ca4cc65298`.

La firma de la instalación real del Infinix todavía no se contrastó. Procedimiento manual, sin desinstalar:

1. Conectar el Infinix, autorizar la depuración USB y comprobar el paquete y versión instalados:

```bash
/home/willian/Android/Sdk/platform-tools/adb devices
/home/willian/Android/Sdk/platform-tools/adb shell dumpsys package com.example.ecolacteoshuata.huata
/home/willian/Android/Sdk/platform-tools/adb shell pm path com.example.ecolacteoshuata.huata
```

2. Copiar únicamente el `base.apk` de la ruta indicada por `pm path` y verificar su certificado con `apksigner verify --print-certs`. Compararlo con el SHA-256 anterior. No extraer datos privados. Si el certificado difiere, no continuar ni desinstalar: hace falta firmar con la clave compatible con esa instalación. Si la versión instalada supera 1, tampoco forzar un downgrade.
3. Cuando paquete, firma y versión sean compatibles, actualizar manteniendo datos:

```bash
/home/willian/Android/Sdk/platform-tools/adb install -r /home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

4. Abrir Huata, comprobar los datos previos, iniciar/actualizar la sesión, seguir la autorización y el consentimiento anteriores. El permiso nuevo aparece como Recepción Yape de Huata. No borrar datos ni desinstalar para corregir un error de firma. `-r` conserva los datos según la [documentación oficial de ADB](https://developer.android.com/tools/adb).

Ninguno de estos comandos sobre el Infinix se ejecutó durante esta entrega. No se instaló automáticamente la APK.

## Play Protect y distribución legítima

Se revisaron la implementación anterior y la [guía oficial de advertencias de Google Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance). La combinación de instalación desde Internet y acceso sensible a notificaciones puede producir bloqueo preventivo. Es una causa compatible con el caso anterior, no un diagnóstico físico confirmado: no se dispone aquí del mensaje exacto ni de una prueba actual del Infinix.

Integrar el listener en Huata no elimina automáticamente esas advertencias ni garantiza aprobación. No se intenta deshabilitar Play Protect, ocultar permisos, falsificar el instalador o eludir controles. Si existe bloqueo, utilizar la revisión/apelación oficial o una distribución legítima; no forzar un bypass.

La preparación para pruebas internas de Google Play debe completar la ficha de privacidad/Data safety de la organización, el aviso destacado/consentimiento ya incorporados, destinatarios y finalidad del tratamiento, retención, contacto y URL pública de privacidad. Los textos de notificaciones pueden contener nombres, importes y datos financieros. La organización debe definir esos datos y su política antes de publicar; no se inventó una URL ni se publicó la APK en Play.

La variante de publicación del adaptador exige HTTPS y validación TLS normal. Esta APK debug permite el servidor de desarrollo configurado por Huata. Para Play se necesita una compilación de publicación, firma controlada y cuenta de desarrollador correspondiente; cambiar a una firma distinta no actualizaría esta instalación debug sin un procedimiento compatible. No se generaron nuevas claves de firma.

Fuentes revisadas: [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [comprobación de acceso del listener](https://developer.android.com/reference/android/app/NotificationManager#isNotificationListenerAccessGranted(android.content.ComponentName)), [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) y [política de datos de Google Play](https://support.google.com/googleplay/android-developer/answer/10144311).

## Validación realizada y límites

- 11 pruebas nuevas Laravel, 69 aserciones: autorización por instalación/sesión, consentimiento, listener, inscripción idempotente sin credenciales móviles, evidencia sin código, revocación, receptor central sin ventas, sesión incompatible, transferencia, recuperación de alta, selección autorizada y exclusión de segundo colector.
- 6 pruebas existentes de `BridgeProvisioningTest.php`, 23 aserciones, aprobadas. Total Laravel focalizado: 17 pruebas, 92 aserciones.
- 23 pruebas Android: 2 de autorización de captura/sesión, 7 de ACK/reintento, 5 de captura/identidad/transporte, 8 de extracción de código y 1 que verifica que sesión/evidencia no siguen redirecciones. Todas aprobadas.
- `assembleDebug`: aprobado. Firma y paquete comparados con la APK local anterior. Servicio y permiso inspeccionados en el manifiesto compilado.
- Sintaxis PHP comprobada en seis archivos y vistas renderizadas mediante Livewire.
- 135 fuentes previas de negocio, Finanzas, filtros y autorización se cotejaron con la instantánea previa y permanecieron sin cambios. Android compila los módulos existentes, aunque no se afirma una prueba manual de todos ellos en el teléfono.
- Respaldo SQLite previo: `storage/app/private/antes-yape-integrado-20261027T214626Z.sqlite`, 0600. La comparación de las 66 tablas previas solo detectó el registro de la migración. La única tabla nueva está vacía. Integridad SQLite y referencias correctas. No se autorizó, inscribió, rotó ni revocó ningún dispositivo operativo durante las pruebas.
- Pendientes físicos: actualización real del Infinix y cotejo de su firma, aparición/concesión del permiso, acceso efectivo al texto que permita su Android/Yape, funcionamiento de Keystore/cola en ese equipo, recepción con pantalla cerrada y restricciones de energía, reconexión y retirada coordinada del Bridge antiguo. Los tests usan HTTP y datos sintéticos aislados; no acreditan dinero real.

Comando de validación Android usado desde `EcolacteosHuata`:

```bash
ANDROID_HOME=/home/willian/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest --tests '*yape.Huata*Test' --offline --no-daemon
```

Desde Laravel, las pruebas usan `DB_CONNECTION=sqlite DB_DATABASE=:memory: DB_URL=''` y únicamente `HuataYapeIntegradoTest.php`/`BridgeProvisioningTest.php`. No se ejecutaron suites generales. D8 emitió advertencias del toolchain Kotlin/R8 existente; la compilación final terminó con BUILD SUCCESSFUL.

## Archivos de esta intervención

Laravel:

- Nuevos: `app/Services/Payments/HuataYape.php`, `app/Http/Controllers/Api/HuataYapeController.php`, `app/Livewire/DispositivosHuataYape.php`, `resources/views/livewire/dispositivos-huata-yape.blade.php`.
- Modificados: `app/Services/Payments/AsignacionesReceptor.php`, `resources/views/pages/vendedor/⚡configuracion-pagos.blade.php`, `routes/api.php`.
- Nuevos: `database/migrations/2026_10_27_220000_add_huata_yape_installations.php`, `tests/Feature/HuataYapeIntegradoTest.php`, este documento.

Kotlin/Android, bajo `EcolacteosHuata/`:

- `shared/src/androidMain/kotlin/com/example/ecolacteoshuata/yape/`: listener, runtime, worker, guardas, modelos, protocolo, serialización, cifrado y outbox reutilizados/adaptados.
- `shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/platform/YapeReception.kt` (nuevo).
- `shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/common/conexion/RecepcionYapeSection.kt` (nuevo) y `ConexionSyncSheet.kt` (sección incorporada).
- `shared/src/androidMain/kotlin/com/example/ecolacteoshuata/di/PlatformModule.android.kt`, `shared/src/iosMain/kotlin/com/example/ecolacteoshuata/di/PlatformModule.ios.kt` (implementación no disponible en iOS, sin nuevos accesos).
- `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/kotlin/com/example/ecolacteoshuata/EcolacteosApplication.kt`.
- `androidApp/src/main/res/xml/huata_backup_rules.xml`, `huata_data_extraction_rules.xml` (excluyen únicamente metadatos nuevos del listener; la cola nueva reside fuera de backup).
- Cinco clases de prueba nuevas en `shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/yape/`.

## Retirada posterior

Para instalaciones nuevas basta Huata y la infraestructura Laravel/Core existente. Después de validar físicamente cada receptor y revisar la cola anterior, pueden retirarse la instalación externa de Bridge de ese teléfono y, en una entrega posterior, sus descargas/enlaces/IPC de compatibilidad. El repositorio, registros, evidencia, auditorías, QR, asociaciones históricas y Payment Core se conservan. Esta entrega no elimina ni desinstala nada anterior.
