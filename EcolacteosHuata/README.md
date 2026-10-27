# Ecolácteos Huata

Aplicación Kotlin Multiplatform (Compose Multiplatform) para la asociación de lácteos de Huata, Puno — Perú. Replica la funcionalidad del sistema Laravel de la asociación (acopio de leche, producción/laboratorio, ventas e insumos) con seis roles: Administrador, Vendedor, Producción, Personal de planta, Acopiador y Proveedor. Funciona 100 % offline con SQLite (SQLDelight) y corre en Android e iOS desde una única base de código en `shared/`.

## Cómo ejecutar

### Android
```
./gradlew :androidApp:assembleDebug
```
o abre el proyecto en Android Studio y ejecuta la configuración `androidApp` en un emulador/dispositivo (minSdk 24).

### iOS
Abre `iosApp/iosApp.xcodeproj` (o el workspace) en Xcode, en macOS, y ejecuta sobre un simulador o dispositivo. El framework `shared` se compila automáticamente como parte del build de Xcode gracias al plugin de Kotlin Multiplatform.

### Tests
```
./gradlew :shared:testAndroidHostTest      # tests de lógica de negocio (JVM)
./gradlew :shared:iosSimulatorArm64Test    # los mismos tests en simulador iOS (requiere macOS)
```
Cubren: descuentos excluyentes del carrito, cálculo de vuelto, total de recojo, conteo de alteraciones de calidad y bloqueo automático al tercer aviso.

## Usuarios de demostración

Todas las cuentas usan la contraseña **`1234`**.

| Usuario | Rol | Nombre |
|---|---|---|
| `admin` | Administrador | Nilda Huanca Cutipa |
| `vendedor` | Vendedor | Rosa Flores Mamani |
| `produccion` | Producción | Edwin Calcina Turpo |
| `planta` | Personal de planta | Teodoro Cachi Larico |
| `acopio` | Acopiador | Rufino Quispe Mamani |
| `proveedor` | Proveedor | Basilia Yupanqui Chura |

Menús por rol (calcados de las rutas del sistema Laravel — `routes/admin.php`, `vendedor.php`, `produccion.php`, `planta.php`, `acopiador.php`, `proveedor.php`):

- **Administrador**: Panel, Usuarios, Asignar rol a usuarios, Descuentos, Precios de la leche, Rutas, Mapa de rutas, Turnos de rutas y turnos de producción, Empresa, Comunicados y asesoramiento.
- **Vendedor**: Panel, Venta, Productos, Historial de ventas, Clientes, Inventario.
- **Producción**: Panel, Producción, Recetas, Insumos, Unidades de medida, Productos, Compras, Inventario.
- **Personal de planta**: Panel, Laboratorio (foto + OCR en Android), Entregas de acopiadores, Pago a proveedores, Rutas, Mapa de rutas, Comunicados.
- **Acopiador**: Panel, Recojo de ruta, Mapa de recojo, Entrega a planta, Inventario.
- **Proveedor**: Panel, Mis entregas, Laboratorio, Comunicados.

**Mapa de rutas / Mapa de recojo** (`presentation/common/mapa/`): mapa dibujado en Canvas que funciona sin internet (no descarga mosaicos). Planta y administrador ubican fincas tocando el mapa, ordenan paradas, trazan el camino (según paradas o dibujándolo) y lo borran; el acopiador ve sus paradas en verde (recogido) / rojo (pendiente) y puede abrir Google Maps con la navegación a las paradas pendientes. El camino (`ruta.trazo`) y el orden (`ruta_proveedor.orden`) llegan desde Laravel en la sincronización.

**Primera instalación**: si hay un servidor Laravel activo y el teléfono nunca sincronizó, antes del login se muestra la pantalla "Preparando tu app" que descarga todo el sistema con su progreso y un resumen de lo descargado (`PrimeraDescargaViewModel`).

Al primer arranque, si la base de datos está vacía, se siembran automáticamente empresa, precio de leche, usuarios, acopiadores, turnos, proveedores, rutas, productos, clientes, recojos del día, resultados de laboratorio y comunicados de ejemplo (ver `data/seed/SeedData.kt`).

## Estructura de carpetas (`shared/src/commonMain/kotlin/com/example/ecolacteoshuata`)

```
App.kt                  Punto de entrada @Composable — tema + observa la sesión y enruta por rol
di/                      Módulos Koin: databaseModule, dataModule, domainModule, viewModelModule
core/
  theme/                 Colores, tipografía (Sora/Manrope), Theme, Dimens
  ui/                    Componentes reutilizables: SessionHeader, SyncBanner, tarjetas, botones,
                          chips, bottom sheet, bottom nav, animaciones, campos de formulario
  util/                  Dinero, fechas, validadores, generador de IDs, SHA-256 propio (sin deps)
  sync/                  SyncService (interfaz) + FakeSyncService (simula sin señal → sincronizando
                          → sincronizado, listo para conectarse a una API real)
  pdf/                   PdfDocumentModel + PdfExporter (expect/actual: PdfDocument en Android,
                          UIGraphicsPDFRenderer en iOS)
domain/
  model/                 Entidades de dominio (Usuario, Proveedor, Recojo, Venta, Reporte, ...)
  repository/            Interfaces de repositorio (una por agregado)
  usecase/               Un caso de uso por acción de negocio (login, ventas, recojo, laboratorio,
                          pagos, reportes, dashboards, notificaciones, sincronización)
data/
  local/                 DatabaseDriverFactory (expect/actual SQLDelight)
  mapper/                Entidad SQLDelight ⇄ modelo de dominio
  repository/            Implementaciones de los repositorios sobre SQLDelight
  seed/                  Datos de demostración de Huata, Puno
presentation/
  navigation/            Screen (sealed class) — navegación propia por estado, sin dependencias
                          de navegación adicionales (más simple y estable entre Android/iOS)
  login/                 Pantalla de inicio de sesión con accesos demo
  common/                RootScaffold (cabecera + barra de título + contenido + nav inferior),
                          hojas de notificaciones/perfil/módulos, pantalla de reportes compartida
  admin/, planta/, acopiador/, proveedor/
                          Un paquete por rol; cada pantalla = ViewModel + UiState + Screen (Compose)
```

`shared/src/commonMain/sqldelight/.../db/*.sq` contiene el esquema SQLite (17 tablas) y todas las consultas tipadas que SQLDelight convierte en Kotlin.

`androidApp/` y `iosApp/` son cascarones finos: `androidApp` define `EcolacteosApplication` (inicializa Koin con el contexto de Android) y `MainActivity` (llama a `App()`); `iosApp` inicializa Koin una sola vez y arranca `MainViewController` desde Swift.

## Decisiones de arquitectura

- **Clean Architecture + MVVM**: `domain` no depende de nada; `data` implementa los repositorios sobre SQLDelight; `presentation` sólo habla con casos de uso a través de ViewModels (`androidx.lifecycle.ViewModel` multiplataforma), nunca con repositorios directamente.
- **Offline-first real**: cada recojo se guarda localmente de inmediato (`sincronizado = 0`); `SyncService` es una interfaz con una implementación falsa que simula el ciclo de conexión para poder enchufar una API real más adelante sin tocar la UI.
- **Navegación propia**: en vez de `navigation-compose`, cada pantalla raíz por rol mantiene su propia pila simple (`sealed class Screen` + estado local) para evitar la complejidad de argumentos tipados de Navigation Compose multiplataforma; los formularios se presentan como hojas inferiores (bottom sheets) tal como pide el diseño, y sólo las vistas de detalle (ruta de un acopiador, registro de laboratorio) usan una barra de título con botón "atrás".
- **`expect`/`actual`** limitado a tres puntos, tal como se pidió: el driver de SQLDelight, el exportador de PDF y el formateo de moneda (aunque el formateo termina siendo el mismo cálculo puro en ambas plataformas, para evitar que el separador decimal cambie con el locale del dispositivo).
- **Contraseñas**: hash SHA-256 (implementación pura en Kotlin, sin depender de `MessageDigest`/`CryptoKit`) con una sal fija de aplicación — suficiente para una demo offline, no para producción con contraseñas reales de usuarios finales.
- **Reportes**: un único `GetReporteUseCase` parametrizado por `TipoReporte` genera una tabla genérica (`ReporteTabla`) que alimenta tanto la vista previa en pantalla como el PDF exportado, evitando duplicar la lógica de agregación por rol.
- **Fuentes**: `Type.kt` usa la fuente del sistema como *fallback* de Sora/Manrope; para igualar el diseño exacto basta con colocar los archivos `.ttf`/`.otf` en `shared/src/commonMain/composeResources/font/` y registrarlos en `SoraFontFamily`/`ManropeFontFamily`.
- **PDF**: en Android se guarda en el almacenamiento específico de la app bajo `Download/` (no requiere permisos en tiempo de ejecución); en iOS se guarda en el directorio de Documentos de la app.

## Notas

- El fondo de la cabecera (`fondo_huata.jpg`) proviene de `img/fondo de img.jpg` del repositorio.
- Los diseños detallados por pantalla no se compararon pixel a pixel contra un archivo `.dc.html` porque no se adjuntó ninguno a esta conversación; se implementó a partir de la especificación de colores, tipografía, espaciados y animaciones del prompt original.
