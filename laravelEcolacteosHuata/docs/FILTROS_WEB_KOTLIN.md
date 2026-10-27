# Filtros de listados Web y Kotlin

Implementación sobre los componentes existentes, sin nuevas dependencias, migraciones, cambios en contratos de API, sincronización, permisos o datos comerciales. No se hicieron commits, pushes, instalaciones de APK ni reinicios de servicios.

## Cobertura y criterios

Todos los criterios nuevos se combinan con la búsqueda textual. Los campos de edición y las acciones conservan sus identificadores originales. `Filtrar` abre controles compactos, indica cantidad/selección activa y ofrece limpiar. En los listados paginados Laravel los criterios se aplican antes de `paginate` y reinician la página. Los límites de 20/30 registros existentes se conservan y los filtros se aplican antes de esos límites.

| Función | Web | Kotlin | Criterios incorporados o conservados |
|---|---|---|---|
| Usuarios | Sí | Sí | Nombre, apellido, usuario, documento; rol dinámico; activo/inactivo; varios roles/sin roles |
| Asignar roles a usuarios | Sí | Sí | Mismos criterios y catálogo; Asignar opera sobre el ID seleccionado |
| Roles y funciones | Sí | Sí | Nombre/código, activo/inactivo y pantalla asignada |
| Distribuciones, dentro del gestor actual | Sí | Sí | Nombre, base/personalizada y rol incluido; no modifica la plantilla |
| Descuentos | Sí | Sí | Nombre y activo/inactivo |
| Clientes de ventas | Sí | Sí | Búsqueda actual y con/sin compras registradas |
| Productos de Producción | Sí | Sí, componente compartido | Búsqueda por producto/receta; disponibilidad; fechas |
| Productos del Vendedor | Sí | Sí, componente compartido | Búsqueda actual; con stock, cantidad baja hasta 5, agotado; fechas |
| Inventario del Vendedor | Sí | Sí | Búsqueda, disponibilidad y fechas |
| Registrar venta, catálogo de productos | Sí | Sí | Búsqueda y disponibilidad. En Web también fecha; el modelo `Producto` nativo no expone fecha |
| Historial de ventas | Sí | Sí | Boleta/cliente, fecha y método existentes; vendedor añadido. El vendedor se incorpora al StateFlow nativo, conservando acciones sobre resultados correctos |
| Pedidos web | Sí | No existe equivalente nativo | Comprador/celular, estado de pedido existente, pago real, vendedor y fechas |
| Finanzas | Sí | No existe equivalente nativo | Conserva fecha/vendedor/receptor/tipo; agrega búsqueda por origen/referencia y conciliación |
| Rutas | Sí | Sí | Nombre, activa/inactiva y acopiador; no modifica el mapa ni sus selecciones |
| Recojo de acopiador | Sí | Sí | Proveedor y con/sin recojo en la ronda actual; conserva el alcance del acopiador |
| Inventario de acopio | Sí | Sí, alcance nativo existente | Proveedor y pago. Web conserva período y permite estado de entrega; Android conserva solo recojos pendientes descargados y agrega fechas |
| Recepción en planta | Sí | Sí | Acopiador y entrega a planta. Web conserva su fecha seleccionada; Android trabaja sobre los grupos del día que ya carga su ViewModel |
| Laboratorio de planta | Sí | Sí | Proveedor, conforme/no conforme y fechas; no altera el formulario de análisis |
| Pagos a proveedores | Sí | Sí | Proveedor y con/sin importe pendiente; conserva período y acciones de pago |
| Laboratorio de proveedor | Sí | Sí | Resultado/observación y conformidad, dentro del período y propietario existentes |
| Entregas de proveedor | Sí | Sí | Observación, pago y recepción, dentro del período y propietario existentes |
| Comunicados de planta | Sí | Sí | Título/descripción y tipo real |
| Comunicados de proveedor | Sí | Sí | Título/descripción, tipo existente y fechas |
| Registros de producción | Sí | Sí | Producto/receta, pendiente/publicado y fechas |
| Insumos de producción | Sí | Sí | Nombre y unidad. Web también distingue leche/otros; el modelo Kotlin no expone `es_leche` y no se inventa a partir del nombre |
| Inventario de insumos | Sí | Sí | Nombre, unidad y stock; reutiliza los umbrales y controles existentes. Web también permite leche/otros |
| Tomar asistencia | Sí | Sí | Búsqueda existente más estado guardado, incluido sin registro. El filtro no modifica las marcas ni el estado de edición |

Hay **27 páginas Web intervenidas**, además del panel existente de distribuciones. En Kotlin hay **24 componentes de pantalla intervenidos**, más la hoja de distribuciones. Productos comparte componente entre Producción y Vendedor.

## Alcance de lectura y modo offline

- Laravel conserva las condiciones previas de propietario, rol, fecha y pertenencia. Los `orWhere` textuales nuevos están agrupados para no eludir esos límites.
- Los filtros de roles consultan `rolesDinamicos`; no se utiliza exclusivamente el enum histórico. Una asignación múltiple coincide si contiene el rol solicitado. Sin roles significa ausencia real de asociaciones dinámicas.
- Android consume las colecciones locales que ya entregan los repositorios/ViewModels. Filtrar no llama a sincronización ni descarga información adicional. Las listas mantienen las mismas instancias/IDs y las operaciones pendientes no se eliminan.
- El catálogo de roles de Android combina el catálogo autorizado conocido con las asignaciones locales. Los roles nuevos mantienen su ID flexible; no se convierten a enums históricos.
- Las opciones nativas reflejan los datos descargados. No se promete encontrar registros de fechas que el ViewModel original no conserva localmente.
- Los KPI que pertenecen al período completo mantienen ese alcance. Finanzas conserva separadas posición actual y actividad filtrada. Los filtros no cambian el cálculo financiero ni el stock.
- Limpiar restablece texto y criterios adicionales. Se conservan los períodos obligatorios propios de cada módulo; historial de ventas vuelve a su rango inicial de 30 días. Finanzas limpia también sus filtros originales opcionales; pedidos vuelve al segmento Todos.

## Componentes reutilizados

Web: Flux input/select/button, tablas y modales existentes, Livewire y Eloquent. Se añade un componente visual pequeño `x-ui.filtros` y el trait de estado `FiltrosListado`, sin motor de consultas genérico.

Kotlin: AppTextField, AppDropdown, Material3, remember, StateFlow, listas, tarjetas y modelos actuales. Se añade `core/ui/FiltrosListado.kt` y funciones puras de filtrado para usuarios, stock y fechas. No cambia la base SQLDelight ni las consultas de sincronización.

## Pantallas que deliberadamente se conservan

- Dashboards/paneles: sus resúmenes y listas recientes pequeñas no se convierten en nuevos historiales.
- Empresa y Configuración de pagos: son formularios/configuración; no se añaden buscadores artificiales.
- Turnos/jornadas, unidades y precios: catálogos pequeños o formularios; se mantienen sus controles actuales.
- Compras: ya tiene fecha/período e insumo y paginación Web; se conserva su filtrado existente.
- Mi asistencia: calendario personal mensual con su navegación existente.
- Entrega del acopiador: no se filtra el lote de entrega de forma que pueda ocultar registros incluidos en una acción colectiva.
- Mapas, detalles de un usuario/proveedor/acopiador y formularios: conservan sus selectores y búsquedas previas; no se agrega una administración adicional.
- Finanzas y Pedidos web continúan exclusivamente Web. No se crean imitaciones Android.

## Verificación

Pruebas Web aisladas: `DB_CONNECTION=sqlite DB_DATABASE=:memory: DB_URL='' php vendor/bin/pest tests/Feature/FiltrosListadosTest.php`. Cubren render, búsqueda, rol dinámico/múltiple/sin rol, activo/inactivo, combinación, limpiar, vacío, paginación, IDs de edición/asignación, stock sin mutación, consultas de los demás módulos, permisos y distribuciones sin aplicar cambios.

Pruebas Kotlin focalizadas: `./gradlew :shared:testAndroidHostTest --tests '*FiltrosLocalesTest*' --offline`. Cubren filtrado puro sin Internet, roles nuevos, combinaciones, vacío, identidad de objetos/datos locales, stock y fechas. No se ejecuta la suite general.

Compilación: `./gradlew :androidApp:assembleDebug --offline --no-daemon`. APK: `EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk`. No se instaló en dispositivos.

Assets Web: `npm run build`.

Diseño: comparación con las copias anteriores confirma que se conservan las clases visuales y las acciones `wire:click`/`wire:submit` previas. No se modifican layouts globales, sidebar, fondos, tipografías ni tarjetas existentes. Solo se insertan los controles de filtros y sus mensajes de estado vacío. No se realizó una inspección visual en teléfonos ni se instala la APK para simularla.

## Archivos modificados o añadidos

Rutas desde la raíz de Huata (se conservan los demás cambios locales):

- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/core/ui/FiltrosListado.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/acopiador/inventario/AcopiadorInventarioScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/acopiador/recojo/AcopiadorRecojoScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/AdminRootScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/clientes/AdminClientesScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/descuentos/AdminDescuentosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminAsignarRolesScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/AdminRolesScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/roles/DistribucionesSheet.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/rutas/AdminRutasScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/admin/usuarios/AdminUsuariosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/common/asistencia/TomarAsistenciaScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/common/asistencia/TomarAsistenciaViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/common/filtros/FiltrosLocales.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/planta/comunicados/PlantaComunicadosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/planta/laboratorio/PlantaLaboratorioScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/planta/pagos/PlantaPagosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/planta/recepcion/PlantaRecepcionScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/produccion/insumos/ProduccionInsumosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/produccion/inventario/ProduccionInventarioScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/produccion/productos/ProduccionProductosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/produccion/recetas/ProduccionRecetasScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/produccion/registros/ProduccionRegistrosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/proveedor/comunicados/ProveedorComunicadosScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/proveedor/entregas/ProveedorEntregasScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/proveedor/laboratorio/ProveedorLaboratorioScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/vendedor/VendedorRootScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/vendedor/historial/VendedorHistorialScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/vendedor/historial/VendedorHistorialViewModel.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/vendedor/inventario/VendedorInventarioScreen.kt`
- `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/presentation/vendedor/venta/VendedorVentaScreen.kt`
- `EcolacteosHuata/shared/src/commonTest/kotlin/com/example/ecolacteoshuata/presentation/common/filtros/FiltrosLocalesTest.kt`
- `laravelEcolacteosHuata/app/Livewire/Concerns/FiltrosListado.php`
- `laravelEcolacteosHuata/app/Livewire/DistribucionesRoles.php`
- `laravelEcolacteosHuata/docs/FILTROS_WEB_KOTLIN.md`
- `laravelEcolacteosHuata/resources/views/components/ui/filtros.blade.php`
- `laravelEcolacteosHuata/resources/views/livewire/distribuciones-roles.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/acopiador/⚡inventario.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/acopiador/⚡recojo.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡asignar-roles.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡descuentos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡finanzas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡roles.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/admin/⚡usuarios.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/asistencia/⚡tomar.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡comunicados.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡entregas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡laboratorio.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡pagos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/planta/⚡rutas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡insumos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡inventario.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡productos.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡recetas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/produccion/⚡registros.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/proveedor/⚡comunicados.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/proveedor/⚡entregas.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/proveedor/⚡laboratorio.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡clientes.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡historial.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡inventario.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡pedidos-web.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡produccion.blade.php`
- `laravelEcolacteosHuata/resources/views/pages/vendedor/⚡ventas.blade.php`
- `laravelEcolacteosHuata/tests/Feature/FiltrosListadosTest.php`

## Resultado de la entrega

- 34 pruebas Web aprobadas, 141 aserciones, exclusivamente sobre SQLite `:memory:`.
- 4 pruebas Kotlin aprobadas, cero fallos/errores/omitidas, ejecutadas mediante el filtro `*FiltrosLocalesTest*`.
- Assets Web compilados; la advertencia de tamaño del bundle 3D existente no impide la compilación y no se modifica ese módulo.
- Clases visuales y acciones Web anteriores conservadas en las 28 vistas intervenidas; layouts globales y sidebar intactos. Se conserva también el espaciado anterior del buscador del historial nativo.
- No se ejecutaron migraciones, seeders ni operaciones de preparación sobre la base operativa. No se restauraron bases ni se sobrescribió actividad concurrente de la aplicación.
- Compilación APK de depuración entregada para actualización manual; sin instalación ni borrado de datos.
