# Finanzas Android, disponibilidad y pedidos por vendedor — 27/10/2026

Implementación sobre los avances locales, sin restaurar el respaldo ni crear de nuevo el rol Finanzas. No se hicieron commits, pushes, instalaciones, reinicios de servicios ni cambios en Payment Core o Yape Bridge. Se conservaron los filtros existentes y los estilos globales, portada, layouts y menús. Se añadieron controles funcionales dentro de las pantallas actuales y la correspondencia nativa de Finanzas al catálogo dinámico.

## Reglas implementadas

- Vender y recibir dinero son responsabilidades independientes. Una cuenta receptora no concede capacidad comercial. Los permisos amplios del rol histórico `administrador` no bastan para vender ni entrar al reparto. Hace falta un rol comercial adicional activo (Vendedor o un rol dinámico con la pantalla comercial correspondiente). No se retiraron permisos ni modificaron distribuciones guardadas.
- La disponibilidad para nuevos pedidos exige usuario activo, rol comercial dinámico con `vendedor.pedidos-web`, autorización administrativa y ausencia de pausa propia. Bridge, QR, cuentas receptoras y presencia de red no determinan disponibilidad comercial.
- Se conserva el reparto circular existente por ID. La transacción que guarda el pedido escribe primero la fila de secuencia, bloquea los candidatos, selecciona el siguiente disponible y guarda al vendedor en ese pedido. También se conserva la referencia idempotente del carrito. Una recarga ya no avanza el turno de pedidos. Sin candidatos se devuelve validación y no se guarda el pedido.
- No hay un vendedor permanente en el perfil del comprador. Se congela el contacto comercial de cada pedido; se mantiene el WhatsApp comercial previo y se permite usar el celular del vendedor cuando no tiene cuenta receptora propia.
- Pausa, desautorización o desactivación no reescriben pedidos previos. Los listados de pedidos limitan al vendedor a sus asignaciones; administración con `admin.usuarios` puede supervisar y reasignar pendientes a vendedores disponibles, indicando motivo. La auditoría guarda actor, responsable anterior, nuevo responsable y motivo. Las intenciones originales, sus referencias, receptor, QR y pagos confirmados se conservan. No se reasignan pedidos vendidos ni con una intención PAID.
- Las recargas nuevas usan el último pedido únicamente como contexto de esa operación. Sin pedidos utilizan el receptor central de la política, o el receptor autorizado de un vendedor disponible si no hay central. No heredan de forma permanente al vendedor de la primera recarga. Reintentar una intención existente conserva el receptor original. La cuenta central puede recibir recargas sin tener capacidad de venta.

## Finanzas Web y Android

La pantalla compartida sigue siendo `admin.finanzas`. Ahora corresponde a `Screen.AdminFinanzas` en Kotlin. Tanto las consultas como la rendición móvil exigen ese permiso en Laravel; no exigen Administración General. El rol Finanzas existente y sus asignaciones no se recrearon ni modificaron.

Android consulta el resumen y el mismo libro financiero de Laravel: recaudación, ventas, recargas confirmadas, saldos/pasivos de clientes, custodia, fondos entregados, rendiciones, egresos, movimientos y advertencias de conciliación. Los filtros de período, vendedor, receptor, tipo y conciliación usan las mismas consultas del servidor que Web. La posición actual no se filtra, conforme al criterio del módulo existente.

Los importes móviles utilizan céntimos enteros `Long`; los totales de pedidos conservan la cadena decimal del servidor. La aplicación no calcula una segunda contabilidad. Las consultas y las operaciones requieren conexión en esta entrega; el estado consultado puede permanecer en memoria si falla una actualización. La confirmación de una rendición solo aparece después de la respuesta afirmativa del servidor. No se crea una cola financiera offline.

Cada rendición conserva su UUID y contenido al reintentar. El servicio también reconoce la misma constancia del mismo ingreso con el mismo importe aunque se reabra la aplicación con otro UUID; una constancia existente con otro importe se rechaza. El límite de fondos pendientes y el asiento se validan en la transacción del servicio financiero actual.

### Clientes y saldos

En Web y Android, dentro de Finanzas:

- Nombre, celular, correo, saldo aplicado actual, recargas canónicas aplicadas y consumo/debitos aplicados del libro de saldo.
- Cantidad de pedidos, pendientes y pagados.
- Buscador, filtros de saldo y vendedor que atendió alguno de los pedidos, paginación e historial por pedido con fecha, vendedor real, importe y estados.
- No existe edición de saldos desde la tabla. Los abonos sin ingreso conciliado siguen identificados en el resumen y no se cuentan como recarga canónica.

## Procedimientos de prueba manual

Usar cuentas de prueba autorizadas y evitar crear pagos o movimientos ficticios en la base operativa.

1. En Roles y funciones, comprobar que el vendedor tiene un rol activo con `vendedor.pedidos-web`. Para vender presencialmente se requiere `vendedor.ventas`. Un administrador que también venderá necesita además ese rol comercial; ser propietario de una cuenta Yape no basta.
2. Web: Administración → Usuarios muestra la recepción web de cada usuario. Usar Autorizar/Desautorizar recepción. Android: Usuarios → Disponibilidad de vendedores web permite consultar, buscar, filtrar y cambiar la misma autorización.
3. Como vendedor: en el Panel de ventas Web o Compras por web usar Activar/Pausar recepción. En Android usar el control de recepción del Panel del vendedor. Cambiar disponibilidad requiere conexión y confirmación del servidor. Desautorizar administrativamente no borra la pausa; reautorizar tampoco la elimina.
4. Con dos vendedores disponibles, crear dos pedidos pendientes del mismo comprador: comprobar que alternan responsables. Pausar uno y comprobar que el siguiente pedido va al otro. Desautorizarlo como administrador y comprobar que su intento de activación se rechaza. Con todos inhabilitados, el carrito debe mostrar que no hay vendedores disponibles y no guardar el pedido.
5. Abrir los pedidos anteriores para comprobar que conservan su responsable. Para una reasignación necesaria, entrar como administrador en Compras por web, escoger nuevo responsable disponible, indicar un motivo de al menos diez caracteres y pulsar Reasignar en el pedido pendiente. El vendedor mantiene acceso a sus pedidos anteriores según su permiso, aunque esté pausado.
6. Web: Finanzas → Clientes y saldos → buscar cliente → Ver pedidos y vendedores. Android: iniciar sesión con conexión, actualizar autorizaciones y abrir Finanzas desde los módulos del rol autorizado; desplazarse hasta Clientes y saldos, aplicar búsqueda/filtros y consultar el historial. No se presenta vendedor permanente.
7. Para una rendición real, elegir un ingreso con custodia pendiente, introducir importe y constancia, marcar la recepción real y confirmar. Sin red no debe mostrarse una nueva entrega confirmada. Ante timeout, reintentar la misma operación y constancia. No usar este procedimiento para fabricar registros de prueba operativos.
8. Para eliminar usuarios, la Web comprueba referencias antes del borrado, incluyendo cascadas, sesiones, identidad móvil y notificaciones. Si existen, muestra un mensaje y Desactivar usuario. La cuenta principal sigue protegida. Solo una cuenta sin relaciones puede borrarse físicamente. Los mismos guardas se aplican a la eliminación recibida por sincronización móvil, con mensaje de rechazo conservado para revisión; para desactivar desde Android se mantiene el campo Activo del formulario existente.

## Migración y datos operativos

Migración incremental aplicada: `database/migrations/2026_10_27_210000_add_disponibilidad_vendedores_web.php`.

Solo añade a `users`:

- `pedidos_web_autorizado`, booleano, inicialmente true para conservar la recepción de vendedores comerciales existentes.
- `pedidos_web_pausado`, booleano, inicialmente false.

Esos valores no convierten al administrador ni al receptor en vendedor. El rollback conserva los estados administrativos y pausas.

Respaldo consistente SQLite previo, privado (0600):
`storage/app/private/antes-disponibilidad-web-20261027T201620Z.sqlite`.

Se compararon los contenidos de las columnas previas de las 66 tablas: solo cambió `migrations`. Usuarios, roles, distribuciones, credenciales, relaciones, pedidos, cuentas receptoras y libros anteriores se conservaron. `integrity_check=ok`; `foreign_key_check` sin errores. La base mantiene 12 usuarios, un vendedor actualmente disponible y cero movimientos financieros; no se crearon operaciones ficticias ni se modificó ningún usuario operativo para las pruebas.

## Validación focalizada

Todas las pruebas usan bases aisladas, HTTP simulado o un servidor de prueba local:

- 10 pruebas nuevas, 77 aserciones, en `DisponibilidadFinanzasAndroidTest.php`: separación de administrador/receptor/vendedor, reparto por pedido, pausa y revocación, ausencia de candidatos, roles comerciales personalizados, autorización financiera, saldo e historial, rendición idempotente, reasignación y conservación de intención, recarga central y eliminación segura.
- 13 pruebas existentes de `PedidosSaldoFinanzasTest.php`, 123 aserciones, aprobadas.
- 9 regresiones seleccionadas de `PagosCanonicosTest.php`, 47 aserciones, aprobadas: permisos comerciales dinámicos, reintento estable, conciliación canónica, falta de stock, saldo, vendedor desactivado e historial/intenciones vencidas.
- 1 prueba del catálogo/distribuciones, 16 aserciones: 42 pantallas y 40 correspondencias nativas; las plantillas históricas permanecen intactas.
- Android: 2 pruebas nuevas de transporte financiero/importes y 9 existentes de autorización multiplataforma; cero fallos. Comprueban céntimos exactos, rechazo sin confirmación, bloqueo offline, autenticación, reintento con el mismo cuerpo y correspondencia nativa.
- Sintaxis PHP revisada en 16 archivos. Las vistas nuevas/modificadas se renderizaron en las pruebas Livewire correspondientes. No se ejecutaron suites generales.

Comandos principales:

```bash
DB_CONNECTION=sqlite DB_DATABASE=:memory: DB_URL='' php vendor/bin/pest tests/Feature/DisponibilidadFinanzasAndroidTest.php tests/Feature/PedidosSaldoFinanzasTest.php --compact
ANDROID_HOME=/home/willian/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest --tests '*FinanzasAndroidTest*' --tests '*AutorizacionMultiplataformaTest*' --offline --no-daemon
```

El primer comando se ejecuta desde Laravel; el segundo desde EcolacteosHuata.

APK debug generada, firma v2 verificada, sin instalar:
`/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

SHA-256:
`21c8d2cca33ad6e5807bfee73adf44f24a4fe4e315b5b1bfb0af7ab041023f7e`.

La compilación terminó con `BUILD SUCCESSFUL`. D8 emitió advertencias de metadatos Kotlin/R8 del toolchain existente; no se alteraron versiones para esta tarea. No se validó la APK en un dispositivo físico ni se realizó un pago Yape real.

## Archivos de esta intervención

Laravel (rutas relativas a `laravelEcolacteosHuata/`):

- `app/Models/User.php`, `app/Models/ReceptorPago.php`.
- `app/Authorization/Autorizacion.php`, `app/Authorization/Pantallas.php`.
- `app/Services/VendedoresWeb.php` y `app/Services/EliminarUsuarioSeguro.php` (nuevos).
- `app/Services/Payments/CobrosService.php`, `app/Services/Payments/VentasService.php`.
- `app/Services/Finanzas/FinanzasService.php`, `app/Services/Finanzas/ConsultasFinanzas.php` (nuevo).
- `app/Http/Controllers/Api/MobileFinanzasController.php`, `app/Http/Controllers/Api/MobileVendedoresWebController.php` (nuevos), `app/Http/Controllers/Api/MobileCambiosController.php`.
- `app/Livewire/DisponibilidadWeb.php`, `resources/views/livewire/disponibilidad-web.blade.php` (nuevos).
- `resources/views/pages/admin/⚡usuarios.blade.php`, `resources/views/pages/admin/⚡finanzas.blade.php`.
- `resources/views/pages/vendedor/⚡panel.blade.php`, `resources/views/pages/vendedor/⚡pedidos-web.blade.php`.
- `routes/api.php`: cuatro endpoints de Finanzas y cuatro de disponibilidad con el middleware móvil existente.
- `database/migrations/2026_10_27_210000_add_disponibilidad_vendedores_web.php` (nueva).
- `tests/Feature/DisponibilidadFinanzasAndroidTest.php` (nuevo), `tests/Feature/DistribucionesRolesTest.php`.
- `docs/FINANZAS_ANDROID_DISPONIBILIDAD_WEB.md` (este documento), nota de actualización en `docs/PEDIDOS_SALDO_FINANZAS.md`.

Kotlin (rutas bajo `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/`):

- `data/remote/datasource/FinanzasRemota.kt` (nuevo).
- `domain/model/AutorizacionUsuario.kt`, `domain/usecase/RegistrarVentaUseCase.kt`: capacidad comercial recibida del servidor, conservada en el snapshot local; una autorización antigua debe actualizarse antes de registrar ventas nuevas.
- `di/DataModule.kt`, `di/DomainModule.kt`, `di/ViewModelModule.kt`.
- `presentation/navigation/Screen.kt`, `presentation/navigation/RootAutorizado.kt`.
- `presentation/admin/AdminRootScreen.kt`, `presentation/admin/usuarios/AdminUsuariosScreen.kt`.
- `presentation/admin/finanzas/FinanzasScreen.kt`, `presentation/admin/finanzas/FinanzasViewModel.kt` (nuevos).
- `presentation/vendedor/DisponibilidadWebControl.kt` (nuevo), `presentation/vendedor/VendedorRootScreen.kt`, `presentation/vendedor/dashboard/VendedorDashboardScreen.kt`.
- Prueba nueva: `shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/FinanzasAndroidTest.kt`.

Los cambios previos ajenos que ya aparecían en Git se dejaron intactos. `git diff --check` señala espacios finales preexistentes en vistas de filtros ajenas a esta intervención; no se reformatearon.
