# Ecolácteos Huata

Laravel web/API + Kotlin Multiplatform Huata + Payment Core comercial + Yape Bridge Android.
La instalación inicial permite entrar como administrador sin cuentas, bases ni claves operativas.

| Directorio | Contenido |
|---|---|
| `laravelEcolacteosHuata/` | Laravel 13, Livewire, compradores, ventas, pagos y autorización central |
| `EcolacteosHuata/` | Huata Kotlin Multiplatform/Compose y SQLDelight |
| `payment-core/` | Motor canónico Kotlin/JVM, pruebas y herramientas |
| `yape-bridge/` | Captura cifrada Android, inscripción y comprobación Huata–Bridge |

El catálogo actual incluye 43 pantallas, 40 correspondencias Android, seis roles base, roles personalizados y distribuciones Inicial/En desarrollo. Las plantillas históricas se conservan.
El administrador inicial recibe el rol real `administrador` y la distribución Inicial vigente
(17 pantallas tras las migraciones de Finanzas y Receptores Yape, incluidas Usuarios, Asignar roles y Roles y funciones). Como administrador protegido
puede administrar y delegar las funciones del catálogo; no se cambia la distribución aprobada
para conceder automáticamente todas las operaciones comerciales. Las demás funciones continúan
bajo sus asignaciones y reglas de propiedad, stock y pagos.

## Funcionalidades actuales

- Carrito simplificado, pedidos pendientes, recargas y pago con saldo; WhatsApp del vendedor después del pago.
- Finanzas en Web y Android, clientes y saldos, custodia, rendiciones e historial por pedido, con autorización dinámica.
- Disponibilidad de pedidos web controlada por administración; reparto equilibrado por pedido y conservación del responsable de cada pedido histórico.
- Roles dinámicos, distribuciones y filtros en las 27 páginas Web y 24 componentes Kotlin documentados.
- Eliminación segura de usuarios relacionados, con alternativa de desactivación.
- Sesión y sincronización Android corregidas, recuperación automática de conexión y registros nuevos primero.
- Bridge integrado en una única APK Huata: solicitudes aceptar/rechazar para cualquier usuario activo, permiso Android y vinculación automática.
- Administración central de Receptores Yape, asignación explícita a vendedores, transferencia controlada y tarjeta Android con diagnóstico. Los vendedores ya no administran Bridge ni pausan la recepción web.

El administrador no vende ni recibe pedidos automáticamente: debe tener además un rol comercial explícito.
Para probar compras en una instalación nueva, crea/asigna un vendedor con `vendedor.pedidos-web`, mantén su usuario activo y su recepción autorizada por administración. Una pausa histórica requiere resolución administrativa explícita. Para usar Finanzas, asigna `admin.finanzas` mediante el gestor existente; no se exige Administración General adicional. Android debe actualizar sus autorizaciones con conexión.

Detalles y pruebas: [pedidos, saldo y finanzas](laravelEcolacteosHuata/docs/PEDIDOS_SALDO_FINANZAS.md),
[filtros Web/Kotlin](laravelEcolacteosHuata/docs/FILTROS_WEB_KOTLIN.md) y
[Finanzas Android y disponibilidad](laravelEcolacteosHuata/docs/FINANZAS_ANDROID_DISPONIBILIDAD_WEB.md).
Para los avances recientes: [orden y recuperación Android](laravelEcolacteosHuata/docs/ORDEN_ALTAS_RECUPERACION_ANDROID.md),
[rescate de sesión/sincronización y Finanzas](laravelEcolacteosHuata/docs/RESCATE_ANDROID_SINCRONIZACION_FINANZAS.md) y
[recepción Yape centralizada](laravelEcolacteosHuata/docs/SIMPLIFICACION_RECEPCION_YAPE.md).

## Requisitos

PHP 8.3+ (el lock se valida con Composer), Composer 2, SQLite/PDO SQLite y extensiones PHP exigidas
por `composer check-platform-reqs`; Node 22.12+ o Node 24 LTS y npm. Java/Android no son necesarios
para el primer acceso web. Para Core y las APK: JDK 21, Android SDK 36 y las herramientas de cada
Gradle Wrapper. No instales dependencias en una base o instalación operativa para seguir esta guía.

## Instalación local limpia

```bash
git clone https://github.com/Will-M7/checalo.git
cd checalo/laravelEcolacteosHuata
cp .env.example .env
composer install --no-interaction
php artisan key:generate
touch database/database.sqlite
php artisan migrate
php artisan db:seed
npm ci
npm run build
php artisan serve --host=0.0.0.0 --port=8000
```

Abre `http://localhost:8000/login` (acceso del personal): usuario **admin**, contraseña **1234**.
`/mi-cuenta/ingresar` es el acceso separado de compradores. Desde otro equipo de la misma LAN usa
la IP de tu servidor en lugar de localhost. No se configura USB ni adb reverse como transporte.

`php artisan db:seed` es idempotente: no duplica admin/equipo/membresía ni reinicia contraseña,
roles o asignaciones existentes. Solo crea la primera cuenta sobre una instalación sin usuarios;
si ya existe otro administrador operativo, se niega a agregar privilegios automáticamente.
No ejecuta el antiguo conjunto masivo de demostración. Esos datos están disponibles opcionalmente
con `php artisan db:seed --class=DatosDemostracionSeeder` solo en local/testing; la operación se
omite cuando ya hay datos de negocio o usuarios adicionales. No es un procedimiento para poblar
una base operativa.

## Producción

**admin/1234 está prohibido fuera de APP_ENV=local/testing, incluso con db:seed --force.** Para una
base nueva de producción configura `APP_ENV=production`, `APP_DEBUG=false`, HTTPS y
`HUATA_INITIAL_ADMIN_PASSWORD` con una contraseña propia de 12+ caracteres, mayúsculas,
minúsculas y números antes de sembrar. Un cambio posterior de esa variable no rota una cuenta
existente. El seeder no sustituye el flujo autorizado de recuperación de contraseñas. No publiques
`.env`, bases, claves, archivos de firma ni el directorio `private/`.

## Pagos opcionales, sin bloquear el login

El ejemplo deja `PAYMENT_CORE_ENABLED=false`. El administrador puede entrar y configurar el
sistema. Los pedidos pendientes requieren vendedores disponibles y se guardan sin cobrar ni reservar stock; los cobros Yape requieren la configuración verificada correspondiente. No se incluyen receptores
habilitados, dispositivo inscrito, evidencia bancaria ni claves reales.

Para una integración propia, compila Core:

```bash
cd ../payment-core
JAVA_HOME=/ruta/a/jdk-21 ./gradlew test installDist
```

Genera configuración/credenciales nuevas siguiendo `payment-core/examples/README.md` y guarda los
archivos privados fuera de `public` y de Git. Después inicia MANUALMENTE Core en otra terminal:

```bash
cd payment-core
PAYMENT_MODE=commercial PAYMENT_HOST=127.0.0.1 PAYMENT_PORT=8087 \
PAYMENT_CONFIG_FILE=/ruta/privada/commercial.json \
PAYMENT_DB_PATH=/ruta/privada/commercial.sqlite \
JAVA_HOME=/ruta/a/jdk-21 build/install/qr/bin/qr server
```

En el `.env` de Laravel configura `PAYMENT_CORE_ENABLED=true`, `PAYMENT_CORE_URL=http://127.0.0.1:8087`,
`PAYMENT_CORE_ALLOW_LOCAL_HTTP=true` y `PAYMENT_CORE_KEY_FILE=/ruta/privada/application.key` (0600).
No pegues claves en el navegador ni en fuentes de las APK. Para el scheduler usa otra terminal:

```bash
cd laravelEcolacteosHuata
php artisan schedule:work
```

Para Huata, desde `EcolacteosHuata`, ejecuta `./gradlew :androidApp:assembleDebug`;
la APK queda en `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. El Bridge integrado
forma parte de esa misma APK para todos los usuarios. No se publican APK ni claves de firma.
El proyecto `yape-bridge/` original se conserva para mantenimiento e historial; no es necesario
instalar una segunda aplicación para el flujo integrado.

Configura la conexión habitual de Huata con Laravel. Después:

1. Administración → Receptores Yape: titular, número, QR y usuario responsable activo de cualquier rol.
2. El responsable inicia sesión o sincroniza Android y acepta o rechaza la solicitud. Si acepta,
   concede acceso desde Ajustes de Android; la vinculación técnica se completa sin claves manuales.
3. La tarjeta Recepción Yape permite consultar autorización, listener y eventos. El administrador
   conserva la comprobación física de titular/cuenta/QR y la habilitación comercial.
4. Administración asigna el receptor a los vendedores. Varios pueden usarlo sin activar Bridge
   en sus propios teléfonos. Las asignaciones pendientes no habilitan cobros.
5. Si existe un Bridge anterior, coordina la revisión de su cola y su detención antes de confirmar
   la transferencia controlada desde la misma pantalla. No borres sus datos ni fuerces dos colectores.

Aceptar el uso del teléfono no demuestra titularidad ni confirma dinero. Los eventos sin código
o ambiguos permanecen en revisión. La revocación no es instantánea mientras el teléfono está
sin red; el servidor vuelve a comprobarla en cada transmisión. Android puede suspender trabajo
en segundo plano. Consulta la guía de recepción para estos límites y para cambios de identidad
que deban reconciliarse con Core. No se instalan APK ni se inicia ningún servicio al clonar.

## Pruebas

```bash
cd laravelEcolacteosHuata
php vendor/bin/pest --compact
```

`phpunit.xml` usa SQLite `:memory:`. Los siete casos opt-in de Core se omiten normalmente. Para
probarlos, después de `payment-core/./gradlew test installDist`, desde Laravel:
`JAVA_HOME=/ruta/a/jdk-21 python3 tools/test_receiver_provisioning.py`. La herramienta crea un Core
TEMPORAL en un puerto aleatorio, usa secretos ficticios y lo detiene al finalizar; no utiliza
8000/8087 ni bases operativas. `BRIDGE_TEST_CORE_PROJECT`/`RECEIVER_TEST_CORE_PROJECT` permiten
sobrescribir la ruta hermana `payment-core/` si tu organización local difiere.

Consulta `laravelEcolacteosHuata/docs/` para contratos, distribuciones, seguridad, restauración
visual y limitaciones de las entregas anteriores. Sus rutas absolutas y PID describen la laptop
de origen: no son comandos de despliegue para tu instalación. Esta guía usa rutas relativas.
La validación inicial no acredita una prueba con dinero real ni un despliegue productivo.

Procedencia y referencia histórica: [UPSTREAM.md](UPSTREAM.md).
