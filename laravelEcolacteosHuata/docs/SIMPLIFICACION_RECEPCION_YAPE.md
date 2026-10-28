# Recepción Yape: administración central y consentimiento Android

Entrega local: 27 de octubre de 2026 (America/Lima).

## Resultado implementado

Una sola APK Huata conserva el listener integrado. Administración crea o reutiliza una cuenta receptora, selecciona cualquier usuario activo y registra una solicitud persistente. Al iniciar sesión, volver a primer plano o completar la sincronización, Android consulta las solicitudes de esa sesión. El diálogo presenta titular, número, QR y el aviso de acceso a notificaciones, con ACEPTAR/RECHAZAR. No admite cierre por fuera ni por Atrás mientras el servidor esté disponible; un fallo de comunicación termina la operación y permite continuar con la aplicación.

Aceptar vincula la instalación aleatoria autenticada a la solicitud y al receptor. Luego abre Ajustes oficiales si falta el acceso. Al regresar o conectarse el listener, la vinculación técnica continúa automáticamente mediante HuataYape y BridgeProvisioning. La decisión no concede roles comerciales, Finanzas ni administración, no prueba titularidad bancaria y no confirma dinero. El backend comprueba solicitud, responsable, instalación e identidad en cada transmisión.

La tarjeta **Recepción Yape** utiliza ModuloItem/ModulosGrid, las tarjetas centrales existentes y el acento del tema. Está disponible para el usuario autorizado en sus paneles, también en el panel genérico sin funciones nativas. Abre un monitor con autorización, cuenta, permiso, listener, comunicación, último evento, pendientes, enviados, revisión y reintentos. Los textos e importes originales se muestran cuando siguen conservados cifrados; un evento enviado no se presenta como pago confirmado. Los textos enviados que la cola ya purgó no se reconstruyen ni se inventan.

Se reutilizan HuataYapeListener, HuataYapeReception, HuataYapeWorker, AES-GCM/Keystore, WorkManager, outbox, protocolo y ACK canónicos, BridgeIngress y BridgeProvisioning. Se conserva el trayecto Android → Laravel → Payment Core y la conciliación existente. Mientras el sistema mantiene conectado el listener, Huata consulta autorización y comunica su estado cada 60 segundos; WorkManager conserva sus reintentos y rescate periódico de 15 minutos. No se agregaron servicios externos, Firebase, claves en la APK ni otra aplicación.

## Administración y operación

Pantalla: **Administración → Receptores Yape**, ruta `/{current_team}/admin/receptores-yape`, permiso dinámico `admin.receptores-yape`. Se añadió únicamente ese permiso al rol Administrador existente. Puede delegarse mediante el gestor de roles; administrar receptores no exige el antiguo permiso comercial de Configuración de pagos.

1. Crear receptor: titular, número Yape, QR y responsable activo de cualquier rol. El buscador encuentra nombre, apellidos o usuario. Guardar registra la solicitud pendiente; no habilita cobros.
2. En Android, el responsable inicia su sesión con la conexión habitual de Huata. Lee el diálogo, comprueba los datos y acepta o rechaza. Al aceptar, concede acceso a notificaciones en Ajustes del sistema. No introduce IP, puerto, token, código de inscripción ni identificadores técnicos.
3. Consultar la tarjeta Recepción Yape. El permiso y el listener se reflejan sin cerrar sesión. Si hubo un timeout después de guardar la decisión, una nueva consulta recupera la decisión persistida; no crea otra aceptación ni otro dispositivo.
4. En la misma pantalla Web, comprobar físicamente cuenta, titular, número y QR. Marcar la confirmación de ese receptor y **Verificar cuenta y QR**. Cuando el teléfono esté vinculado y comunicado, usar **Habilitar cobros**. Core debe confirmar la verificación y la disponibilidad. También se puede definir allí la cuenta central.
5. Seleccionar vendedor → receptor y guardar. Varios vendedores pueden usar el mismo. Se permite preparar una asignación pendiente, pero el QR comercial sigue bloqueado hasta disponer de todas las autorizaciones. Ser responsable de recepción no asigna implícitamente esa cuenta a sus propias ventas. En cuentas nuevas, el contacto comercial continúa viniendo del usuario; el número Yape no se convierte automáticamente en WhatsApp del vendedor.
6. Editar/cambiar responsable conserva la custodia financiera original y las evidencias. Una nueva solicitud bloquea cobros y revoca la autorización integrada anterior. Cambiar identidad invalida además la verificación financiera. El rechazo, la revocación y los cambios de identidad se muestran en el listado y se auditan.

El listado muestra titular, número, QR, responsable, decisión, instalación, permiso, listener, fechas de conexión/transmisión, habilitación comercial y vendedores asociados. Consultar estado obtiene el diagnóstico existente de Core. Se conservan las modalidades históricas y sus relaciones; las nuevas asignaciones explícitas señalan un receptor concreto.

## Transferencia y revocación

Si hay un colector anterior, el alta nueva se detiene antes de sustituirlo. En la misma pantalla, desplegar **Transferencia de Bridge anterior** después de aceptar en Huata. Comprobar físicamente el nuevo listener, revisar la cola anterior y detener coordinadamente el listener anterior; luego marcar la confirmación del receptor y autorizar la transferencia. La siguiente consulta de Huata reutiliza el dispositivo compatible y el procedimiento de inscripción existente. La sustitución de la credencial forma parte de esa operación administrativa; no se rotó ninguna credencial operativa en esta entrega.

No se autorizan dos instalaciones Huata para el mismo receptor. Si existen varios colectores sin correspondencia única, el servidor mantiene el bloqueo y requiere resolver esa asociación. No borra evidencias, registros ni colas. Desinstalar la aplicación anterior no elimina su historial del backend.

Revocar bloquea primero Laravel y la habilitación local. Después comunica la retirada a Core mediante los servicios existentes. Si Core no responde, el bloqueo local permanece y la acción se puede repetir. Las solicitudes y las identidades de dispositivos anteriores se conservan, y la sustitución de una asociación queda auditada.

## Interfaz del vendedor

Se retiró Configuración de pagos de su navegación. La ruta histórica remite a la administración; el componente antiguo ya no expone edición, selección, inscripción o descargas. Las selecciones comerciales por el propio vendedor se rechazan en servidor. La sección de activación duplicada dejó de aparecer en la hoja de conexión Android.

Se retiraron los botones de pausa Web y Android. Los endpoints antiguos de pausa también rechazan cambios del vendedor. Administración conserva su control desde Usuarios. Una pausa histórica se migra conservadoramente a recepción desautorizada, con auditoría y sin borrar la bandera anterior. Una autorización administrativa posterior resuelve la pausa de forma explícita. Se conserva el reparto por pedido, las capacidades comerciales y los pedidos/intenciones históricos.

## Migración aplicada y conservación

Migración incremental aplicada localmente: `database/migrations/2026_10_27_230000_add_yape_reception_requests.php`.

- Añade `yape_reception_requests`, con snapshot de identidad, responsable, autor, decisión y asociación de instalación; conserva todas las decisiones.
- Añade `notification_permission` a las instalaciones para distinguir acceso de Android y conexión del listener.
- Retira únicamente la unicidad de `receptores_pago.user_id`: la responsabilidad técnica no se usa como prueba de titularidad ni limita una persona a un registro financiero. La selección comercial nueva es explícita.
- Añade el permiso administrativo y adapta pausas históricas con criterio conservador. El rollback no elimina solicitudes ni historial.

Respaldo privado SQLite, permisos 0600:
`/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/storage/app/private/antes-solicitudes-yape-20261028T000957Z.sqlite`.

Se compararon las columnas previas de las 67 tablas con el respaldo. Solo cambiaron `migrations`, `authorization_roles`, `authorization_state` y `authorization_audit` por el permiso nuevo y su registro. Usuarios, pedidos, intenciones, saldos, movimientos, receptores, instalaciones y credenciales previas conservaron sus valores. La columna nueva de permiso quedó en su valor conservador inicial. `integrity_check=ok`; `foreign_key_check` sin errores. La tabla operativa de solicitudes quedó vacía: no se crearon receptores de prueba ni movimientos financieros operativos.

Para otra instalación Laravel, desplegar el código y ejecutar exclusivamente la migración incremental pendiente con el mecanismo habitual. No usar migrate:fresh sobre la base operativa.

## Validación

Todas las pruebas financieras usan SQLite aislado y HTTP simulado o un Core temporal en un puerto libre, nunca la base operativa.

- Laravel: **86 pruebas** aprobadas (511 aserciones en la ejecución conjunta). La comprobación posterior de la ruta Web completa también pasó: 17 casos de solicitudes, 135 aserciones. Incluyen 17 casos nuevos de solicitudes, Proveedor, rol personalizado, rechazo, consentimiento, instalación/sesión, gestor administrativo sin permisos comerciales, asignación explícita, revocación, transferencia y separación entre responsabilidad técnica y ventas. También verifican los contratos existentes de receptores, inscripción, disponibilidad, distribución y preservación de intención/receptor histórico.
- Core comercial temporal: **7 pruebas, 98 aserciones**, aprobadas. El caso nuevo recorre solicitud → aceptación → permiso/listener reportado → inscripción automática → verificación/habilitación → aviso sin código en revisión → DUPLICATE → revocación. Los otros casos comprueban conciliación canónica y ausencia de acreditación duplicada.
- Android: **34 pruebas, cero fallos**: captura, consentimiento/autorización, protocolo, ACK/reintentos, extracción, transporte, dos pruebas nuevas del QR (sesión al servidor configurado y rechazo de destinos/redirecciones externos), y autorización multiplataforma.
- Compilación Android: BUILD SUCCESSFUL. Assets Web: npm run build aprobado. Sintaxis PHP correcta en 20 archivos; git diff --check correcto para los archivos de esta intervención. Las vistas administrativas se renderizaron en las pruebas Livewire.

Comandos desde Laravel:

```bash
DB_CONNECTION=sqlite DB_DATABASE=:memory: DB_URL='' php vendor/bin/pest tests/Feature/SolicitudesRecepcionYapeTest.php tests/Feature/HuataYapeIntegradoTest.php tests/Feature/BridgeProvisioningTest.php tests/Feature/ReceptoresAdministracionTest.php tests/Feature/DisponibilidadFinanzasAndroidTest.php tests/Feature/AsignacionesPagoTest.php tests/Feature/DistribucionesRolesTest.php --compact
python3 tools/test_receiver_provisioning.py
npm run build
```

Desde EcolacteosHuata:

```bash
ANDROID_HOME=/home/willian/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest --tests '*yape.Huata*Test' --tests '*AutorizacionMultiplataformaTest*' --offline --no-daemon
```

## APK e instalación manual

APK debug firmada:
`/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

- Paquete único: `com.example.ecolacteoshuata.huata`.
- minSdk 24, targetSdk 36, versionCode 1 y versionName 1.0, conservados.
- Firma v2 verificada. Certificado SHA-256: `53bb3b0f9c147ec628c1111ab06621030e999c1b0d3d58f7a06931e6e60004ed`, el mismo certificado debug local anterior.
- SHA-256 APK: `082f820c155fe90c2fc1a47917edbc7a1634b9dc37a088c900efbde9bd468d58`.

Instalar manualmente como actualización, conservando datos, únicamente si el paquete, la firma y la versión instalada son compatibles. No desinstalar ni borrar datos ante un conflicto de firma, ni forzar un downgrade. Con un teléfono autorizado conectado, el comando manual es:

```bash
/home/willian/Android/Sdk/platform-tools/adb install -r /home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

También se puede copiar y abrir la APK en el teléfono usando el procedimiento de instalación permitido por Android. No se ejecutó ningún comando de instalación ni se consultó un equipo específico. La firma de una instalación física debe cotejarse antes de actualizarla. Esta es una APK debug para pruebas; la publicación comercial sigue requiriendo la firma y distribución correspondientes.

Prueba manual: actualizar sin borrar datos, iniciar sesión como el responsable seleccionado, comprobar aparición del diálogo/QR, aceptar y conceder el permiso, volver a Huata y revisar la tarjeta. Probar rechazo con otra solicitud, pérdida/recuperación de red, sesión diferente, pantalla cerrada y revocación administrativa. Verificar visualmente los paneles habituales y realizar los pagos reales únicamente por el usuario después de la verificación y habilitación administrativas.

## Límites comprobados

Android exige consentimiento en Ajustes; no existe concesión silenciosa. El sistema, Doze, una detención forzada o restricciones de energía pueden suspender listener y trabajo. Los heartbeats de 60 segundos dependen de que Android mantenga activo el proceso; no se garantiza disponibilidad continua. Core exige comunicación reciente y bloquea el QR si falta. Yape/Android pueden limitar el contenido legible. No se validó físicamente el permiso, la captura ni un pago real en un teléfono durante esta entrega.

El contrato Core existente registra una identidad bancaria y solo verifica coincidencias exactas. **No ofrece edición de una identidad ya registrada**. Si se cambia titular, número o QR y Core conserva otros datos, Laravel invalida la aprobación y mantiene el bloqueo hasta reconciliar esa identidad por el procedimiento autorizado del Core. No se añadió una mutación insegura del catálogo ni se reinterpretaron evidencias antiguas. Una identidad nueva y compatible recorre el alta automática, comprobada con Core temporal.

No se modificaron Payment Core ni el Bridge antiguo. No hubo instalación automática, reinicio de servicios operativos, commit, push, cambios de claves operativas ni eliminación de registros históricos. Se conservaron los cambios locales anteriores y los diseños de los módulos existentes.

## Archivos de esta intervención

Las rutas siguientes son relativas a `/home/willian/Desarrollo/Huata/`. Se obtuvieron comparando hashes al inicio y al final, para separarlas de los cambios locales previos.

- `EcolacteosHuata/androidApp/src/main/kotlin/com/example/ecolacteoshuata/MainActivity.kt`
- `EcolacteosHuata/shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/yape/HuataReceptionQrTest.kt`
- `EcolacteosHuata/shared/src/androidMain/kotlin/com/example/ecolacteoshuata/yape/HuataYapeReception.kt`
- `EcolacteosHuata/shared/src/androidMain/kotlin/com/example/ecolacteoshuata/yape/ReceptionQr.kt`
- `EcolacteosHuata/shared/src/androidMain/kotlin/com/example/ecolacteoshuata/yape/store/OutboxDatabase.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/platform/YapeReception.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/common/conexion/ConexionSyncSheet.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/common/conexion/RecepcionYapeScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/common/conexion/SolicitudRecepcionYape.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/navigation/RootAutorizado.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/navigation/Screen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/vendedor/DisponibilidadWebControl.kt`
- `laravelEcolacteosHuata/app/Authorization/Pantallas.php`
- `laravelEcolacteosHuata/app/Http/Controllers/Api/HuataYapeController.php`
- `laravelEcolacteosHuata/app/Livewire/ReceptoresYape.php`
- `laravelEcolacteosHuata/app/Models/User.php`
- `laravelEcolacteosHuata/app/Services/Payments/AsignacionesReceptor.php`
- `laravelEcolacteosHuata/app/Services/Payments/BridgeProvisioning.php`
- `laravelEcolacteosHuata/app/Services/Payments/HuataYape.php`
- `laravelEcolacteosHuata/app/Services/Payments/ReceptoresService.php`
- `laravelEcolacteosHuata/app/Services/Payments/SolicitudesRecepcionYape.php`
- `laravelEcolacteosHuata/app/Services/VendedoresWeb.php`
- `laravelEcolacteosHuata/database/migrations/2026_10_27_230000_add_yape_reception_requests.php`
- `laravelEcolacteosHuata/resources/views/livewire/disponibilidad-web.blade.php`
- `laravelEcolacteosHuata/resources/views/livewire/receptores-yape.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡receptores-yape.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡configuracion-pagos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡pedidos-web.blade.php`
- `laravelEcolacteosHuata/routes/admin.php`
- `laravelEcolacteosHuata/routes/api.php`
- `laravelEcolacteosHuata/routes/vendedor.php`
- `laravelEcolacteosHuata/tests/Feature/DisponibilidadFinanzasAndroidTest.php`
- `laravelEcolacteosHuata/tests/Feature/DistribucionesRolesTest.php`
- `laravelEcolacteosHuata/tests/Feature/HuataYapeIntegradoTest.php`
- `laravelEcolacteosHuata/tests/Feature/ReceptoresAdministracionTest.php`
- `laravelEcolacteosHuata/tests/Feature/ReceptoresCoreIntegrationTest.php`
- `laravelEcolacteosHuata/tests/Feature/SolicitudesRecepcionYapeTest.php`

Además: este documento, las notas de actualización en `docs/YAPE_INTEGRADO_HUATA.md` y `docs/FINANZAS_ANDROID_DISPONIBILIDAD_WEB.md`, y los assets Web/APK generados por sus compilaciones.
