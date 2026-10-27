# Vinculación y habilitación segura de receptores Yape

> Estado posterior 27/10/2026: Core ya fue desplegado con el reinicio y la rotación de aplicación expresamente autorizados. La ampliación requiere actualizar manualmente Huata y Bridge e inscribir el dispositivo. El receptor sigue sin verificar/deshabilitado. Véase [EJECUCION_FINAL_PAGOS_YAPE.md](EJECUCION_FINAL_PAGOS_YAPE.md); las afirmaciones de despliegue pendiente y Bridge sin actualización de este informe son históricas.

Fecha: 27 de octubre de 2026. Corrección focalizada; no se reconstruyó la integración.

## Resultado y límite operativo

Se implementó el flujo administrativo **registrar → confirmar físicamente → verificar identidad/QR → comunicar Bridge → habilitar**. Verificar ya no exige estar habilitado ni online. Habilitar sí exige verificación previa, coincidencia técnica y heartbeat de un dispositivo asociado al receptor con listener activo durante los últimos 120 segundos.

**No se verificó físicamente ni se habilitó el receptor operativo. No se envió dinero. No se reiniciaron los servicios manuales.** El código nuevo del Core está compilado y probado, pero su distribución ejecutable operativa no se reemplazó: hace falta un despliegue/reinicio manual para disponer del endpoint y del esquema nuevos. Bridge no requiere actualización ni reinstalación.

## 1. Causa raíz

El método privado `comprobar()` de la página de Configuración de pagos exigía simultáneamente `verified`, `enabled`, `online` e identidad/imagen coincidentes. `verificar()` dependía de ese resultado antes de registrar una aprobación local. Core obtenía `verified=false` y `enabled=false` del archivo privado y solo ofrecía consultas de receptores. No había una operación administrativa inicial capaz de superar esa condición circular.

Se retiró esa comprobación indivisible de la página y se centralizó el procedimiento en `ReceptoresService`. La admisión de cobros en `CobrosService` conserva sus condiciones comerciales estrictas. Solo se corrigió allí la equivalencia entre teléfono peruano nacional y `51` + nueve dígitos, para no verificar una cuenta que posteriormente fuera rechazada por una diferencia de formato.

## 2. Autenticación y procedimiento administrativo

Core añade `clients[].receiverAdministration`, opcional y **false por defecto**. El operador debe concederlo explícitamente al cliente servidor Laravel existente, en su configuración privada. No concede derechos a los dispositivos ni crea una credencial nueva.

Es una delegación de servicio: Core autentica la credencial privada del servidor, exige ese alcance y restringe el receptor a `clients[].receivers`. Laravel autentica la sesión web y exige usuario activo, acceso a Configuración de pagos y `puedeAdministracionCritica()` antes de cualquier mutación. La identidad `staff_<id>` se transmite para auditoría; **no es una credencial** ni sustituye esas comprobaciones. Core no interpreta roles de Laravel ni verifica por sí mismo la contraseña del operador. La clave de aplicación debe permanecer exclusivamente en el servidor; nunca en APK, navegador, repositorio o capturas.

Endpoint nuevo, sin rutas equivalentes de laboratorio:

`POST /api/admin/receivers/{id}/state`

Cuerpo: `operationId` único, `expectedVersion`, `actor`, `action` (`verify`, `enable`, `disable`), `name`, `phone`, `qrSha256`, `physicalConfirmed`.

- `verify`: exige confirmación física explícita, receptor existente y coincidencia exacta de nombre/QR y teléfono normalizado. Registra verificación y deja recepción deshabilitada. Puede ejecutarse con Bridge offline.
- `enable`: exige la aprobación persistida y un dispositivo de ese receptor con heartbeat reciente y listener activo. Conserva la comparación de identidad/QR. No crea pagos.
- `disable`: permite bloquear recepción incluso si los datos locales fueron editados erróneamente. Conserva la verificación para una habilitación posterior válida.
- Una versión desactualizada produce conflicto; no reemplaza un estado concurrente.
- Repetir el mismo `operationId` y cuerpo no duplica la operación. Cambiar el cuerpo con el mismo identificador se rechaza. Una repetición antigua devuelve el estado actual: no restaura una habilitación que después se desactivó.
- Solo se aceptan credenciales de aplicación con alcance administrativo. Un Bearer de dispositivo, una aplicación sin alcance o una petición anónima no pueden activar receptores. Se conservan las restricciones HTTPS/HTTP exclusivamente loopback del Core.

`GET /api/receivers` conserva los campos existentes y añade `version`, `deviceLinked`, `verifiedBy`, `verifiedAt`.

## 3. Persistencia y registro de confirmación física

No hay migraciones ni tablas nuevas en Laravel; se reutilizan `receptores_pago.verified_at`, `verified_by`, `enabled`, `version`.

Core incorpora, mediante inicialización incremental de su SQLite comercial:

- `canonical_receiver_state`: receptor, huella compuesta de identidad, verificación, habilitación, versión y actor/fecha de la última verificación.
- `canonical_receiver_audit`: operación única, receptor, cliente de servicio, actor humano declarado por Laravel, acción, confirmación física, fecha, versiones y estados anteriores/posteriores, huella de petición, huella de identidad y SHA-256 del QR.
- `canonical_devices.receiver_id`: vincula el heartbeat persistido a la asociación realmente autenticada. Un heartbeat antiguo de un dispositivo reasignado no acredita al nuevo receptor. Tras la actualización se requiere un heartbeat nuevo.

Estado y auditoría se escriben en una misma transacción. Un fallo al registrar la auditoría revierte la aprobación/habilitación. La verificación física es la declaración explícita del administrador en la casilla de la interfaz; **no es certificación bancaria automática ni una inferencia del heartbeat**. Laravel conserva la fecha que devuelve Core y el ID del administrador.

En la primera carga se preservan los indicadores explícitos de instalaciones antiguas ya provisionadas; esa compatibilidad no inventa un actor/fecha de verificación física histórica. Los receptores nuevos conservan los valores seguros por defecto. Una vez existente el estado persistente, las banderas del JSON **no son el mecanismo de habilitación**: cambiar nombre, teléfono de cuenta o QR invalida la aprobación y deshabilita; se registra el cambio de identidad. Las claves y su rotación no forman parte de la huella de identidad.

No se eliminan ni reescriben pagos/evidencias. Los eventos `REVIEW_RECEIVER_UNVERIFIED` permanecen en revisión después de habilitar; no se promocionan a `OBSERVED` ni a `PAID`.

## 4. Laravel y fallos parciales

Se conserva la página, tarjetas, colores y componentes Flux. Se incorpora en la tarjeta existente una casilla de confirmación física y la posibilidad explícita de confirmar la desactivación en Core cuando Laravel ya está bloqueado. No hay otra pantalla de configuración.

Consultar disponibilidad informa por separado: QR inexistente/alterado, receptor ausente o no autorizado, huella distinta, nombre distinto, número distinto, falta de dispositivo, Bridge offline/listener inactivo, pendiente de confirmación física, Core no disponible, estado verificado pero deshabilitado y falta de reconciliación local.

Cada mutación administrativa bloquea y versiona primero la recepción local en una transacción **anterior** a la petición de red. Un timeout no revierte ese bloqueo. La respuesta de mutación debe ir seguida de una nueva consulta autoritativa con identidad, versión y estados coherentes. Solo entonces se actualiza Laravel mediante comparación de versión. Una edición concurrente del QR no hereda la aprobación anterior.

Si se interrumpe el proceso después de que Core confirme una operación, Laravel puede quedar deshabilitado aunque Core esté habilitado. Ese estado se informa y **Huata no admite cobros nuevos mediante ese receptor**: su admisión exige ambos estados y la identidad correcta. El administrador puede confirmar la desactivación en Core o repetir la habilitación tras revisar la situación. No se afirma una transacción distribuida entre ambas bases. No se confirma una desactivación remota si Core no responde.

## 5. Estado operativo leído sin modificaciones

Lectura efectuada con la credencial del proceso Laravel existente, sin imprimirla:

- Laravel: proceso PHP observado `106906`, puerto esperado 8000; integración habilitada por variables de su proceso, clave configurada y correspondiente al cliente `tienda`.
- Core: proceso observado `106822`, `commercial`, `127.0.0.1:8087`.
- Configuración privada: `/home/willian/.config/payment-core/commercial.json`.
- Base comercial existente: `/home/willian/.config/payment-core/commercial.sqlite`.
- Cliente: `tienda`, autorizado para `receptor-001`, todavía sin alcance `receiverAdministration`.
- Receptor: `receptor-001`, `verified=false`, `enabled=false`, `online=true` en el momento de la lectura; dispositivo configurado `bridge-001`.

La conexión autenticada y el listener activo no demuestran titularidad. No se marcó una confirmación física en nombre del operador.

La shell de herramientas no hereda las variables privadas del servidor PHP: un bootstrap CLI independiente mostró la integración deshabilitada. Para esta comprobación se contrastó el entorno del proceso en ejecución y su consulta autenticada al Core. No se cambió `.env`, la configuración cacheada, las claves ni el entorno de los servicios.

## 6. Pasos manuales exactos para el receptor existente

**Estos pasos no se ejecutaron sobre la configuración ni la base operativas.** Conservar las terminales y las variables privadas que utilizan actualmente los servicios. La credencial del servidor ha recibido un nuevo poder administrativo: coordinar su rotación si sigue siendo una de las expuestas, antes de usar ese alcance.

1. Reservar una ventana manual sin nuevos cobros; revisar las intenciones y evidencias pendientes y el estado de la cola Bridge sin borrarlas. Preparar respaldo consistente de las bases Laravel/Core y del JSON privado, con acceso exclusivo del operador. Para SQLite en uso emplear la API de backup de SQLite; copiar solo el archivo `.sqlite` mientras hay WAL/escrituras no garantiza un respaldo consistente. No restaurar una copia antigua sobre actividad financiera posterior.
2. Conceder únicamente el alcance al cliente servidor existente, conservando todos los datos y banderas del receptor:

```bash
cd /home/willian/Desarrollo/laboratorio/qr
python3 tools/authorize_receiver_administration.py \
  --config /home/willian/.config/payment-core/commercial.json \
  --client tienda \
  --confirm-trusted-laravel-server
```

La herramienta exige archivo regular privado, propiedad del operador; rechaza enlaces simbólicos, permisos compartidos, cliente inexistente y cambios concurrentes. Guarda respaldo privado y reemplaza atómicamente. Es idempotente. **No verifica ni habilita receptores y no crea/cambia claves.** No se ejecutó aquí con ese archivo real.

3. En la terminal donde ejecutas **tu Core manual**, detenerlo con Ctrl+C. No detener Laravel, Scheduler ni Bridge. Solo después de detener ese Core, construir su distribución para no sobrescribir JAR que utiliza un proceso en ejecución:

```bash
cd /home/willian/Desarrollo/laboratorio/qr
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline installDist
```

4. En esa misma terminal, arrancar manualmente con las rutas y escucha privadas existentes:

```bash
cd /home/willian/Desarrollo/laboratorio/qr
PAYMENT_MODE=commercial \
PAYMENT_HOST=127.0.0.1 \
PAYMENT_PORT=8087 \
PAYMENT_CONFIG_FILE=/home/willian/.config/payment-core/commercial.json \
PAYMENT_DB_PATH=/home/willian/.config/payment-core/commercial.sqlite \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
build/install/qr/bin/qr server
```

Este reinicio manual carga código/alcance y crea las estructuras incrementales. No poner `verified/enabled=true` en JSON ni ejecutar SQL para habilitar. No usar base vacía, modo LAB ni otro puerto.

5. Esperar el siguiente heartbeat del Bridge ya instalado. En **Configuración de pagos**, con sesión de administrador autorizado, seleccionar al vendedor que posee `receptor-001`. Consultar disponibilidad y verificar que nombre y celular correspondan; la referencia debe seguir siendo `receptor-001`. La imagen exacta almacenada debe tener el SHA-256 registrado en Core. Una captura visual parecida o recomprimida puede tener otra huella: no sustituir el hash para sortear el rechazo.
6. El operador contrasta físicamente la cuenta Yape abierta en el teléfono receptor, titular, número y destino mostrado al escanear el QR guardado, sin enviar dinero. Si hay cualquier discrepancia, detener el alta y corregir los datos de registro bajo su procedimiento autorizado. Bridge conectado no sustituye este paso.
7. **Solo después de la comprobación humana**, marcar la casilla y pulsar **Verificar vinculación técnica**. Core registra actor, momento, identidad/QR y confirmación física; Laravel registra su aprobación. La recepción permanece deshabilitada.
8. Consultar disponibilidad: Bridge debe estar comunicado con listener activo y asociado al mismo receptor. Pulsar **Activar nuevos pedidos**; consultar otra vez. Debe indicar recepción habilitada y coincidir con Core. Si hubo interrupción, usar **Confirmar desactivación en Core** para volver a un estado bloqueado o revisar y repetir la habilitación; no editar banderas de base.
9. Para bloquear después, usar **Desactivar nuevos pedidos**. La operación se audita. Si falla la red, Laravel queda bloqueado y debe confirmarse la desactivación remota cuando Core vuelva.

No es necesario recompilar ni instalar Bridge; no se cambia su URL, claves, IP, puertos o cola. Laravel necesita conservar el entorno privado de su proceso manual actual. No se ejecutan migraciones Laravel ni se reinicia automáticamente ningún servicio.

## 7. Rotación coordinada de claves expuestas — preparada, no ejecutada

No se generaron ni mostraron credenciales operativas. No se rotó ninguna clave. Los respaldos que contienen claves expuestas también son material privado: conservarlos con permisos 0600, ubicación restringida y una retención controlada.

### Dependencias comprobadas

- Aplicación: `clients[].key` ↔ `PAYMENT_CORE_KEY` del proceso Laravel (`HttpPaymentCore` y comprobación de integración de `BridgeIngress`). `canonical_bindings` utiliza **client_id**, no la clave. La nueva administración exige además el alcance persistido en el JSON privado.
- Dispositivo: `devices[].key` ↔ `DeviceCredentials` del Bridge. `BackendBridge.trySend()` obtiene la credencial actual al reintentar cada evento; la cola no necesita borrarse. Mantener **device id, receiverId y packageName** para conservar atribución y deduplicación. La clave de transporte es distinta de la clave AES del Android Keystore que cifra datos locales.
- Correlación: `matchSecret` genera `canonical_bindings.claim_hmac` y `canonical_evidence.code_hmac`. El chequeo de duplicados también compara `code_hmac`. No hay versión de secreto ni keyring en el esquema actual. Los textos/códigos originales no se conservan en Core para recalcular libremente esos HMAC.

### Aplicación y dispositivo

1. Bloquear nuevos cobros por el procedimiento administrativo; revisar cola y pendientes, registrar el corte y obtener respaldos consistentes. No cancelar, borrar ni marcar PAID para facilitar la rotación.
2. En un entorno privado del operador, generar dos secretos independientes y transferirlos mediante un canal seguro al despliegue correspondiente, sin argumentos de shell, logs, Git o capturas. **No ejecutado aquí.**
3. Coordinar el cambio de `clients[].key` con el entorno/configuración realmente utilizado por Laravel; preservar `clients[].id=tienda`, receptores y base comercial. El servidor no admite una lista de claves antiguas/nuevas para el mismo cliente: programar el breve corte y los reinicios manuales correspondientes. No crear otro client id para eludirlo: perdería el acceso a bindings existentes.
4. Coordinar `devices[].key` con la credencial del Bridge ya instalado, preservando `bridge-001` y `receptor-001`. Actualizar usando sus controles existentes de vinculación. No reinstalar, borrar datos, cambiar el alias AES ni limpiar la cola. Durante el corte los 401 deben conservar los eventos pendientes. Si cambia únicamente la aplicación, la clave del dispositivo no debe cambiar accidentalmente, y viceversa.
5. Reiniciar manualmente solo los procesos cuyo entorno/configuración cambió. Comprobar que las claves antiguas sean rechazadas, el cliente nuevo siga consultando sus PAY anteriores, Bridge recupere heartbeat y reintentos con ACK idempotente y la cola no pierda eventos. No imprimir los valores ni las cabeceras Authorization en las verificaciones.
6. Ante fallo previo a cualquier actividad nueva, recuperar coordinadamente los archivos/entornos de respaldo sin sobreescribir movimientos posteriores. Las claves expuestas recuperadas solo sirven como contingencia temporal y obligan a completar otra rotación segura. No restaurar una base antigua para corregir únicamente una credencial.

### Secreto de correlación: requisito pendiente antes de rotarlo

**No existe una rotación directa segura de `matchSecret` con el esquema actual.** Incluso con cero PAY pendientes, un replay de una evidencia antigua puede generar un HMAC diferente y entrar en conflicto de idempotencia. Cambiar únicamente el JSON puede romper conciliación de claims/evidencias ya persistidos y reintentos del Bridge.

Antes de su despliegue debe prepararse y probarse, como cambio independiente y controlado, una compatibilidad versionada de HMAC/keyring para evidencias, claims y duplicados, con conservación protegida de la versión anterior durante la transición. El procedimiento deberá demostrar correlación de pendientes preexistentes y rechazo de reutilizaciones antes de retirar el secreto viejo. Esta corrección no modifica la lógica financiera para introducir esa migración ni inventa que pueda hacerse cambiando una bandera.

Mientras ese prerrequisito no esté satisfecho, **mantener el secreto actual sin cambios** y tratar su rotación como pendiente de seguridad; no declarar saneadas todas las claves. No reinicializar la base, borrar evidencias ni archivar el historial fuera de servicio como atajo.

## 8. Validación aislada

- Core: **267 pruebas aprobadas**, cero fallos/errores/omisiones; incluye 19 nuevas pruebas de administración y la regresión comercial/laboratorio existente. Compilación JVM y tests correctos. No se reemplazó `build/install/qr` del servicio activo.
- Herramienta de alcance: **4 pruebas Python aprobadas**, con archivos temporales; conserva datos/claves/banderas, respaldo 0600, idempotencia, rechazo de cliente ausente, enlace simbólico y permisos compartidos.
- Laravel: **492 aprobadas, 2041 aserciones**, cinco pruebas opt-in omitidas en la ejecución general. SQLite en memoria, sin usar la base operativa.
- Integración opt-in: **esas cinco pruebas ejecutadas y aprobadas, 68 aserciones**, con las clases nuevas de Core, SQLite temporal y claves/datos ficticios. Incluye verificación/habilitación/deshabilitación reales del motor temporal, bloqueo offline, dispositivo de otro receptor, PAY pendiente y regresión Laravel–Bridge ingress–Core con conciliación exacta, no reutilización y ambigüedad.
- PHP: sintaxis de los servicios comprobada y Pint aprobado en los archivos PHP seleccionados.

Cobertura de las 18 condiciones solicitadas:

| Condición | Evidencia de pruebas |
|---|---|
| 1. Existente sin verificar | Core `existing receiver remains unverified without money changes`; diagnóstico Laravel |
| 2. Verificación inicial autorizada | Core `initial physical verification...`; integración opt-in completa |
| 3. Actor sin privilegios | Laravel peticiones Livewire manipuladas; Core credencial común/dispositivo/anónima |
| 4–6. QR/nombre/teléfono incorrectos | Pruebas independientes Core y Laravel; archivo QR alterado/inexistente |
| 7. Otro receptor/dispositivo | Heartbeat ajeno no habilita; notificación de receptor ajeno rechazada |
| 8. Offline | Core listener inactivo/heartbeat vencido; diagnóstico y bloqueo Laravel |
| 9. Falta confirmación física | Verificar/activar rechazan aunque exista heartbeat |
| 10. Verificado y online habilita | Core y flujo real de API temporal |
| 11. Deshabilitación | Bloquea PAY nuevo y conserva historial; auditoría |
| 12. Reinicio | Nueva instancia conserva verified/enabled/disabled; cambio de identidad invalida |
| 13. Repetición | Una operación/auditoría por identificador; cuerpo alterado y versión obsoleta rechazados |
| 14. Fallo intermedio | Trigger de fallo de auditoría revierte Core; fallo tras commit remoto mantiene Laravel bloqueado |
| 15. PAY solo apto | No verificado/offline/deshabilitado rechazan; apto crea PENDING |
| 16. Evidencia antigua | La revisión previa a verificación no se promociona; ventana y expiración conservadas |
| 17. Una evidencia/operación | Regresión canónica y circuito de integración: sin reutilización ni segunda venta |
| 18. Sin simulación comercial | Rutas LAB/YW ausentes; autenticación independiente; simulador rechaza intenciones Yape |

Comandos reproducibles (las pruebas usan únicamente bases aisladas):

```bash
# Core: no instala ni reemplaza la distribución operativa.
cd /home/willian/Desarrollo/laboratorio/qr
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p test_authorize_receiver_administration.py

cd /home/willian/Desarrollo/Huata/laravelEcolacteosHuata
APP_ENV=testing DB_CONNECTION=sqlite DB_DATABASE=:memory: DB_URL= php vendor/bin/pest --compact
python3 tools/test_receiver_provisioning.py
```

El runner utiliza clases nuevas compiladas y los JAR de dependencias de la distribución existente, inicia únicamente su propio Core en loopback/puerto efímero distinto de 8000/8087 y elimina su configuración ficticia al terminar. No despliega código en el proceso manual ni modifica Bridge.

## 9. Archivos de esta corrección

Laravel, todos bajo `/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/`:

- `app/Services/Payments/ReceptoresService.php` (nuevo): identidad, diagnóstico y coordinación segura.
- `app/Services/Payments/PaymentCore.php`: contrato administrativo.
- `app/Services/Payments/HttpPaymentCore.php`: operación autenticada, modo comercial y errores específicos.
- `app/Services/Payments/TelefonoPeru.php`: equivalencia normalizada segura.
- `app/Services/Payments/CobrosService.php`: únicamente comparación normalizada de teléfonos en las dos comprobaciones existentes.
- `resources/views/pages/vendedor/⚡configuracion-pagos.blade.php`: acciones administrativas y casilla física en la tarjeta existente.
- `tests/Feature/ReceptoresAdministracionTest.php`, `tests/Feature/ReceptoresCoreIntegrationTest.php`, `tests/Support/ReceiverCoreFixture.php` (nuevos).
- `tests/Feature/PagosCanonicosTest.php`: contrato nuevo y regresión de teléfono nacional.
- `tests/Feature/YapeBridgeIntegrationTest.php`: permite también la ruta temporal privada del nuevo runner.
- `tools/test_receiver_provisioning.py` (nuevo).
- Este documento, referencia de actualización en `docs/YAPE_BRIDGE_WIFI.md` y procedimiento corregido en `docs/PAGOS_OPERACION.md`.

Core, todos bajo `/home/willian/Desarrollo/laboratorio/qr/`:

- `src/main/kotlin/pe/willian/qrlab/server/CommercialConfig.kt`: alcance administrativo opt-in.
- `src/main/kotlin/pe/willian/qrlab/server/CommercialRoutes.kt`: endpoint autenticado y respuestas de rechazo.
- `src/main/kotlin/pe/willian/qrlab/server/CanonicalPayments.kt`: consulta de estados persistentes, heartbeat vinculado, elegibilidad atómica; conciliación financiera conservada.
- `src/main/kotlin/pe/willian/qrlab/server/ReceiverAdministration.kt` (nuevo): estados, auditoría y transiciones administrativas.
- `src/test/kotlin/pe/willian/qrlab/server/ReceiverAdministrationTest.kt` (nuevo).
- `tools/authorize_receiver_administration.py`, `tools/test_authorize_receiver_administration.py` (nuevos).

Bridge: ningún archivo modificado, compilación ni instalación. Sin cambios de roles, distribuciones, KMP Huata, diseños globales, IP, puertos, datos comerciales, cuentas, claves operativas o servicios manuales. No se hizo commit/push.

## 10. Qué falta para un pago auténtico

El operador debe coordinar la seguridad de las credenciales expuestas, desplegar/reiniciar manualmente Core, provisionar el alcance del cliente, completar la confirmación física en la interfaz, verificar un heartbeat nuevo y habilitar. La rotación del secreto de correlación conserva el prerrequisito técnico indicado; no está resuelta por este aprovisionamiento.

Después podrá crear manualmente un pedido/intención y realizar, si lo decide, un pago real controlado. Debe contrastarse una notificación auténtica, su receptor, importe exacto, código observado, ventana, evidencia única y estado canónico antes de dar la prueba por válida. Un receptor disponible, heartbeat, ACK o PAY PENDING no acredita dinero. No se declara pago real funcionando por aprobar pruebas con datos sintéticos.
