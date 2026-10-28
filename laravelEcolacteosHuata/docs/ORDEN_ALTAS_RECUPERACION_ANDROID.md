# Orden de altas y recuperación automática de sincronización

## Resultado

Los listados operativos usan `created_at DESC, id DESC` en Web y su equivalente real en Kotlin. Las altas locales tienen una secuencia de primera inserción que no cambia al editar, reasignar un UUID a su ID remoto o aplicar `INSERT OR REPLACE` durante el sync. No se modifican registros históricos para ordenarlos.

Los formularios Livewire vuelven a la primera página tras crear en Usuarios, Clientes y Descuentos, conservando búsqueda y filtros. Al editar mantienen la página y la fecha de alta. El resto de componentes utiliza su render actual y los repositorios/StateFlow existentes. No hay botones nuevos ni cambios de diseño.

Se corrigió la agrupación del texto de búsqueda en Clientes para que sus condiciones `OR` respeten también el filtro de compras. La métrica independiente «cliente frecuente» conserva su cálculo y alcance anteriores.

## Datos de orden en Android

Era necesario conservar metadatos: varias tablas locales no tienen fecha de alta, sus identificadores locales son UUID y `INSERT OR REPLACE` cambia `rowid`. Ordenar solo por esos valores pondría algunas ediciones primero y perdería el orden real entre Web y Android.

Se añadió exclusivamente la migración local **`21.sqm`**, que incorpora `orden_alta`; el esquema pasa de 21 a 22. No altera tablas operativas, filas históricas, saldos ni colas. Su instalación registra una secuencia estable de primera inserción. Las filas históricas sin fecha conocida se registran con fecha NULL, sin inventarla. La prueba de actualización del esquema conservó las filas existentes.

`/api/mobile/sync` incluye ahora `altas`, una lista adicional de tabla, identificador y fecha real de creación UTC. Se obtiene después de aplicar el ámbito de datos existente y también se envía en las respuestas incrementales, para que una instalación actualizada recupere las fechas de sus filas anteriores sin borrar datos ni forzar una descarga completa. Para Usuarios se limita además a los usuarios efectivamente incluidos en la respuesta.

La confirmación de crear/editar usuarios añade su fecha real de creación a la respuesta móvil. Kotlin la guarda como metadato antes de reflejar el usuario confirmado en su listado. No cambia validaciones, roles, distribuciones o permisos. Los clientes antiguos ignoran los campos adicionales; Kotlin conserva compatibilidad con servidores que todavía no los envíen.

Las fechas reales priman; los identificadores numéricos desempatan numéricamente en sentido descendente (10 antes que 2); las altas locales con UUID utilizan su secuencia estable. Una edición o un reintento no produce una alta nueva.

## Excepciones preservadas

- Secuencia de paradas/rutas y orden geográfico.
- Horarios, calendarios y vigencia de precios.
- Prioridades de reposición/stock y alertas de trabajo.
- Selectores funcionales y cálculos que requieren cronología; el cálculo previo de variaciones de compras conserva su cronología aunque el historial se presente por alta.
- Gestor de roles, distribuciones, Finanzas, libro contable y pantallas de configuración de pagos/Bridge.
- Pagos, recargas, receptores, intenciones y operaciones del carrito/pedidos. En los listados de pedidos solo se agregó el desempate por ID al orden de creación existente.

No existían controles de orden manual en los listados modificados. Se conservaron búsquedas, filtros, paginación y ámbitos de usuario; no se aplican ordenaciones globales a modelos Laravel.

## Recuperación inicial

El sincronizador agenda hasta **cuatro reintentos** de una descarga inicial fallida, con esperas de **2, 5, 15 y 30 segundos** entre intentos. Si todos fallan, son cinco intentos totales, incluido el primero. El refresco periódico de datos ya descargados no reinicia indefinidamente una primera descarga agotada.

Se consideran transitorios los errores de red/timeout y HTTP 408, 425, 429 y 5xx. No se reintentan como red una sesión rechazada, HTTP 403/404, formatos incompatibles o errores locales de datos. Un fallo transitorio conserva el token y la sesión; no solicita una contraseña. Ante un 401 real continúa la validación de sesión existente.

Los intentos comparten el mismo mutex con login, sincronización manual y Worker. Se cancelan si cambia el servidor/token, se desactiva la conexión o se pierde la red. Un nuevo episodio de conectividad o un reintento manual permite iniciar un ciclo acotado. La primera descarga observa el resultado de los intentos posteriores y pasa automáticamente a «Lista» cuando llegan los datos; Proveedor mantiene su observación reactiva del perfil.

Los avisos y paneles conservan su diseño. No se vuelve a presentar la falta de descarga como «rol nuevo» ni como perfil inexistente antes de completar la sincronización.

## Validación focalizada

- Laravel: **16 pruebas, 130 aserciones aprobadas**: creación/edición Livewire, conservación de filtros, paginación estable, fechas de alta reales, ámbito del proveedor, filtros operativos y endpoints autenticados.
- Kotlin: **17 pruebas aprobadas**: altas locales, edición y reemplazo sin mover filas, fechas reales y desempate numérico, migración conservadora, contrato Laravel→Kotlin, sync repetido sin duplicados, recuperación automática de dos fallos 503 y transición a «Lista» sin botón, límite de cuatro reintentos, 401/403/404 sin bucles, cambio de token, identidad/cola offline, permisos de Producción y comprobaciones existentes de importes y transporte financiero.
- Sintaxis PHP verificada en los 37 archivos de aplicación afectados.
- Migración y pruebas se ejecutaron únicamente en SQLite de pruebas, sin modificar bases operativas ni realizar operaciones financieras reales.

La prueba física en el Infinix queda pendiente; no se instaló la APK. Las comprobaciones anteriores son de integración automatizada.

## Prueba manual

1. Actualizar la APK conservando datos, con paquete/firma compatibles. No desinstalar ni borrar datos.
2. Crear un registro que coincida con la búsqueda/filtros actuales. En listados paginados con formulario propio, comprobar que se muestra en la primera página. Crear otro y comprobar el orden; editar el anterior y verificar que actualiza sus datos sin subirlo artificialmente.
3. Recorrer páginas con varias altas en el mismo segundo; comprobar que no se repiten ni se saltan filas. Los resultados deben seguir respetando filtros y usuario.
4. Sincronizar los registros de Web con Android y comparar el orden. Las fechas de creación no se sustituyen por fechas operativas ni por la última edición.
5. En un entorno de prueba, hacer que la API responda temporalmente 503 durante una descarga inicial y después vuelva a responder correctamente. Sin pulsar «Volver a intentar», comprobar las esperas y la llegada del perfil. No detener servicios operativos para provocar el fallo.
6. Verificar que el token y la sesión permanecen; si el error persiste, el ciclo se detiene tras cuatro reintentos. Una sesión realmente rechazada requiere la confirmación habitual; no debe entrar en un bucle de contraseñas.

## Archivos modificados

- `EcolacteosHuata/shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/OrdenRecuperacionTest.kt`
- `EcolacteosHuata/shared/src/androidHostTest/resources/huata-orden-altas-sync.json`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/dto/SyncDtos.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/sync/OrdenAltas.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/sync/RegistroCambios.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/sync/SyncService.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/model/AdministracionRoles.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/AdministrarRolesUseCase.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/acopiador/inventario/AcopiadorInventarioViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/clientes/AdminClientesViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/login/PrimeraDescargaViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/produccion/compras/ProduccionComprasViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/proveedor/entregas/ProveedorEntregasViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/proveedor/laboratorio/ProveedorLaboratorioViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/21.sqm`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Acopiador.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Cliente.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Compra.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Comunicado.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Descuento.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/EntregaPlanta.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Insumo.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/OrdenAlta.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Produccion.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Producto.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Proveedor.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Receta.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Recojo.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/ResultadoLaboratorio.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/UnidadMedida.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Usuario.sq`
- `EcolacteosHuata/shared/src/commonMain/sqldelight/com/example/ecolacteoshuata/db/Venta.sq`
- `laravelEcolacteosHuata/app/Http/Controllers/Api/MobileAutorizacionController.php`
- `laravelEcolacteosHuata/app/Http/Controllers/Api/MobileSyncController.php`
- `laravelEcolacteosHuata/app/Http/Controllers/ClientesWeb/CuentaController.php`
- `laravelEcolacteosHuata/app/Support/OrdenAltas.php`
- `laravelEcolacteosHuata/resources/views/pages/acopiador/⚡inventario.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/acopiador/⚡panel.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/acopiador/⚡recojo.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡acopiador-entrega.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡acopiador-recojo.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡asignar-roles.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡descuentos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡panel.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡proveedor-entregas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡proveedor-laboratorio.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡usuarios.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡comunicados.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡entregas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡laboratorio.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡panel.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡compras.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡insumos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡panel.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡productos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡recetas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡registros.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡unidades.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/proveedor/⚡comunicados.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/proveedor/⚡entregas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/proveedor/⚡laboratorio.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/proveedor/⚡panel.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡clientes.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡historial.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡inventario.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡panel.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡pedidos-web.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡produccion.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡ventas.blade.php`
- `laravelEcolacteosHuata/tests/Feature/OrdenAltasListadosTest.php`

## APK

Compilación final: **BUILD SUCCESSFUL** (`:androidApp:assembleDebug`) y **17 pruebas Android aprobadas, cero fallos**.

- APK: `/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- SHA-256 APK: `32c93c25f7671c9078d02833dccb84952ae00409c0d5d02b13fb8369de456e7b`.
- Paquete: `com.example.ecolacteoshuata.huata`, versión `1.0`, código `1`, minSdk 24, targetSdk 36.
- Certificado SHA-256: `53bb3b0f9c147ec628c1111ab06621030e999c1b0d3d58f7a06931e6e60004ed`, verificado con `apksigner`; mismo certificado que la APK local anterior. Comprobar la compatibilidad con la instalación física antes de actualizar conservando datos.
- APK generada una sola vez al finalizar las pruebas, sin instalar.

Las pruebas Laravel finales suman 11 casos de orden/filtros (80 aserciones) y 5 de API autenticada/Proveedor (50 aserciones). Se comprobó que las 99 fuentes protegidas seleccionadas de Finanzas, pagos, Bridge, estilos/componentes globales y manifiesto Android siguen idénticas al inicio del trabajo.

Comando final Kotlin (desde `EcolacteosHuata`):

```bash
ANDROID_HOME=/home/willian/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest --tests '*OrdenRecuperacionTest' --tests '*RescateSincronizacionTest' --tests '*AutorizacionMultiplataformaTest.reconectar*' --tests '*AutorizacionMultiplataformaTest.pendientesDeOtraSesion*' --tests '*AutorizacionMultiplataformaTest.loginRemoto*' --tests '*IntegracionRolesAdministrativosTest.supervisorSinEnum*' --tests '*FinanzasAndroidTest' --tests '*HuataTransportPrivacyTest' --offline --no-daemon
```


## Preservación

Sin migraciones Laravel, cambios en bases operativas, commit, push, reinicio de servicios o instalación automática. Sin cambios de diseño, colores, encabezados, navegación, estilos globales, cálculos financieros, métodos de pago, Payment Core o Yape Bridge integrado/original.
