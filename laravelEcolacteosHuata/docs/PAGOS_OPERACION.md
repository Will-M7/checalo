# Operación de pagos canónicos

Esta integración utiliza notificaciones autenticadas del dispositivo receptor. No es una API bancaria ni una certificación de Yape. Las pruebas automatizadas e integrales emplean datos sintéticos aislados. Ningún receptor real fue activado, ninguna transferencia real fue ejecutada y ninguna APK fue instalada durante la implementación.

## Preparación y arranque en Ubuntu

Los proyectos conservan sus dependencias actuales. Se requiere JDK 21, PHP con las extensiones del proyecto (incluido GD para imágenes/PDF y PDO SQLite), Composer, Node/npm y Android SDK para compilar APK. No se instalaron paquetes del sistema ni se modificaron archivos `.env` o credenciales reales.

Payment Core se inicia en modo comercial por defecto. Su configuración privada es obligatoria. El modo LAB necesita `PAYMENT_MODE=lab`, otra base indicada expresamente mediante `QR_LAB_DB_PATH` y no puede montar una base comercial. El modo comercial rechaza adoptar registros de una base LAB.

Crear una lista JSON **sin secretos** con los datos reales ya contrastados por el operador. Cada elemento debe tener `id`, `name`, `phone`, `qrFile` (ruta absoluta a la imagen exacta subida en Laravel) y `deviceId`. Los identificadores admiten letras, números, `_` y `-`. Una misma cuenta telefónica no puede tener dos identidades de receptor. El nombre debe coincidir exactamente en ambos sistemas y el teléfono se normaliza a `51` y nueve dígitos.

```bash
install -d -m 700 "$HOME/.config/payment-core"
cd /home/willian/Desarrollo/laboratorio/qr
python3 tools/create_commercial_config.py \
  --receivers "$HOME/.config/payment-core/receptores-publicos.json" \
  --output "$HOME/.config/payment-core/commercial.json" \
  --client-id tienda --allow-loopback-http
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline test installDist
PAYMENT_MODE=commercial PAYMENT_HOST=127.0.0.1 PAYMENT_PORT=8087 \
PAYMENT_CONFIG_FILE="$HOME/.config/payment-core/commercial.json" \
PAYMENT_DB_PATH="$HOME/.config/payment-core/commercial.sqlite" \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 build/install/qr/bin/qr server
```

El generador crea exclusivamente un archivo nuevo con permisos `600`, claves aleatorias independientes y receptores `verified=false`, `enabled=false`. No imprime secretos y se niega a sobrescribir configuraciones existentes. No regenerar `matchSecret` al reiniciar: invalida las comparaciones HMAC de operaciones pendientes. Realizar las copias de seguridad del archivo privado y de SQLite con los controles de acceso del operador. No añadirlos al repositorio.

Para Laravel, configurar en su entorno privado `PAYMENT_CORE_ENABLED=true`, `PAYMENT_CORE_URL=http://127.0.0.1:8087`, `PAYMENT_CORE_ALLOW_LOCAL_HTTP=true` únicamente si el motor está en ese mismo host, y `PAYMENT_CORE_KEY` con la clave del elemento `clients` correspondiente. Si el motor está en otro host, usar una URL HTTPS válida y `PAYMENT_CORE_ALLOW_LOCAL_HTTP=false`. No pegar claves en una línea de comandos guardada en el historial ni imprimirlas.

Ejemplo de carga de la clave en el entorno del proceso local, sin mostrarla:

```bash
export PAYMENT_CORE_ENABLED=true PAYMENT_CORE_URL=http://127.0.0.1:8087 PAYMENT_CORE_ALLOW_LOCAL_HTTP=true
export PAYMENT_CORE_KEY="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["clients"][0]["key"])' "$HOME/.config/payment-core/commercial.json")"
cd /home/willian/Desarrollo/Huata/laravelEcolacteosHuata
# Con respaldo operativo previo de la base, ejecutar la migración incremental:
php artisan migrate --force
# Crear el enlace solo si aún no existe:
test -e public/storage || php artisan storage:link
npm run build
php artisan serve --host=127.0.0.1 --port=8000
```

En otro proceso con el mismo entorno privado de Laravel:

```bash
cd /home/willian/Desarrollo/Huata/laravelEcolacteosHuata
php artisan schedule:work
# Recuperación manual adicional, idempotente:
php artisan pagos:reconciliar --limit=100
```

La migración se probó sobre un esquema anterior con boletas y pedidos existentes; no fue aplicada a la base operativa. No requiere reconstruirla. La reversión se bloquea cuando existe historial financiero: corregir entonces mediante una migración hacia adelante. No ejecutar reconstrucciones ni borrados de base.

En producción utilizar el servidor web y supervisor del despliegue existente, no `artisan serve`. El motor escucha por defecto en loopback; publicar el acceso del Bridge mediante un proxy HTTPS con certificado válido que reenvíe al listener loopback. En ese caso, `allowLocalHttp=true` habilita únicamente el tramo local del proxy al motor; la URL del Bridge sigue siendo HTTPS. No abrir el puerto HTTP del motor a la LAN o Internet ni confiar en un encabezado `X-Forwarded-Proto` enviado por clientes. Esta implementación no instala un proxy ni obtiene un certificado.

## Alta y vinculación inicial de un vendedor

1. Usar el personal existente con rol vendedor y estado activo. En el mismo menú, abrir **Configuración de pagos**, debajo de **Inventario**. Guardar nombre del receptor, celular Yape, WhatsApp e imagen PNG/JPEG/WebP real (máximo 2 MB, entre 100 y 2400 píxeles por dimensión).
2. El QR subido no prueba titularidad. El administrador supervisa desde la misma pantalla, seleccionando al vendedor. Solo el administrador asigna la referencia externa del receptor. Debe coincidir con `receivers[].id` del archivo privado de Payment Core.
3. El operador contrasta personalmente el nombre/cuenta que muestra Yape al escanear la imagen, la cuenta receptora abierta en el teléfono y el dispositivo que ejecutará Bridge. Utilizar exactamente esa imagen en `qrFile`: el motor registra su SHA-256 y Laravel comprueba archivo, hash, nombre y teléfono.
4. En el Bridge de desarrollo compilado, el operador introduce URL HTTPS del motor, referencia del receptor y la clave privada de **ese dispositivo**, tomada de su entrada `devices`. No utilizar la clave de Laravel. La clave queda cifrada con Android Keystore; no se incorpora en la APK ni en el código. La base del outbox también cifra el payload pendiente.
5. El operador instala o actualiza manualmente la APK apropiada, concede el acceso a notificaciones y comprueba que el listener está conectado. El botón técnico de conexión autentica un heartbeat. No se modificaron permisos, instalaciones ni datos de un teléfono desde esta implementación.
6. Mientras el receptor no esté verificado/habilitado en el estado persistente de Core, los eventos auténticos reconocidos pueden recibir ACK `REVIEW_RECEIVER_UNVERIFIED`; permanecen en cuarentena y no pagan intenciones. Esto permite contrastar el recorrido técnico antes de autorizar cobros. No se crean intenciones nuevas para esos receptores.
7. Provisionar explícitamente el alcance administrativo del cliente servidor y actualizar Core mediante el procedimiento de [VINCULACION_RECEPTORES_YAPE.md](VINCULACION_RECEPTORES_YAPE.md). No modificar manualmente `verified/enabled` en JSON para habilitar; las aprobaciones nuevas se registran en SQLite y se auditan.
8. En Configuración de pagos, el administrador confirma físicamente titular, número, cuenta y QR; marca la casilla y pulsa **Verificar vinculación técnica**. La identidad/hash deben coincidir; no se exige estar habilitado ni online para verificar. Después de recibir heartbeat reciente del dispositivo correcto con listener activo, pulsa **Activar nuevos pedidos**. Se confirma el estado de Core antes de habilitar localmente. Un cambio concurrente de QR o error de red conserva el bloqueo; la desactivación también se confirma y audita en Core.

Cambiar identidad, número, QR o referencia deshabilita el receptor local y exige nueva verificación. Los pedidos existentes conservan imagen, teléfono, WhatsApp, vendedor, referencia y versión en su snapshot. No borrar imágenes históricas utilizadas por pagos. Modificar únicamente WhatsApp afecta nuevos pedidos; no redirige contactos de pedidos anteriores. Deshabilitar un vendedor impide asignaciones nuevas y no descarta dinero ya recibido para sus pedidos anteriores.

## Contrato HTTP

Dos clases de credenciales Bearer independientes: `clients[].key` para la aplicación comercial y `devices[].key` para un dispositivo vinculado a un solo receptor. Los identificadores de dispositivo/receptor del cuerpo no sustituyen la autenticación. `/health` solo informa salud y modo, sin datos financieros.

| Método/ruta | Principal | Entrada y efecto |
|---|---|---|
| `GET /api/receivers` | Aplicación | Receptores permitidos a esa aplicación; nombre, teléfono, hash, habilitación, verificación y heartbeat reciente |
| `POST /api/payments` | Aplicación | `reference`, `amountMinor`, `receiverId`, `subject`, `currency="604"`, `method="YAPE_NOTIFICATION"`; creación idempotente |
| `GET /api/payments/{id}` | Aplicación propietaria | Consulta canónica autenticada; autoridad usada por Laravel para aplicar dinero |
| `POST /api/payments/{id}/resolve-yape` | Aplicación propietaria | `securityCode`: tres dígitos exactos; registra solo su HMAC y concilia evidencia |
| `POST /api/payments/{id}/cancel` | Aplicación propietaria | Cierra una intención pendiente; nunca retrocede un pago confirmado |
| `GET /api/payments/{id}/events` | Aplicación propietaria | SSE de estado; al reconectar obtiene estado durable, sin usarlo como prueba definitiva de cobro |
| `POST /api/bridge/heartbeat` | Dispositivo | `listenerConnected`: disponibilidad técnica, no prueba bancaria |
| `POST /api/bridge/notifications` | Dispositivo | `bridgeEventId`, `receiverId`, paquete esperado, `postedAt` ISO con precisión de milisegundos y campos mínimos de notificación auténtica; ACK JSON con `accepted` booleano e `eventId` exacto |

La respuesta canónica conserva `id`, `reference`, `amountMinor`, `currency`, `receiverId`, `qrSha256`, `status`, `expiresAt`, `evidenceId` y `result`. Estados: `PENDING`, `PAID`, `EXPIRED`, `CANCELLED`. Resultados incluyen `WAITING_PAYMENT`, `PAYMENT_NOT_FOUND`, `CODE_NOT_MATCHED`, `AMBIGUOUS` y `REVIEW_CODE_UNAVAILABLE`. Sin código auténtico, coincidencia única o ventana temporal válida no se acredita automáticamente. El teléfono del comprador es un contacto declarado y no identifica bancariamente al pagador.

Una referencia reutilizada con datos distintos produce conflicto. Un timeout se recupera con la misma referencia; no genera otro pago. El motor impone cinco validaciones por minuto por pago, sujeto y receptor, con contadores durables compartidos entre aplicaciones. Laravel añade límites de cuenta, pago, receptor e IP. No conservar códigos en logs, URLs, WhatsApp ni historial de comandos.

## Operación y contingencias

El comprador usa `/mi-cuenta`, con guard, proveedor y recuperación separados del personal. Para terminar el checkout necesita esa sesión. La recuperación de contraseña envía el enlace con el transporte real de correo de Laravel; con mailer `log` o `array` fuera de pruebas se informa que no está configurado. Configurar SMTP u otro transporte real antes de ofrecer recuperación operativa. No hay verificación telefónica simulada.

Yape en el POS utiliza el receptor autorizado del vendedor y el código de tres dígitos que comunica el comprador, dentro de la pantalla existente. Las ventas móviles Yape nuevas se rechazan en servidor; Android Huata guarda el intento como borrador SQLite local por vendedor y lo recupera al reabrir la pantalla, sin boleta, pago confirmado ni fila de sincronización. Su migración `19.sqm` agrega únicamente la tabla de borradores (versión 19 a 20) y conserva el esquema anterior. Si se completa legítimamente la venta local por otro método, el borrador se elimina en la misma transacción que esa venta. Debe completarse Yape en el POS web verificado. Las operaciones legítimas de otros métodos mantienen su sincronización. Plin, tarjeta y efectivo mantienen sus reglas y no reciben una etiqueta de verificación bancaria automática.

La reconciliación consulta intenciones pendientes o `PAID` aún no aplicadas. Cada aplicación se serializa en SQLite; venta, ítems, stock, boleta y movimiento de saldo se confirman conjuntamente. No se mantienen bloqueos de inventario durante llamadas HTTP. Si falta stock tras un cobro web confirmado, el pago sigue `PAID`, el pedido queda cancelado con `saldo_acreditado` y se registra un único crédito. El dinero se puede gastar únicamente en compras, sin retiros ni transferencias entre clientes. Las ventas manuales del POS también conservan una referencia de canal única para recuperar reintentos concurrentes sin emitir otra boleta.

La evidencia ambigua queda en cuarentena persistente; cancelar una intención competidora no autoriza a reutilizarla automáticamente. Las notificaciones agregadas o con códigos contradictorios también requieren revisión. Un reintento del outbox no reactiva por sí solo la disponibilidad del listener: esta depende del heartbeat explícito y reciente. Mantener sincronizados los relojes del teléfono y del servidor para respetar las ventanas temporales.

Una venta presencial sin cuenta web que recibió dinero cuando ya no había stock queda `PAID` con `pagado_sin_stock_revision`, sin venta ficticia y sin aplicarse a un comprador inventado. Requiere que administración resuelva la entrega, identificación del comprador o devolución procedente por sus canales operativos; el registro no se elimina. No se implementó una API bancaria de devolución.

Los ajustes/reversos administrativos disponen de un comando explícito y auditable. Requiere administrador activo, referencia única/motivo de al menos diez caracteres, importe entero en unidades menores, reverso exacto del movimiento de ese cliente y saldo final no negativo:

```bash
# Ejemplos de sintaxis: sustituir los identificadores y la referencia por un expediente autorizado.
php artisan saldo:ajustar ID_CLIENTE 100 --actor=ID_ADMIN --referencia=EXPEDIENTE_AUTORIZADO
php artisan saldo:ajustar --actor=ID_ADMIN --referencia=EXPEDIENTE_REVERSO --reverso=ID_MOVIMIENTO -- ID_CLIENTE -100
```

Estos comandos modifican únicamente el libro interno; no envían dinero. Un movimiento original no se edita ni borra. Las ventas vinculadas a pagos o consumos de saldo están excluidas de la limpieza de historial y de la eliminación móvil.

## Pruebas reproducibles

```bash
cd /home/willian/Desarrollo/Huata/laravelEcolacteosHuata
APP_ENV=testing DB_CONNECTION=sqlite DB_DATABASE=:memory: vendor/bin/pest --compact
npm run build
PAYMENTS_INTEGRATION_ALLOWED=1 \
PAYMENT_CORE_BINARY=/home/willian/Desarrollo/laboratorio/qr/build/install/qr/bin/qr \
php tests/Support/CanonicalCircuit.php

cd /home/willian/Desarrollo/laboratorio/qr
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline test installDist
cd /home/willian/Desarrollo/laboratorio/yape-bridge
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/willian/Android/Sdk \
./gradlew --offline :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug
cd /home/willian/Desarrollo/Huata/EcolacteosHuata
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/willian/Android/Sdk \
./gradlew --offline :androidApp:assembleDebug :shared:testAndroidHostTest
python3 tools/test_yape_draft_migration.py
```

El harness exige autorización explícita, crea un directorio aleatorio privado en `/tmp`, bases nuevas, imágenes marcadas **TEST ONLY**, claves efímeras y un servidor comercial solo loopback en puerto libre. Prueba HTTP real con el adaptador, migración incremental sobre datos previos sintéticos, PDF, recuperación tras reinicio y concurrencia multiproceso en reparto, aplicación, correlativos, última unidad y doble gasto. Produce `report.json`, logs y una boleta de prueba dentro de ese directorio. No usa el Bridge instalado ni movimientos reales de Yape. Requiere GD y `pcntl`.

En este host PHP no cargaba GD; para las pruebas se extrajo un paquete de GD en `/tmp/huata-payments/php-gd`, sin instalarlo en el sistema. Se ejecutó PHP con `-d extension=/tmp/huata-payments/php-gd/usr/lib/php/20250925/gd.so`. Para operación normal, el operador debe habilitar GD en su runtime habitual. Las dependencias Gradle necesarias estaban en caché; `--offline` evita descargas no necesarias.

## Validación física pendiente

El operador debe comprobar en un teléfono receptor auténtico: instalación/variante correcta, URL TLS y certificado, credencial del dispositivo, cuenta Yape y QR correspondientes, permisos del listener, recepción de una notificación entrante real, paquete/texto/importe/código que esa versión de Yape exponga, ACK, modo sin red y posterior reintento del outbox, reinicio del proceso Android, heartbeat al quitar permisos y ahorro de batería. Debe contrastar manualmente el ingreso en Yape con la operación comercial. No introducir ni inventar un código si Yape no lo expone en la notificación: queda revisión pendiente.

También falta comprobar físicamente Android Keystore y la migración/cifrado del SQLite nativo del outbox sobre una instalación anterior real. Las pruebas JVM cubren el protocolo y las decisiones de entrega/reintento; no sustituyen estas comprobaciones Android. No se borran ni sustituyen los datos de una instalación existente.
