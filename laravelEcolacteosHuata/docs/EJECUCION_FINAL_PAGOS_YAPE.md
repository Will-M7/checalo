# Ejecución final — Huata, Payment Core y Yape Bridge

Fecha: 27/10/2026. Este documento sustituye el estado operativo descrito en las entregas anteriores, sin borrar su historia.

## Resultado y límites

Se implementaron P0–P3: despliegue del Core comercial corregido; captura del paquete Yape real y revisión segura sin código; modalidades central/personal/compartida; inscripción revocable y comprobación de sesión Huata–Bridge; pedidos guardados con pago posterior y receptor congelado. Se generaron ambas APK. **La validación física en el Infinix sigue pendiente: no se instalaron APK, no se enviaron fondos y no se verificó ni habilitó el receptor real.**

La autorización humana recibida permitió reiniciar únicamente Core, rotar coordinadamente la credencial de aplicación Laravel–Core y conceder su alcance administrativo, implementar el registro y las autorizaciones P2, y modificar/compilar Bridge. No se reiniciaron Laravel ni Scheduler. No se hicieron commits, pushes, cambios generales de roles ni limpieza de datos.

## P0: causa real y despliegue

El paquete instalado observado por ADB fue `com.bcp.innovacxion.yapeapp`. La integración filtraba `com.bcp.innovacion.yapeapp`, diferente. Además, el filtro de texto exigía una frase concreta de ingreso. Ambas condiciones podían descartar la notificación antes de persistirla.

Ahora el listener acepta notificaciones no vacías de ese paquete exacto, excluye resúmenes de grupo y conserva título, texto, texto expandido, líneas y metadatos de texto pertinentes. La ausencia de código no impide capturar, cifrar y encolar el evento. No se amplió la captura a aplicaciones ajenas.

El caso observado «Confirmación de Pago», S/ 1.00 y sin código está cubierto por pruebas con datos ficticios. Core conserva formatos insuficientes como `REVIEW_FORMAT_UNRECOGNIZED`; un ingreso reconocido sin código queda en `REVIEW_CODE_UNAVAILABLE`. Ninguno se acredita únicamente por importe y hora. El ACK incluye `reviewRequired`, también al reenviar un duplicado ya guardado en revisión. Bridge marca `REMOTE_REVIEW` y conserva la evidencia cifrada, en lugar de borrarla como un envío conciliado.

Los 3393 registros anteriores eran `LEGACY_REVIEW`; el diagnóstico agregado incluía notificaciones de múltiples aplicaciones, entre ellas 18 del paquete Yape real. No se borraron, atribuyeron, reprodujeron ni convirtieron en cobros. No se recuperó retrospectivamente el evento de las 07:51 ni se confirmó el pago real de S/ 1.00.

### Estado operativo comprobado al cierre

| Componente | Estado |
|---|---|
| Laravel existente | PID 106909, escucha `0.0.0.0:8000`, sin reinicio |
| Core final | PID 134239, escucha solamente `127.0.0.1:8087`, modo `commercial`, health OK |
| Receptor `receptor-001` | `verified=false`, `enabled=false`, versión 0 |
| Comunicación del receptor | En la última consulta `online=false`; había tenido heartbeat durante la intervención. No se interpreta como verificación bancaria |
| Dispositivo `bridge-001` | Todavía `legacy`, sin inscripción administrada nueva |
| Dispositivos nuevos inscritos en Laravel | 0; inscripción queda como acción explícita del operador |
| ADB al cierre | Ningún dispositivo conectado; no se pudo comprobar la firma actualmente instalada |

Se comprobó la autorización del endpoint administrativo. La credencial anterior de aplicación devuelve 401; la nueva permite las operaciones autorizadas. Una solicitud sin confirmación física devuelve `PHYSICAL_CONFIRMATION_REQUIRED` sin cambiar el receptor.

Core ejecuta la distribución separada:

`/home/willian/Desarrollo/laboratorio/qr/build/releases/huata-final-20261027/qr-0.1.0-SNAPSHOT`

El proceso final quedó funcionando. Comando empleado, documentado para una futura operación manual después de detener coordinadamente el proceso que ocupa 8087:

```bash
cd /home/willian/Desarrollo/laboratorio/qr
PAYMENT_MODE=commercial PAYMENT_HOST=127.0.0.1 PAYMENT_PORT=8087 \
PAYMENT_CONFIG_FILE=/home/willian/.config/payment-core/commercial.json \
PAYMENT_DB_PATH=/home/willian/.config/payment-core/commercial.sqlite \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
build/releases/huata-final-20261027/qr-0.1.0-SNAPSHOT/bin/qr server
```

No ejecutar una segunda instancia simultánea. No se utilizó systemd, nohup ni tmux para este despliegue. Se conservaron las distribuciones anteriores.

### Respaldos y credenciales

Respaldos consistentes mediante SQLite backup, con comprobación de integridad, directorios privados y archivos 0600:

- `/home/willian/.config/payment-core/backups/huata-final-20261027T134205Z`: Core, Laravel y configuración previa.
- `/home/willian/.config/payment-core/backups/huata-final-schema-20261027T143320Z`: respaldo adicional inmediatamente anterior a las migraciones finales, configuración ya rotada, clave de aplicación y conteos.

No restaurar una base antigua sobre actividad financiera posterior sin un procedimiento de recuperación. Los respaldos contienen secretos y no deben publicarse.

La nueva credencial de aplicación se guarda en `/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/storage/app/private/payment-core.key`, permisos 0600, ignorada por Git. `PaymentCredentials` permite que Laravel la lea sin reiniciar PHP. Se actualizó el mismo cliente comercial, preservando su identidad y concediendo `receiverAdministration` con autorización expresa. No se muestran claves en este informe.

**La credencial antigua del dispositivo aún no se rotó:** la inscripción manual posterior rota la de `bridge-001` sin duplicar el colector. La habilitación financiera exige ya una inscripción administrada activa, por lo que la clave legacy no permite completar el alta operativa. `matchSecret` permanece intacto; su rotación requiere soporte HMAC versionado y continúa pendiente.

## P1: cuentas y atribución

`AsignacionesReceptor` es la autoridad Laravel para resolver el receptor del vendedor. Permite cuenta central, cuenta personal autorizada u otro receptor compartido verificado. Central y compartida requieren autorización expresa del responsable; el alta personal nueva requiere permiso administrativo y declaración de titularidad en la tarjeta existente. Se conserva compatibilidad de los receptores propios anteriores.

No existe obligación de un vendedor por dispositivo: varios vendedores pueden resolver al mismo receptor y utilizar su Bridge autorizado. La venta, el pedido y la intención mantienen el vendedor que originó la operación; no se transfieren al propietario de la cuenta central. Los snapshots guardan identidad/QR del receptor y vendedor original. Cambiar la política no reescribe pedidos/intenciones previos.

No se aplicaron nuevas asignaciones a la base operativa. El administrador debe elegirlas. La coincidencia entre usuario Huata y Bridge no demuestra propiedad de una cuenta Yape.

## P2: inscripción e identidad

Se eligió **una APK común firmada con inscripción individual**, no una compilación por vendedor ni una compilación desde PHP. Desde Configuración de pagos se descarga la APK y se prepara un enlace `huatabridge://enroll` de 10 minutos. La URL autorizada viaja en el enlace, sin exigir escribir IP, puerto, receptor o claves.

El ticket se guarda como hash. La primera instalación lo reclama con un nonce persistente; los reintentos de la misma reclamación son idempotentes y otra instalación es rechazada. El modo, propietario y receptor los decide el servidor. Se reutiliza el colector legacy único del receptor, o el dispositivo ya inscrito, para evitar duplicaciones. Si hay varios colectores antiguos sin correspondencia inequívoca, se bloquea la elección automática.

Bridge conserva la clave de transporte cifrada con Android Keystore. No se cambia el alias de cifrado de la cola ni se borra la base local. La revocación es persistente y exige nueva inscripción autorizada. Los receptores registrados por este mecanismo nacen sin verificación y deshabilitados.

Para modo personal, Huata expone un servicio Binder con permiso de nivel `signature`; se comprueban firma, UID y paquete llamante. Bridge verifica asimismo la firma de Huata. No hay lectura de almacenamiento privado de otra aplicación. Huata usa su sesión local y su autenticación móvil actual para pedir a Laravel una prueba efímera vinculada al dispositivo, nonce y evento. No entrega su token de usuario a Bridge.

Laravel exige usuario activo, propietario correcto, autorización personal y acceso vigente a Configuración de pagos. Cada prueba dura 30 segundos y se consume una vez. Tras validarla emite hacia Core una concesión por evento, también temporal y de un solo uso. Core exige esa concesión para dispositivos personales, incluso si alguien intenta acceder directamente con la clave del dispositivo. Heartbeat o una cadena de nombre no sustituyen esta comprobación.

Un cambio de sesión dispara revalidación mediante comunicación protegida. Sin prueba compatible se suspende el dispositivo; Laravel invalida pruebas pendientes y Core invalida concesiones y aparta evidencia no vinculada. No se revierte un pago canónico anterior ni se borra la cola. La sesión correcta puede recuperar una suspensión; un dispositivo revocado no se recupera de ese modo.

Central/compartido no exige que cada vendedor sea el usuario del teléfono colector. El modo directo, para un teléfono sin Huata, solo puede autorizarlo administración crítica con consentimiento del responsable. No se permite escogerlo desde la APK para evadir la validación personal.

Sin red se conserva la cola. No se promete revocación instantánea en un teléfono desconectado; la admisión de evidencia y conciliación se decide en el servidor, con el instante original del evento.

### Endpoints

| Destino | Método y ruta | Protección |
|---|---|---|
| Laravel | GET `/pagos/bridge.apk` | Sesión de personal activa y acceso a Configuración de pagos |
| Laravel | POST `/api/bridge/enroll` | Ticket temporal, reclamación individual, rate limit, respuesta no-store |
| Laravel | POST `/api/mobile/bridge/assertion` | Autenticación móvil existente, propietario y permisos vigentes, rate limit |
| Laravel | POST `/api/bridge/heartbeat` | Credencial de dispositivo; prueba personal cuando corresponde |
| Laravel | POST `/api/bridge/notifications` | Igual, receptor vinculado y concesión por evento |
| Core privado | GET/POST `/api/admin/devices` | Credencial de aplicación y alcance administrativo |
| Core privado | POST `/api/admin/devices/{id}/state` | Revocación/suspensión persistente y actor auditado |
| Core privado | POST `/api/admin/devices/{id}/grant` | Concesión temporal para propietario/evento personal |
| Core privado | POST `/api/admin/receivers` | Registro técnico; no verifica ni habilita |
| Core privado | POST `/api/admin/receivers/{id}/state` | Verificación física, versiones y controles existentes |

## P3: pedidos web y pago posterior

El pedido se persiste antes de crear la intención y mostrar un QR operativo. Si el receptor registrado o el motor no están habilitados, se conserva el pedido sin una intención falsa y se informa el bloqueo. No se presenta un QR como cobro disponible mientras falte habilitación.

El comprador puede conservarlo en Mi cuenta y volver después. No reserva ni consume stock por ese guardado. Se reutiliza el acceso existente de Mi cuenta, sin añadir otra pantalla ni rediseñar checkout. El transporte admite además `yape_despues`; no se añadió otro selector visual de métodos de pago.

Al volver a un pedido cuyo intento expiró canónicamente, se puede crear una intención nueva con identificador nuevo y con el receptor/snapshot original, manteniendo el intento expirado como historia. Un pedido cancelado o pagado no se renueva. Un índice garantiza un solo intento activo/pagado por pedido y el estado de un intento viejo no pisa el de uno nuevo. La confirmación continúa dependiendo exclusivamente del PAID canónico, evidencia única y reglas existentes de stock/saldo/idempotencia.

**TTL conservado: 300 segundos, cinco minutos.** No se amplió silenciosamente a los diez minutos planteados. El pago posterior obtiene una ventana nueva solamente tras expirar la anterior, sin reutilizar códigos ni alterar timestamps históricos. La inscripción de dispositivos de diez minutos es una ventana distinta.

## Persistencia y migraciones

Las tres migraciones incrementales siguientes se aplicaron a la SQLite operativa, después del respaldo. No queda pendiente ejecutarlas en esta instalación:

1. `2026_10_27_150000_add_payment_receiver_assignments.php`: `payment_receiver_policy`, `payment_seller_assignments`, `payment_authorization_events`.
2. `2026_10_27_160000_add_bridge_enrollment.php`: `payment_bridge_devices`, `payment_bridge_tickets`, `payment_bridge_assertions`.
3. `2026_10_27_170000_allow_expired_payment_attempt_history.php`: reemplaza exclusivamente el índice único de pedido en `intentos_pago` por el índice parcial `intentos_pago_order_active_unique`, para conservar intentos expirados sin permitir varios activos.

La tercera admite SQLite/PostgreSQL; rechaza otros motores antes de cambiar el esquema. Una futura instalación MySQL necesita una adaptación expresamente probada. Los rollback se niegan a borrar historia cuando ya existe información; la recuperación entonces requiere migración correctiva, no eliminación forzada.

Core inicializa su registro persistente mediante `CommercialRegistry`: `canonical_receiver_catalog`, `canonical_device_authorizations`, `canonical_device_grants`, `canonical_registry_audit`. Conserva el estado físico y las tablas financieras anteriores. La cola Bridge mantiene versión de base 3 y los registros existentes; `REMOTE_REVIEW` es otro valor del estado textual. Huata agrega una consulta SQLDelight, sin migrar ni reinicializar la SQLite del teléfono.

Conteos operativos conservados: usuarios 13, ventas 2, pedidos web 12, intentos de pago 0, movimientos de saldo 0; Core: intenciones 0 y evidencia canónica 0. Los dos receptores Laravel anteriores permanecen. No se insertaron usuarios ni transacciones comerciales de demostración.

## Archivos intervenidos

Las rutas de esta lista son relativas a cada raíz explícita. El repositorio ya contenía cambios locales extensos: no se atribuye a esta entrega todo el diff contra Git. Se compararon las fuentes disponibles con la copia anterior a la intervención.

**Laravel — `/home/willian/Desarrollo/Huata/laravelEcolacteosHuata`:**

- Nuevos: `app/Services/Payments/PaymentCredentials.php`, `AsignacionesReceptor.php`, `BridgeProvisioning.php`; `app/Http/Controllers/Api/BridgeEnrollmentController.php`.
- Servicios existentes: `app/Services/Payments/HttpPaymentCore.php`, `PaymentCore.php`, `BridgeIngress.php`, `ReceptoresService.php`, `CobrosService.php`; modelo `app/Models/ReceptorPago.php`.
- Controladores: `app/Http/Controllers/ClientesWeb/CuentaController.php`, `app/Http/Controllers/PedidoWebController.php`.
- Integración: `config/payments.php`, `routes/api.php`, `routes/vendedor.php`, las tres migraciones arriba enumeradas.
- Vistas: `resources/views/pages/vendedor/⚡configuracion-pagos.blade.php` — controles imprescindibles dentro de tarjetas actuales; `resources/views/clientes-web/pago.blade.php` — textos de pago posterior/revisión.
- Pruebas y herramientas: `tests/Feature/PaymentCredentialsTest.php`, `AsignacionesPagoTest.php`, `BridgeProvisioningTest.php`, `PagosCanonicosTest.php`, `ReceptoresCoreIntegrationTest.php`, `YapeBridgeIngressTest.php`, `YapeBridgeIntegrationTest.php`, fixture de `tests/Support/ReceiverCoreFixture.php`; `tools/test_receiver_provisioning.py`, `tools/test_bridge_wifi.py` y fixtures del circuito canónico para el paquete real.
- Documentación: este informe y avisos de actualización en los dos informes previos de pagos.

**Core — `/home/willian/Desarrollo/laboratorio/qr`:**

- Nuevo `src/main/kotlin/pe/willian/qrlab/server/CommercialRegistry.kt`.
- `CanonicalPayments.kt`, `CommercialConfig.kt`, `CommercialRoutes.kt`, `ReceiverAdministration.kt` en el mismo directorio.
- `src/test/kotlin/pe/willian/qrlab/server/CommercialRegistryTest.kt`, `CanonicalPaymentsTest.kt` y fixtures pertinentes de integración. Se reutilizó la herramienta de aprovisionamiento administrativo existente.

**Bridge — `/home/willian/Desarrollo/laboratorio/yape-bridge`:**

- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`.
- Bajo `app/src/main/java/pe/willian/yapebridge/`: `MainActivity.kt` (recepción del enlace fuera de los Composables); nuevos `bridge/BridgeEnrollment.kt`, `bridge/HuataIdentity.kt`; `bridge/BackendBridge.kt`, `BridgeHttpTransport.kt`, `CanonicalProtocol.kt`, `DeviceCredentials.kt`; `listener/LabNotificationListener.kt`; `store/BridgeOutboxEvent.kt`, `OutboxDatabase.kt`.
- Pruebas de protocolo/notificaciones en `app/src/test/java/pe/willian/yapebridge/`.

**Huata Kotlin — `/home/willian/Desarrollo/Huata/EcolacteosHuata`:**

- `androidApp/src/main/AndroidManifest.xml`; nuevo `androidApp/src/main/kotlin/com/example/ecolacteoshuata/BridgeIdentityService.kt`.
- Nuevo `shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/BridgeSessionAssertion.kt`; `data/remote/ApiConfig.kt` y `di/AppModules.kt` bajo la misma raíz de paquete.
- `shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/AutorizacionLocal.sq`: consulta de sesión actual.

### Conservación visual

Comparación contra `/tmp/huata-final-before`: `welcome.blade.php` y `clientes-web/cuenta.blade.php` idénticos byte a byte; clases del formulario de pago idénticas; todos los Composables de `MainActivity` de Bridge idénticos; `MainActivity` de Huata idéntico. No se modificaron estilos globales, colores, fondos, menús ni pantallas comerciales. Esta comprobación de fuente no sustituye la prueba física de Android pendiente.

## Pruebas y compilaciones

Todas las pruebas automatizadas financieras utilizaron bases aisladas y credenciales ficticias. La integración arrancó únicamente un Core temporal en puerto aleatorio distinto de 8000/8087 y lo detuvo al terminar; no sustituyó el proceso comercial.

| Verificación | Resultado |
|---|---|
| Core `./gradlew --offline test distTar` | 274 pruebas, cero fallos; distribución final compilada |
| Laravel suite completa final | 510 casos: 504 aprobados, 2099 aserciones, 6 opt-in omitidos en la ejecución normal y aprobados por separado contra Core |
| Laravel contra Core real aislado | 6 aprobadas, 80 aserciones |
| Última barrera Laravel: inscripción/ingreso | 31 aprobadas, 109 aserciones, incluye rechazo de clave anterior en receptor administrado |
| Bridge debug/release unit tests + assembleDebug | 50 aprobadas por variante, cero fallos; APK generada |
| Huata `:shared:testAndroidHostTest :androidApp:assembleDebug` | 58 pruebas host aprobadas; APK generada |
| Herramienta administrativa anterior | 4 pruebas aisladas aprobadas |
| `git diff --check` de Huata | Sin errores de espacios |

Cobertura focalizada: captura sin código/formato desconocido, ACK de revisión y duplicados, sesión ajena/prueba repetida, recuperación de suspensión, revocación, expiración/reclamación de tickets, claves antiguas, concesiones Core por evento, cuentas compartidas y atribución del vendedor, snapshots, pedido guardado sin falsa intención, renovación tras expiración, aislamiento del comprador, evidencia única e idempotencia. La ejecución en teléfono, permisos del sistema y conservación física de su cola tras actualizar siguen siendo pruebas manuales pendientes.

Logs de esta ejecución: `/tmp/huata-final-core-all.log`, `/tmp/huata-final-laravel-all.log`, `/tmp/huata-final-integration.log`, `/tmp/huata-final-last-gate.log`, `/tmp/huata-final-bridge-p2.log`, `/tmp/huata-final-huata-tests.log`. Son registros temporales locales, no deben confundirse con auditoría financiera persistente.

## Artefactos y firma

| APK | Ruta absoluta | SHA-256 |
|---|---|---|
| Huata debug | `/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk` | `49ef2d6f6eeeabc990d3346cc6c78e68b643259d3092d9993d32e1e0004737fc` |
| Bridge debug 0.2.0-payments | `/home/willian/Desarrollo/laboratorio/yape-bridge/app/build/outputs/apk/debug/app-debug.apk` | `b3e55fee2ac38c8e805d973ea162a46a33132faf4dea0616df41eeaa9b2b663d` |

Bridge publicado para descarga autenticada en `/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/storage/app/private/bridge/yape-bridge.apk`, permisos 0600, mismo hash que la APK generada. No hay compilación web privilegiada.

Los artefactos generados comparten certificado Android Debug, SHA-256 `53bb3b0f9c147ec628c1111ab06621030e999c1b0d3d58f7a06931e6e60004ed`. Paquetes: Huata `com.example.ecolacteoshuata.huata`, Bridge debug `pe.willian.yapebridge.debug`. **Son builds de desarrollo LAN; no son un lanzamiento firmado para producción.** La firma instalada debe ser compatible para actualizar conservando datos y para el IPC. No se generaron ni publicaron claves de firma nuevas.

## Procedimiento de prueba física

1. Actualizar manualmente ambas APK en el mismo Infinix, conservando instalación y datos. Comprobar compatibilidad de firma antes de actualizar; no desinstalar ni borrar almacenamiento para resolver un conflicto. No se exige USB como transporte de pagos.
2. Conectar teléfono y laptop a la red LAN habitual. Mantener Core privado en loopback; Bridge comunica con Laravel en 8000. Huata conserva su punto de conexión existente. Para producción usar HTTPS y firma de distribución controlada.
3. Iniciar sesión en Huata con el propietario autorizado del receptor personal. El receptor Laravel 1 pertenece al usuario ID 11, `pepe`; el otro receptor anterior pertenece al ID 12. No se cambiaron usuarios ni contraseñas.
4. En Configuración de pagos, administración prepara el enlace para el receptor 1. Se reutilizará `bridge-001`, rotando su credencial de transporte. Abrir el enlace de inscripción de diez minutos en el teléfono. No copiar claves ni receiver IDs manualmente. Para cuenta compartida/directa usar únicamente el modo expresamente autorizado por el administrador y responsable.
5. Conceder manualmente el acceso Android a notificaciones y comprobar listener/heartbeat con la credencial nueva. La sesión Huata personal correcta debe permitir transmisión. Cerrar sesión o cambiar a otro usuario debe suspender; confirmar que la cola permanece. Volver al propietario debe recuperar la suspensión, no una revocación.
6. **Antes de pagar**, el responsable y administrador verifican físicamente cuenta/titular/número/QR. Registrar esa confirmación con el flujo existente; después habilitar con Bridge administrado activo y heartbeat reciente. No basta la inscripción o la coincidencia de sesión. Si cualquier control falla, no presentar el cobro como operativo.
7. Elegir en configuración las asignaciones central/personal/compartida necesarias. No se aplicaron automáticamente a vendedores. Crear un pedido comprador y comprobar vendedor original y QR asignado; guardarlo y reabrirlo desde Mi cuenta. Comprobar que no se descontó stock al guardar.
8. Con intención nueva vigente y receptor habilitado, el usuario puede efectuar voluntariamente una prueba física pequeña. Observar la captura, persistencia, envío y estado canónico. Si no hay código o el formato es insuficiente, el resultado correcto es revisión, no PAID automático. No fabricar un código ni reutilizar el evento antiguo.
9. Probar pérdida/restauración de WiFi, timestamps originales y cola conservada. Un evento recibido tarde debe respetar la expiración canónica. No borrar ni reprocesar masivamente los 3393 registros heredados.

## Pendientes reales y compatibilidad

- Actualización manual de APK, prueba física del IPC, permisos Android, inscripción y verificación/habilitación del receptor. Hasta completarlo no se declara funcionando el cobro real.
- Rotación de clave del dispositivo al inscribir; la clave legacy aún existe en la configuración anterior y no debe compartirse. Rotación de `matchSecret` pendiente de compatibilidad HMAC versionada.
- Los eventos sin código/formato suficiente están conservados en revisión. No se implementó una nueva pantalla de aprobación bancaria ni se relajó conciliación para acreditarlos.
- Un receptor ya registrado no cambia silenciosamente nombre/número/QR en Core: una discrepancia devuelve `REGISTERED_IDENTITY_MISMATCH` y requiere procedimiento controlado de nueva comprobación. No se sustituyó la cuenta financiera actual.
- Varios colectores legacy ambiguos requieren selección coordinada; no se inventa una asociación.
- Producción requiere HTTPS y certificado de distribución común confiable para ambas aplicaciones; no se ha desplegado producción ni publicado Laravel a Internet.
- No se garantizan permisos revocados instantáneamente sin red. El servidor sigue siendo barrera obligatoria de conciliación.
- No se amplió TTL a diez minutos ni se cambió una base móvil. No se instalaron aplicaciones ni se borraron pendientes.

La implementación técnica está compilada, probada y el Core actualizado está desplegado. El circuito financiero físico permanece deliberadamente bloqueado hasta completar las comprobaciones anteriores.
