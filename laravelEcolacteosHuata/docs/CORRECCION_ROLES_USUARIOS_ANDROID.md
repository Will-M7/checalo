# Corrección focalizada: usuarios y administración nativa de roles

Fecha: 26/10/2026. Implementación sobre el sistema dinámico existente. No se creó otro modelo de autorización ni otra base de roles.

## Causas corregidas

1. **Usuarios web:** `⚡usuarios.blade.php` seguía usando `Rol::options()`, un campo singular y `Rol::from()` al guardar. Los códigos nuevos no podían seleccionarse ni almacenarse por esa vía. La edición histórica tampoco utilizaba el flujo central de asignación.
2. **Administración Android ausente:** `admin.roles` tenía correspondencia Android nula. Faltaban `Screen.AdminRoles`, su formulario Compose y endpoints administrativos para consultar/crear/editar definiciones de roles. La APK solo disponía de asignación a usuarios.
3. **Sustituto histórico en cuentas nuevas:** `AuthMapper` y la descarga de usuarios sustituían una categoría inexistente por `VENDEDOR`. Aunque los accesos ya usaban el snapshot, el modelo seguía representando una categoría inventada. El dominio admite ahora ausencia de categoría histórica y conserva el identificador dinámico íntegro en las asignaciones.

## Usuarios y asignaciones

Se comprobó en lectura que el rol real llamado `ventas` está activo y tiene código `001`; no se alteró su definición ni sus asociaciones.

El formulario web utiliza el catálogo de `Autorizacion::catalogoRoles`, compartido con «Asignar rol a usuarios» y el snapshot móvil. Muestra roles activos delegables; al editar incluye los ya asociados aunque estén inactivos para conservarlos o retirarlos. Se reutilizan los controles Flux, el modal y su distribución. La selección admite varios roles.

`GuardarUsuarioConRoles` es el flujo común de persistencia de la ficha web y móvil. Valida los mismos campos de identidad, contraseña y finca; conserva Teams y asigna mediante `Autorizacion::asignar`, dentro de una transacción. Crear una cuenta solo con roles propios deja `users.rol = null` y no inventa una categoría. Si se eligen roles históricos, el servicio central mantiene su proyección y perfiles de acopiador/proveedor. Los perfiles preexistentes no se eliminan.

Modificar una ficha requiere `admin.usuarios`; crear/asignar/retirar sus roles requiere además `admin.asignar-roles`. Una edición sin cambios de asignación no requiere conceder esa segunda función. Se conservan el alcance de delegación, las protecciones del administrador principal y los controles sobre cuentas superiores. Un fallo de asignación revierte también la creación o modificación de la ficha y su pertenencia.

El formulario Android también utiliza los códigos dinámicos y la API específica para guardar usuarios con sus roles. Estas operaciones administrativas requieren conexión. Las operaciones de negocio y la cola offline existentes continúan disponibles bajo su última autorización conocida.

## Pantalla nativa

`AdminRolesScreen` y `AdminRolesViewModel` permiten consultar, crear y editar roles, cambiar su estado y marcar/desmarcar las pantallas del catálogo. Se reutilizan `TarjetaLista`, `AppBottomSheet`, `AppTextField`, `CasillaTexto`, `BotonesFormulario`, colores y estilos existentes. No se añadieron WebViews.

El acceso usa el mismo identificador `admin.roles` en Laravel y `Screen.AdminRoles` en KMP. El catálogo mantiene 41 funciones, ahora con **39 correspondencias nativas y 2 exclusivamente web**: Pedidos web y Configuración de pagos. El acceso a «Asignar roles a usuarios» abre su pantalla nativa existente, sin duplicar el formulario.

Laravel sigue siendo la autoridad. La pantalla móvil no cambia pivotes, permisos o roles mediante SQL local ni mediante `POST /mobile/cambios`. Una concesión solo se refleja en la caché después de la respuesta válida del servidor. Los errores de validación del backend se muestran en el formulario. Las definiciones protegidas y los límites de delegación se validan en el servicio existente, también cuando la solicitud proviene de una APK manipulada.

## Endpoints

Todos usan `AuthenticateMobileToken` y las comprobaciones del servicio central.

| Método y ruta | Uso y autorización |
|---|---|
| `GET /api/mobile/autorizaciones` | Snapshot propio y catálogo de roles delegables; utilizado también por los selectores móviles. |
| `GET /api/mobile/autorizaciones/roles` | Catálogo administrativo de roles/pantallas; exige `admin.roles`. |
| `POST /api/mobile/autorizaciones/roles` | Crear definición; exige `admin.roles` y el alcance de delegación existente. |
| `PUT /api/mobile/autorizaciones/roles/{id}` | Editar/activar/desactivar; mismo servicio, código estable y administrador protegido. |
| `POST /api/mobile/autorizaciones/asignar` | Asignación existente; exige `admin.asignar-roles` y controles contextuales sobre el destinatario. |
| `POST /api/mobile/autorizaciones/usuarios` | Crear ficha y asignaciones mediante el flujo común. |
| `PUT /api/mobile/autorizaciones/usuarios/{id}` | Editar ficha; requiere permiso de asignación si cambia sus roles. |

El catálogo administrativo devuelve `roles` (`registro_id`, `codigo`, `nombre`, `activo`, `protegido`, `pantallas`), `pantallas` (`id`, `nombre`, `area`, `android`, `delegable`) y el snapshot del actor. Las mutaciones de rol usan `nombre`, `codigo`, `activo` y `pantallas`. No aceptan código ejecutable ni rutas nuevas definidas por el cliente.

## Aceptación automatizada

**Laravel: 428 pruebas aprobadas y 1767 aserciones.** La suite se ejecutó con `APP_ENV=testing`, `DB_CONNECTION=sqlite`, `DB_DATABASE=:memory:` y `DB_URL` vacío. La prueba nueva comprueba explícitamente que está conectada a `:memory:` antes de escribir.

**Kotlin: 56 pruebas aprobadas**, con SQLite JDBC real y el cliente Ktor/OkHttp de producción. Las pruebas administrativas HTTP usan un servidor efímero de prueba con los contratos JSON exportados por las pruebas reales del kernel Laravel; no se presentan como una prueba en un teléfono ni como una conexión de la APK instalada contra el servicio manual. Ambos lados comprueban el mismo cuerpo de modificación de `supervisor_prueba`.

| Caso | Evidencia |
|---|---|
| Crear `supervisor_prueba` con Ventas/Clientes y Producción/Unidades | Formulario Livewire de roles, luego formulario Nuevo usuario. |
| Catálogo disponible, varios roles y edición | Formulario Usuarios y pantalla Asignar comparten catálogo; se prueban agregación/retirada e inactivos. |
| Cuenta sin categoría inventada | Laravel devuelve `rol: null` y categorías vacías; KMP conserva `supervisor_prueba`, persiste ausencia de categoría y valida las credenciales offline. |
| Utilizar ambas funciones web | Guardado real de cliente y unidad en la base aislada. |
| Correspondencias y operaciones Android | `Screen.VendedorClientes` y `Screen.ProduccionUnidades`; repositorios originales escriben y consultan SQLite. |
| Administración desde cliente nativo | Ktor consulta/crea/modifica por HTTP autenticado, realiza asignación y envía usuarios con códigos abiertos; no utiliza `/cambios` para privilegios. |
| Retirar Producción, conservar Clientes | API Laravel actualiza el rol; snapshot nuevo, 403 de la pantalla retirada y rechazo de su escritura móvil. Clientes continúa funcionando. |
| Trabajo offline | `RealSyncService` aplica revocación antes de subir, conserva el documento rechazado en `cambio_revision` y permite la función restante. |
| Red ausente o rechazo del backend | No se conceden roles ni se pierden caché o pendientes. |
| Protección administrativa | Denegación de autoasignación, rol protegido no desactivable, funciones inventadas rechazadas, sin acceso a administración desde el rol de prueba. |
| Histórico | Suite existente de los seis roles, equipos, perfiles y módulos completa aprobada. |

La nueva prueba PHP es `IntegracionRolesUsuariosMovilTest.php`; la nueva prueba host es `IntegracionRolesAdministrativosTest.kt`. Los contratos de prueba se incluyen también como recursos para no depender de archivos temporales en otro entorno.

## Compilación y uso

Se compiló con `./gradlew --offline :androidApp:assembleDebug :shared:testAndroidHostTest`, usando dependencias ya disponibles. No se instaló la APK en ningún dispositivo.

APK: `/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

SHA-256: `3d87916a2731b1df527cd71c960b7639b3a58b8c3e514f010682f5ce1c7e0310`.

La aplicación instalada debe actualizarse manualmente para mostrar la pantalla nueva. La comprobación visual y operativa en los celulares corresponde a esa actualización posterior; no se declara realizada. Los roles personales de acopio/proveedor siguen necesitando sus perfiles y datos de negocio; asignar una pantalla no inventa una finca, ruta ni datos de operación. No se promete revocación mientras un dispositivo siga desconectado.

## Conservación y observaciones

No se ejecutaron migraciones en la base real. No se modificaron IP, puertos, configuración de conexión, Payment Core, Yape Bridge, pagos, infraestructura ni diseños globales. No se instalaron aplicaciones, no se limpiaron bases y no se hizo commit/push.

Los procesos manuales de Laravel web, scheduler y Core se mantuvieron activos, sin detenerlos ni reiniciarlos. La comparación de huellas de tablas reales conserva los datos de negocio, usuarios, equipos y autorización. `mobile_tokens` registra actividad del servicio manual (`last_used_at`, actualizado por `AuthenticateMobileToken`); no se restauró ni alteró esa actividad normal de los dispositivos.

Observaciones ajenas conservadas sin corregir: el empaquetador D8 emite advertencias previas sobre metadatos Kotlin; la compilación finaliza correctamente. Una prueba previa de Usuarios depende de la función global `usuarioAdmin` definida en `AdminAccesoModulosTest.php`; para ejecutar ese grupo focalizado se incluyó ese archivo, sin cambiar su dependencia.

## Archivos de esta corrección

- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/mapper/AuthMapper.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/mapper/UsuarioMapper.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/datasource/SyncRemoteDataSource.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/repository/UsuarioRepositoryImpl.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/sync/SyncService.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/di/AppModules.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/di/DomainModule.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/di/ViewModelModule.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/model/Usuario.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/model/UsuarioRemoto.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/AuthUseCases.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/CrudUsuarioUseCases.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/GetReporteUseCase.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/AdminRootScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminAsignarRolesViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/usuarios/AdminUsuarioFormScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/usuarios/AdminUsuarioFormViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/usuarios/AdminUsuariosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/navigation/RootAutorizado.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/navigation/Screen.kt`
- `laravelEcolacteosHuata/app/Authorization/Autorizacion.php`
- `laravelEcolacteosHuata/app/Authorization/Pantallas.php`
- `laravelEcolacteosHuata/app/Http/Controllers/Api/MobileAutorizacionController.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡asignar-roles.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡usuarios.blade.php`
- `laravelEcolacteosHuata/routes/api.php`
- `laravelEcolacteosHuata/tests/Feature/AdminUsuariosProveedorAcopiadorTest.php`
- `EcolacteosHuata/shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/IntegracionRolesAdministrativosTest.kt`
- `EcolacteosHuata/shared/src/androidHostTest/resources/huata-supervisor-catalogo.json`
- `EcolacteosHuata/shared/src/androidHostTest/resources/huata-supervisor-login.json`
- `EcolacteosHuata/shared/src/androidHostTest/resources/huata-supervisor-modificar.json`
- `EcolacteosHuata/shared/src/androidHostTest/resources/huata-supervisor-revocado.json`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/data/remote/datasource/AdministracionRolesRemota.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/model/AdministracionRoles.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/domain/usecase/AdministrarRolesUseCase.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminRolesScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminRolesViewModel.kt`
- `laravelEcolacteosHuata/app/Actions/GuardarUsuarioConRoles.php`
- `laravelEcolacteosHuata/tests/Feature/IntegracionRolesUsuariosMovilTest.php`

También se actualiza `docs/ROLES_DINAMICOS.md` para retirar la indicación obsoleta de que `admin.roles` existe solo en web. Este documento contiene el inventario de cambios de la corrección, sin atribuirle los cambios locales de integraciones anteriores.
