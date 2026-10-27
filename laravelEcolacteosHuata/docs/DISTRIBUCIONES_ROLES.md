# Distribuciones de roles — implementación entregada el 27/10/2026

Se incorporan plantillas de asignaciones al gestor existente. Laravel conserva la autoridad central. No hay otra autorización ni nuevas pantallas operativas. Web usa un modal Flux en Roles y funciones; Android usa un AppBottomSheet nativo en AdminRoles con los componentes y tema existentes.

## Estado operativo verificado

- Dos bases protegidas, versión 1: **Inicial** (predeterminada para instalaciones nuevas) y **En desarrollo** (referencia histórica).
- **Ninguna distribución fue aplicada a la base operativa.** El historial de aplicaciones permanece vacío. Las asignaciones activas de los seis roles no fueron sustituidas: Administrador 32, Personal de planta 7, Acopiador 6, Proveedor 5, Vendedor 9, Producción 9.
- Rol `001` (ventas) eliminado mediante el servicio transaccional, después de seleccionar explícitamente Administrador para `will`, ID 15, conforme a la respuesta del usuario. Pasó de su única pantalla `vendedor.ventas` a las 32 del Administrador operativo; no se convirtió en Vendedor ni se eliminó la cuenta. No cambia quién es el administrador principal protegido.
- Rol auxiliar `tomar_asistencia` eliminado: no tenía usuarios asociados, flags legados activos ni referencias de plantilla obligatorias. `asistencia.tomar` permanece en el catálogo y en Administrador. Se impide recrear ese identificador auxiliar; se permiten otros roles personalizados y asignarles la pantalla existente.
- Se conservaron los 13 usuarios y sus registros. Frente al respaldo, solo cambió la proyección autorizada de roles de ID 15 (`rol`, `roles`, `updated_at`). Contraseñas, identidad, Teams, perfiles y todas las demás tablas comerciales permanecieron iguales. Los seis roles existentes conservan exactamente sus nombres, estado y listas de pantallas anteriores.
- Solo se añadieron `authorization_distributions` y `authorization_distribution_runs`. También se registraron la migración y las acciones de autorización en las estructuras ya existentes. No se añadieron tablas comerciales ni columnas de usuarios.
- No se modificaron Payment Core, Yape Bridge, puertos, IP, servicios, estilos globales, pantallas comerciales ni bases de teléfonos. La APK no se instaló.

## Inicial: asignaciones exactas aprobadas

50 asociaciones cubren las 41 pantallas distintas. Se conservan 39 correspondencias nativas y las dos exclusivas web. La lista siguiente procede del recurso versionado que consume la inicialización:

### Administrador — 15

- `admin.panel`
- `admin.usuarios`
- `admin.asignar-roles`
- `admin.roles`
- `admin.descuentos`
- `admin.precios`
- `admin.turnos`
- `admin.empresa`
- `planta.rutas`
- `planta.pagos`
- `planta.comunicados`
- `vendedor.pedidos-web`
- `vendedor.configuracion-pagos`
- `asistencia.mis`
- `asistencia.tomar`

### Personal de planta — 7

- `planta.rutas`
- `planta.comunicados`
- `planta.panel`
- `planta.laboratorio`
- `planta.entregas`
- `planta.pagos`
- `asistencia.mis`

### Acopiador — 6

- `acopiador.panel`
- `acopiador.recojo`
- `acopiador.mapa-recojo`
- `acopiador.entrega`
- `acopiador.inventario`
- `asistencia.mis`

### Proveedor — 4

- `proveedor.panel`
- `proveedor.entregas`
- `proveedor.laboratorio`
- `proveedor.comunicados`

### Vendedor — 9

- `vendedor.configuracion-pagos`
- `vendedor.pedidos-web`
- `vendedor.panel`
- `vendedor.ventas`
- `vendedor.produccion`
- `vendedor.historial`
- `vendedor.clientes`
- `vendedor.inventario`
- `asistencia.mis`

### Producción — 9

- `produccion.panel`
- `produccion.registros`
- `produccion.recetas`
- `produccion.insumos`
- `produccion.unidades`
- `produccion.productos`
- `produccion.compras`
- `produccion.inventario`
- `asistencia.mis`

## En desarrollo: procedencia y límites

La primera entrada `clone:` del reflog `.git/logs/HEAD` apunta a `0fd6a98e3010187a041baa1ce60cf79333f582d4`, igual a HEAD y origin/web-laravel-huata al inspeccionar. Se leyó el contenido de las rutas en esa revisión con `git show`, no la base operativa ni las rutas locales modificadas. También se comprobó el middleware histórico EnsureRole.

La plantilla reconstruye las asignaciones rol-ruta web: Administrador 31 (30 históricas + `admin.roles` indispensable para recuperación), Personal de planta 7, Acopiador 6, Proveedor 5, Vendedor 8, Producción 9. Cubre 40 pantallas actuales. No se inventa una concesión histórica para Configuración de pagos, inexistente en la revisión original. La vista previa advierte qué pantallas quedarían sin roles activos.

No se reconstruyen excepciones históricas individuales de asistencia sin evidencia de datos de aquella instalación, ni se reproduce la ausencia de controles móviles antiguos. La asignación común autenticada `asistencia.mis` se representa en los seis roles. `asistencia.tomar` permanece en Administrador; no se crea un rol auxiliar. La plantilla es una referencia de asignaciones, no una restauración de código, interfaces, base de datos ni vulnerabilidades antiguas.

Los seis archivos de rutas originales y sus huellas SHA-256 se conservan en el recurso `en-desarrollo.json`:

- `laravelEcolacteosHuata/routes/admin.php` — `d914db6467305b8cc21dbcd64c92835a7f2512e1a8a37c2f023fcb5f8e22cfa2`
- `laravelEcolacteosHuata/routes/planta.php` — `d3374b595baa765038d5016e9f8d51523d0832cacd5b1b418d73ca16b435bbb1`
- `laravelEcolacteosHuata/routes/acopiador.php` — `bf87aafe5d9cf6806bd26b5d92576909a2557af12ca3ed46ec89e34cd57f1967`
- `laravelEcolacteosHuata/routes/proveedor.php` — `46a365a9114cc641cd661ae05c96d71b12c64b35987eb0a6b82a4649ae1a5e24`
- `laravelEcolacteosHuata/routes/vendedor.php` — `35bee696dda46950cbbe9e5565cc3a8688cdccea3047b7e40c4a65e629c378c8`
- `laravelEcolacteosHuata/routes/produccion.php` — `6421ef47abf38b2aa2e028ed47d5faa55b22d0b523513f9321faeaf3d7c32ff8`

## Persistencia y aplicación segura

`authorization_distributions`: identidad/código, nombre, tipo, versión, predeterminada, asignaciones con ID y código del rol existente, procedencia y timestamps. Los IDs impiden aplicar silenciosamente una plantilla a un rol eliminado y recreado con el mismo código.

`authorization_distribution_runs`: UUID, plantilla/versión, actor, tipo aplicación/recuperación, instantáneas completas de asignaciones anteriores y posteriores, huella posterior, referencia única a la aplicación recuperada y fecha. No duplica entidades ni guarda código ejecutable.

El botón Distribuciones permite consultar, crear, editar, duplicar y guardar las asignaciones actuales con otro nombre. Las bases son de solo lectura y se personalizan mediante copias. No hay obligación de guardar plantillas para editar los roles activos normalmente.

La vista previa calcula en Laravel roles actuales/destino, altas, bajas, usuarios cuyos accesos efectivos cambiarían (considera múltiples roles), roles no afectados, referencias inexistentes y advertencias. Usa la versión **guardada**, no ediciones pendientes del formulario. No escribe datos. Requiere confirmación explícita y token cifrado autenticado ligado al actor, plantilla/versión, estado observado y expiración de diez minutos.

Confirmar vuelve a verificar permisos, token, cambios concurrentes y catálogo dentro de la transacción serializada de autorización. Solo modifica las diferencias de pantallas en los roles incluidos. No crea roles, activa roles inactivos ni asigna usuarios al aplicar una distribución. Los roles ajenos y sus membresías permanecen intactos. Se reutiliza Autorizacion::guardarRol y su techo de delegación; el rol protegido solo lo modifica el administrador principal. Se conservan las tres pantallas críticas y no se permite retirar la administración crítica de la propia sesión.

La revisión central forma parte de la versión descargada por Android; no es necesario tocar los registros de usuarios para propagar una aplicación. El gestor de roles normal conserva su comportamiento de actualización habitual.

La recuperación requiere otra vista previa y confirmación. La huella de roles, membresías, revisión y usuarios debe coincidir con la posterior a la aplicación original. Cambios posteriores o una recuperación previa bloquean la operación; el gestor muestra el conflicto. No se sobreescribe ese estado ni se restaura ciegamente una base. Una aplicación sin diferencias no genera un cambio de autorización ni una entrada de aplicación falsa.

El historial del modal muestra las últimas 30 operaciones; todas las instantáneas se conservan en la tabla. No se creó un dashboard de auditoría ni nuevas capacidades.

## Instalaciones nuevas e idempotencia

1. La migración crea únicamente las dos estructuras de distribuciones; las estructuras de autorización previas siguen a cargo de la migración existente.
2. `registrarBases()` registra las bases una sola vez, enlazando los seis roles existentes. Solo crea roles base faltantes cuando no existe ningún usuario en una instalación nueva.
3. Inicial se aplica automáticamente **solo** al registrar por primera vez su plantilla y si no hay usuarios. No se ejecutan seeders ni se crean cuentas de demostración.
4. En una instalación existente solo se guardan las plantillas. Ejecutar de nuevo `php artisan huata:inicializar-distribuciones` no reemplaza asignaciones o plantillas ya existentes ni recrea roles eliminados.

No ejecutar seeders de demostración ni restaurar bases para usar este administrador. La migración incremental ya se ejecutó aquí; no se aplicó Inicial. El rollback SQL destructivo está bloqueado; la recuperación de asignaciones usa el flujo controlado del gestor.

## API y Android

API autenticada existente de personal, con autorización del servidor:

| Método y ruta | Uso |
|---|---|
| GET `/api/mobile/autorizaciones/distribuciones` | Catálogo, correspondencias, estado, usuarios pendientes de 001, historial y autorización actual |
| POST `/api/mobile/autorizaciones/distribuciones` | `modo=guardar` para crear/editar con versión, o `modo=copiar` y origen opcional para duplicar/base actual; devuelve `guardada_id` |
| POST `/api/mobile/autorizaciones/distribuciones/previsualizar` | `operacion=aplicar`, `recuperar` o `retirar-001`, con la selección explícita correspondiente |
| POST `/api/mobile/autorizaciones/distribuciones/confirmar` | Token previo y `confirmar=true`; nunca se aplica al seleccionar |

Android incorpora modelos de transporte, métodos del datasource y del caso de uso administrativos existentes, ViewModel de formulario y panel nativo dentro de Roles y funciones. No reproduce la lógica de diferencias, validación o aplicación. No crea otra configuración local de roles. La administración requiere conexión/token; un error de red, validación, autorización o conflicto conserva el estado local y se muestra al usuario.

No se modificaron el esquema SQLDelight, las pantallas operativas ni las 39 correspondencias. La última autorización conocida conserva la política offline existente. No se promete revocación instantánea sin conexión; al reconectar se actualiza desde Laravel, y los trabajos rechazados siguen disponibles para revisión según el flujo actual. El interruptor del rol auxiliar desaparece de Asignar roles; la pantalla de asistencia se asigna desde el catálogo central. La API genérica de sincronización rechaza concederla mediante un flag legado y no recrea el auxiliar.

## Archivos de esta entrega

Laravel:

- [app/Authorization/Distribuciones.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Authorization/Distribuciones.php)
- [app/Authorization/Autorizacion.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Authorization/Autorizacion.php)
- [app/Models/DistribucionRoles.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Models/DistribucionRoles.php)
- [app/Livewire/DistribucionesRoles.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Livewire/DistribucionesRoles.php)
- [resources/views/livewire/distribuciones-roles.blade.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/resources/views/livewire/distribuciones-roles.blade.php)
- [resources/views/pages/admin/⚡roles.blade.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/resources/views/pages/admin/⚡roles.blade.php)
- [resources/views/pages/admin/⚡asignar-roles.blade.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/resources/views/pages/admin/⚡asignar-roles.blade.php)
- [app/Http/Controllers/Api/MobileDistribucionesController.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Http/Controllers/Api/MobileDistribucionesController.php)
- [app/Http/Controllers/Api/MobileCambiosController.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Http/Controllers/Api/MobileCambiosController.php)
- [routes/api.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/routes/api.php)
- [app/Console/Commands/InicializarDistribuciones.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/app/Console/Commands/InicializarDistribuciones.php)
- [database/migrations/2026_10_27_060000_add_authorization_distributions.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/database/migrations/2026_10_27_060000_add_authorization_distributions.php)
- [resources/authorization/inicial.json](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/resources/authorization/inicial.json)
- [resources/authorization/en-desarrollo.json](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/resources/authorization/en-desarrollo.json)
- [tests/Feature/DistribucionesRolesTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/DistribucionesRolesTest.php)
- [tests/Feature/RoleAccessTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/RoleAccessTest.php)
- [tests/Feature/AdminAccesoModulosTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/AdminAccesoModulosTest.php)
- [tests/Feature/Api/MobileSyncTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/Api/MobileSyncTest.php)
- [tests/Feature/Api/MobileCambiosTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/Api/MobileCambiosTest.php)
- [tests/Feature/AsistenciaTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/AsistenciaTest.php)
- [tests/Feature/AutorizacionDinamicaTest.php](/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/tests/Feature/AutorizacionDinamicaTest.php)

Kotlin Multiplatform:

- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/model/DistribucionesRoles.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/model/DistribucionesRoles.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/datasource/AdministracionRolesRemota.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/datasource/AdministracionRolesRemota.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/datasource/SyncRemoteDataSource.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/datasource/SyncRemoteDataSource.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/AdministrarRolesUseCase.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/AdministrarRolesUseCase.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/CrudUsuarioUseCases.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/CrudUsuarioUseCases.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/DistribucionesViewModel.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/DistribucionesViewModel.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/DistribucionesSheet.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/DistribucionesSheet.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminRolesScreen.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminRolesScreen.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminAsignarRolesScreen.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminAsignarRolesScreen.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminAsignarRolesViewModel.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminAsignarRolesViewModel.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/AdminRootScreen.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/AdminRootScreen.kt)
- [shared/src/commonMain/kotlin/com/example/ecolacteoshuata/di/ViewModelModule.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/di/ViewModelModule.kt)
- [shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/DistribucionesRolesTest.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/DistribucionesRolesTest.kt)
- [shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/IntegracionRolesAdministrativosTest.kt](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/IntegracionRolesAdministrativosTest.kt)
- [shared/src/androidHostTest/resources/huata-distribuciones-catalogo.json](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/androidHostTest/resources/huata-distribuciones-catalogo.json)
- [shared/src/androidHostTest/resources/huata-distribuciones-preview.json](/home/willian/Desarrollo/Huata/EcolacteosHuata/shared/src/androidHostTest/resources/huata-distribuciones-preview.json)

Documento de entrega: `/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/docs/DISTRIBUCIONES_ROLES.md`.

Respaldo operativo anterior a la migración y reasignación: `/home/willian/Desarrollo/Huata/laravelEcolacteosHuata/storage/app/private/antes-distribuciones-20261027T072145Z.sqlite` (privado, modo 0600). No se necesita restaurarlo para utilizar el gestor y no se debe restaurar sobre cambios posteriores sin revisión.

## Validación y límites

- Laravel/Pest: **444 pruebas aprobadas, 1887 aserciones**, SQLite en memoria (`APP_ENV=testing DB_CONNECTION=sqlite DB_DATABASE=:memory: DB_URL= php vendor/bin/pest --compact`). Incluye 16 pruebas específicas de distribuciones y regresión de operaciones/rutas de los seis roles, cuentas, Teams y pagos existentes sin cambiar su lógica.
- KMP: **58 pruebas aprobadas**, 17 suites, cero fallos/errores/omitidas. Incluye catálogo 41/39, las listas Inicial, transporte autenticado de crear/editar/duplicar/preview/aplicar/recuperar, conflicto 409, administración sin conexión bloqueada y conservación de la cola pendiente al recibir una revocación; mantiene la regresión existente de sincronización y pantallas dinámicas.
- `pint --test` de los archivos PHP implicados y `git diff --check`: aprobados.
- `./gradlew --offline :androidApp:assembleDebug :shared:testAndroidHostTest` con Java 21 y SDK local: **BUILD SUCCESSFUL**. No instala aplicaciones ni usa los teléfonos.
- APK: [androidApp-debug.apk](/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk), 73113470 bytes. SHA-256: `9baa022c97c5e90b29860c485879a40212e48823ff3c92f93a80b6da34f4fba1`.
- El compilador conserva advertencias D8 de metadatos Kotlin de la cadena de herramientas existente; no impiden generar la APK. No se actualizaron dependencias ni se modificó el toolchain en esta tarea.
- La verificación de la base real contra el respaldo comprobó dos tablas nuevas, cero aplicaciones de distribución, mismos seis roles y pantallas activas, mismos 13 usuarios, y únicamente la reasignación explícitamente autorizada de ID 15 y el retiro de los dos roles. No se ejecutaron tests contra la base operativa.


La validación automatizada nativa usa contratos generados por el kernel Laravel con SQLite aislado, el cliente KMP real por HTTP local de pruebas y SQLDelight/SQLite real en memoria. No sustituye una prueba visual/manual en los teléfonos. No se instaló ni se borraron datos de ningún dispositivo. La actualización y comprobación física de la APK quedan en manos del usuario, como solicitó. La integración comercial de pagos no se ha declarado operativa ni fue modificada.
