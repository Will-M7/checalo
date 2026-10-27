# Prompt para construir desde cero el sistema de Ecolácteos Huata

Copia todo lo que está debajo de la línea y pégalo en tu asistente de código (Claude Code, Cursor, etc.) dentro de una carpeta vacía. Está escrito para reproducir el proyecto tal como está hoy, en el orden en que conviene construirlo.

---

## ROL Y OBJETIVO

Eres un desarrollador senior Laravel y un diseñador UX/UI. Construye un sistema web completo en español para **Ecolácteos Huata**, una asociación de productores lecheros de la comunidad de Huata, Puno (Perú). El sistema da trazabilidad a la leche desde el proveedor y el acopio, pasando por laboratorio y producción, hasta la venta, y paga a los proveedores. Incluye además una **página pública de inicio con carrito de compras** y una **API móvil** para una app nativa. Toda la interfaz, los mensajes, los nombres de clases de dominio y las pruebas van en español (zona horaria `America/Lima`, moneda soles `S/`).

## REGLAS DE TRABAJO

1. Crea archivos con `php artisan make:...` siempre que exista el comando. No crees carpetas base nuevas. No cambies dependencias fuera de las indicadas.
2. Cada funcionalidad lleva pruebas con **Pest**. Al terminar cada bloque corre `vendor/bin/pint --format agent` y las pruebas.
3. Las pruebas usan SQLite en memoria. Si la terminal no tiene el driver, ejecuta `php -d extension=pdo_sqlite -d extension=sqlite3 vendor/bin/pest`.
4. Sigue las convenciones del proyecto: `#[Fillable([...])]` en los modelos, `casts()` como método, `HasFactory` con fábrica, enums con `label()` y `color()`.
5. Las páginas del panel son componentes **Livewire de un solo archivo** (`⚡nombre.blade.php`) dentro de `resources/views/pages/<rol>/`, registradas con `Route::livewire('ruta', 'pages::rol.nombre')`.
6. No inventes datos comerciales (testimonios, cifras, clientes). Usa solo datos reales de la base.

## STACK

- PHP 8.3, **Laravel 13**, **Livewire 4**, **Flux UI** (`livewire/flux`), **Fortify** (login, registro, 2FA, passkeys con `@laravel/passkeys`), `livewire/blaze`.
- **Tailwind 4** con Vite (`laravel-vite-plugin`, `vite-plus`). Las dos hojas de entrada: `resources/css/app.css` (paneles) y `resources/css/landing.css` (página pública).
- `barryvdh/laravel-dompdf` para PDF. Dev: Pest 4, Pint, Larastan, Pail, Boost.
- Base de datos MySQL en desarrollo, SQLite en memoria en pruebas.
- Multi-equipo (Teams) del starter kit de Livewire: las rutas internas van bajo `{current_team}`; existe un equipo principal `ecolacteos-huata` (`Team::principal()`).

## ROLES (enum `App\Enums\Rol`)

`administrador`, `personal_planta`, `acopiador`, `proveedor`, `vendedor`, `produccion`. Cada uno tiene `label()`, `routePrefix()` (admin, planta, acopiador, proveedor, vendedor, produccion), `navegacion()` (ítems del menú lateral con ruta, etiqueta e ícono Heroicon) y, para el administrador, `navegacionAgrupada()`. Un usuario tiene un rol principal (`rol`) y puede tener roles extra (`roles`, JSON). Middleware `EnsureRole` (`role:vendedor,administrador`), `EnsureTeamMembership` y `SetTeamUrlDefaults`. Tras iniciar sesión, la ruta `dashboard` redirige al panel del rol.

Campos extra de `users`: `nombres, apellidos, documento (15), celular (20), rol, roles (json), toma_asistencia (bool), usuario, activo (bool)`. Gate `tomar-asistencia` (administrador o quien tenga `toma_asistencia`).

## MODELO DE DATOS

Tablas y campos principales (crea migraciones, modelos, fábricas):

- `empresa` (fila única, `Empresa::actual()` la crea): `razon_social, ruc, telefono, direccion`. Método `whatsapp()`: solo dígitos; si son 9 se antepone `51`; `null` si no hay teléfono.
- `acopiadores` (`user_id, ruta_nombre`), `proveedores` (`user_id, finca_ubicacion, finca_referencia, latitud, longitud, acopiador_id, bloqueado, alteraciones`).
- `turnos` (`nombre, hora_inicio, hora_fin, es_produccion`), `rutas` (`acopiador_id, nombre, activa, trazo, orden, rango`), pivotes `ruta_proveedor` (proveedor único por ruta) y `ruta_turno`.
- `precios_leche` (`precio_litro, vigente_desde`), `descuentos` (`nombre, porcentaje, activo`).
- `recojos` (`proveedor_id, acopiador_id, turno_id, fecha, litros, estado_leche, estado_otro, precio_litro, total, pagado, metodo_pago, entregado, litros_entregados`).
- `entregas_planta` (`acopiador_id, turno_id, fecha, litros_total, total_pagado, total_deuda, proveedores_no_recogidos, guia`).
- `resultados_laboratorio` (`proveedor_id, recojo_id, turno_id, fecha, resultado, densidad, acidez, temperatura, observaciones, conforme, enviado_produccion, ticket del analizador`, campos de análisis).
- `pagos_proveedor` (`proveedor_id, recojo_id, fecha, monto, metodo_pago, periodo`).
- `comunicados` (`tipo, titulo, descripcion, fecha, autor_id`, roles destinatarios) + notificaciones de base de datos (campanita `notification-bell`).
- Producción: `unidades_medida (nombre, simbolo)`, `insumos (nombre, unidad_medida_id, costo_unitario, stock_actual, es_leche)`, `compras` + `compra_detalles`, `recetas (nombre, precio_venta nullable)` + `receta_detalles (receta_id, insumo_id, cantidad, unidad_medida_id)`, `producciones (fecha, nombre, imagen, receta_id, cantidad_producida, cantidad_vendida, lote, turno_id, responsable_id, estado)` + `produccion_detalles` (consumo de insumos).
- Ventas: `clientes (nombres, apellidos, documento único, compras_count)`, `ventas (numero_boleta, cliente_id, cliente_nombre_libre, cliente_doc_libre, vendedor_id, fecha, subtotal, descuento, descuento_tipo, total, metodo_pago, efectivo_recibido, vuelto)`, `venta_items (venta_id, produccion_id, nombre, precio_unitario, cantidad, subtotal)`.
- Asistencia: `asistencias (user_id, fecha, horario_asistencia_id, estado, hora_marcada, registrado_por)`, `justificaciones`, `horarios_asistencia`, `dias_asistencia_acopiador`.
- Pedidos web: `pedidos_web (venta_id nullable, total, nombre, celular, estado)` y `pedido_web_items (pedido_web_id, produccion_id nullable, producto, cantidad, precio_unitario, subtotal)`.
- API móvil: `mobile_tokens`, `mobile_ids`. También `passkeys`, `teams`, `team_invitations`, `cache`, `jobs`.

Enums: `Rol, EstadoProduccion (Planificada "Pendiente", Completada "Publicado"), EstadoLeche, EstadoAsistencia, EstadoJustificacion, EstadoPedidoWeb (Pendiente, Confirmado, Cancelado), MetodoPago (Efectivo, Yape, Plin, Tarjeta, Otro), Periodo, TipoComunicado, TeamRole, TeamPermission`.

## REGLAS DE NEGOCIO CLAVE

- **Precio de venta:** `Receta::precioVenta()` devuelve `precio_venta` si es mayor que 0, y si no el `costoTotal()` (suma de cantidad × costo unitario de cada insumo). Se usa en ventas, inventario, productos, panel del vendedor y la portada.
- **Producción:** al registrar una producción se consumen los insumos de la receta (descuenta `stock_actual`; se bloquea si no alcanza). Completarla ("publicarla") la deja disponible para vender. `cantidadDisponible() = producida − vendida`. Un `VentaItem` al crearse descuenta la cantidad vendida de la producción.
- **Catálogo público:** `Produccion::catalogoPublico()` devuelve las producciones completadas con stock y receta, de la más reciente a la más antigua: `id, nombre (nombre o receta), precio, imagen (URL pública o null), disponible`.
- **Boleta:** número `B001-` + correlativo de 6 dígitos (`Venta::siguienteNumeroBoleta()`); PDF A5 con dompdf en `ventas/boleta.blade.php`.
- **Pedido web a venta:** `PedidoWeb::confirmarComoVenta(User, MetodoPago)` en una transacción con bloqueo: verifica estado pendiente y stock de todas las líneas (todo o nada), crea la venta (cliente libre = nombre del pedido; efectivo = total exacto, vuelto 0), crea los ítems, y deja el pedido confirmado y ligado a `venta_id`. Es idempotente.
- **Asistencia:** el administrador (o quien tenga el módulo) toma asistencia por tabla (presente, tardanza, falta); los trabajadores ven la suya y justifican faltas y tardanzas con archivo dentro de 7 días; los acopiadores marcan su propia asistencia según horario y días asignados, con falta automática al vencer el horario.
- Las fechas de negocio usan `config('app.zona_horaria_local')` (`America/Lima`).

## MÓDULOS POR ROL (páginas Livewire `pages::<rol>.<nombre>`)

- **admin:** panel, usuarios, asignar-roles, empresa, descuentos, turnos (de rutas y de producción), precios de la leche, acopiador-recojo, acopiador-entrega, proveedor-entregas, proveedor-laboratorio; además entra a rutas y comunicados de planta y a "Tomar asistencia".
- **planta:** panel, entregas de acopiadores, laboratorio (con PDF `planta/resultado-laboratorio-pdf`), rutas (mapa y trazo), pagos a proveedores, comunicados.
- **acopiador:** panel, recojo de ruta, mapa de recojo, entrega a planta, inventario (con PDF), asistencia propia.
- **proveedor:** panel, mis entregas, laboratorio, comunicados.
- **produccion:** panel, registros de producción, recetas (con campo **Precio de venta**), insumos, unidades, productos, compras, inventario, asistencia.
- **vendedor:** panel, ventas (punto de venta con carrito, descuentos, método de pago, boleta PDF), productos, historial, clientes, inventario, **pedidos-web**, asistencia.
- **asistencia (común):** `tomar`, `mis`, archivo de justificación protegido.
- **settings:** perfil, apariencia, seguridad (2FA y passkeys), equipos.

Componentes Blade: `app-logo`, `confirm-modal`, mapas (`mapa-ruta`, `mapa-rutas-general`, `mapa-ubicacion`, `ubicacion-grande`), `notification-bell`, `team-switcher`, `passkey-*`. Layout con menú lateral (`layouts/app/sidebar`) y layouts de autenticación.

## API MÓVIL (`routes/api.php`)

`POST /api/mobile/login` (throttle 30/min), `GET /api/mobile/sync` y `POST /api/mobile/cambios` protegidos por el middleware `AuthenticateMobileToken` (tokens en `mobile_tokens`, ids en `mobile_ids`). `MobileSyncController` entrega los datos del usuario según su rol; `MobileCambiosController` aplica los cambios hechos sin conexión. Existe una app nativa Kotlin Multiplatform aparte que consume esta API.

## PÁGINA PÚBLICA (`/`, vista `resources/views/welcome.blade.php`, CSS `landing.css`)

La ruta `home` pasa `empresa`, `productos` (`Produccion::catalogoPublico()`) y `whatsapp` (`Empresa::whatsapp()`).

**Identidad:** nombre Ecolácteos Huata, logo `public/images/logo-huata.jpg` (becerro con bandas azul, verde, amarilla y roja), foto `public/images/fondo-huata.jpg`. Paleta tomada del logo y de la leche, definida como tokens `@theme` en `landing.css`:
leche `#f8faff`, suero `#e8f0fc`, tinta `#0a1f44`, tinta-suave `#4a5c7a`, cielo-50 `#e6eefb`, cobalto 600 `#0b4fa3` / 700 `#083b7d` / 900 `#072a66` / 950 `#061b3f`, rojo 600 `#d7263d` / 700 `#b81d32`, sol (amarillo) `#f5c400`, eco `#2e9b4a`. **Sin verde como color de marca.** Tipografías: Bricolage Grotesque (títulos), Figtree (texto), Playfair Display (solo en la intro). Estilo claro, con acentos de cobalto, rojo para comprar y amarillo para el botón principal.

**Orden de la página:**
1. **Intro cinematográfica** (una vez por visita, `sessionStorage['huataIntroVista']`; se salta con "Saltar intro" o Esc; se omite con `prefers-reduced-motion`; `<noscript>` muestra la portada): tarjeta de apertura (logo + "Ecolácteos Huata" + "Del campo a tu mesa") de ~1,8 s mientras se precargan las fotos; luego seis escenas a pantalla completa con número 01–06, título en Playfair Display y texto corto abajo a la izquierda sobre degradado oscuro: 1 "Amanece en Huata", 2 "Manos que cuidan", 3 "Leche fresca, el mismo día" (foto propia), 4 "Control en cada paso", 5 "El arte de hacer queso", 6 "Listo para tu mesa". Grano de película (SVG `feTurbulence`), viñeteado, tinte cálido, polvo flotante y movimiento lento de cámara (Web Animations API). Transiciones de ~1,5 s que alternan acercamiento, desenfoque que se enfoca y deslizamiento lateral del 3 %. Cada foto dura ~2,5 s. Al final espera ~2 s, la cámara se acerca al queso (zoom ~7×) mientras un velo crema cubre la pantalla, y la portada "aterriza". Si las fotos fallan, continúa o salta. Si existen fotos en `public/images/intro/`, reemplazan a las de Unsplash en orden alfabético. Botón "Volver a ver la intro" en el pie.
2. **Cabecera fija** transparente sobre la portada y sólida al hacer scroll: logo, enlaces (Inicio, Quiénes somos, Productos, Cómo trabajamos, Contacto), botón de carrito con contador, "Iniciar sesión" y menú móvil accesible.
3. **Portada** a pantalla completa: carrusel de fondo (fundido lento con acercamiento; fotos de `fondo-huata.jpg`, de `public/images/carrusel/` y de los productos; barras de progreso por foto y botón de pausa) con parallax suave del mouse y velo azul; título "Sabor de la **leche fresca** de Huata", texto "Acopio, producción y venta de derivados lácteos, hechos con la leche fresca de las familias productoras de Huata.", botones "Haz tu pedido ›" (amarillo) y "Ver productos", y la línea "Asociación de productores de Huata, Puno". A la derecha, un **vaso de leche de cristal** en SVG dentro de una **esfera de cristal translúcida** (`backdrop-filter`), con cuatro burbujas que orbitan ("Queso fresco", "Yogurt", "Mantequilla", "Leche") y gotas que caen. Remata con una **ola de leche** blanca.
4. **Franja de confianza** con tres puntos: leche acopiada cada día, control de laboratorio en cada recojo, boleta en cada venta.
5. **Quiénes somos** y **¿Cómo se hace la compra?** (4 pasos: elige tus productos, agrégalos al carrito, confirma por WhatsApp, recibe o recoge con boleta).
6. **Productos disponibles** con buscador y tarjetas (foto o el vaso de leche, nombre, "N unidad(es) disponible(s)" con singular y plural correctos, precio "S/ 0.00 c/u", etiqueta "DESTACADO" en la primera). Botón **Agregar**; al agregar se vuelve un selector de cantidad con "N en el carrito". Sin productos: "Pronto publicaremos aquí nuestros productos frescos".
7. **Cómo trabajamos** (Acopio, Calidad, Producción, Venta), **Contacto** (dirección de `Empresa`, enlace "Ver en el mapa", teléfono si existe), franja de las cuatro bandas del logo y pie con "Iniciar sesión".

**Carrito (panel lateral):** líneas con foto, nombre, precio, cantidad ±, subtotal y quitar; total; campos "Tu nombre" (obligatorio) y "Celular" (opcional); "Pedir por WhatsApp" (o "Iniciar sesión para pedir" si la empresa no tiene teléfono); "Vaciar carrito"; estado de éxito "¡Pedido enviado!". Se guarda en `localStorage` (`huataCarrito`), valida contra el catálogo actual, respeta el máximo disponible, bloquea el scroll, atrapa el foco, cierra con Esc y usa `inert` cuando está cerrado. En celular hay una barra inferior "Ver carrito (N) · S/ total". Al enviar, hace `POST /pedidos` (CSRF, `throttle:8,1`) y recién entonces abre `https://wa.me/<número>?text=` con el mensaje "Hola Ecolácteos Huata, soy X. Pedido #N: • 2 × Producto (S/ 15.00) … Total: S/ 27.00".

**`PedidoWebController@store`:** valida `items` (1–30, `produccion_id` distinto, `cantidad` 1–1000), `nombre` (2–80) y `celular` (regex de dígitos, espacios, `+`, `-`); toma producto y precio del servidor; rechaza productos no publicados o cantidades sobre el stock; crea el pedido y sus líneas en una transacción; responde 201 con `id`, `total` y `lineas`.

**SEO y accesibilidad:** título, meta descripción, `canonical`, Open Graph, `twitter:card`, `theme-color`, datos estructurados `LocalBusiness`, `alt` y `loading="lazy"`, enlace para saltar al contenido, `prefers-reduced-motion` en todas las animaciones, foco visible, objetivos táctiles de 44 px o más.

## PANELES Y TEMA (`resources/css/app.css`)

Los paneles usan la escala `--color-huata-verde-50…950` (el nombre se conserva por compatibilidad), pero sus valores son **azul cobalto**: 50 `#eef4fd`, 100 `#dce8fb`, 200 `#b9d1f7`, 300 `#84acef`, 400 `#4c85e3`, 500 `#1f66cf`, 600 `#0b4fa3`, 700 `#083b7d`, 800 `#072f66`, 900 `#06255a`, 950 `#061b3f`; además `huata-celeste`, `huata-amarillo`, `huata-rojo`. `--color-accent` apunta a cobalto 700 (400 en oscuro). Las tarjetas usan la utilidad `huata-cuadro` (blanco translúcido con desenfoque). El menú lateral y el login llevan la foto de fondo con un **velo azul translúcido** (degradado `rgba(7,36,96,.9)` → `rgba(11,79,163,.72)` → `rgba(6,27,63,.88)` en el login). Los estados de éxito (insignias "Presente" o "Confirmado", avisos de guardado) siguen en verde semántico. Los PDF (boleta, inventario, laboratorio) usan los mismos azules.

## PRUEBAS A INCLUIR (Pest)

Acceso por roles; asistencia (toma, edición, justificaciones, horarios, marcación del acopiador); producción (receta con costo, registrar y completar descontando insumos, stock insuficiente); ventas desde producción y descuentos; laboratorio, pagos y entregas; comunicados y notificaciones; **página de inicio** (productos publicados con stock y precio, precio de venta propio, singular y plural, exclusión de pendientes y agotadas, mensaje vacío, WhatsApp con prefijo 51 y sin teléfono, carrito presente); **pedidos web** (guardar carrito con varios productos y precios del servidor, validaciones, el vendedor ve y cancela, confirmar crea venta con boleta y descuenta stock, todo o nada si falta stock, permisos); precio de venta editable en recetas.

## ORDEN SUGERIDO DE CONSTRUCCIÓN

1. Starter kit Livewire + Flux, Fortify, equipos, passkeys, roles y navegación.
2. Usuarios, empresa, turnos, rutas, precios y descuentos (administrador).
3. Acopio: recojos, entregas a planta, laboratorio y pagos a proveedores.
4. Producción: unidades, insumos, compras, recetas, registros y productos.
5. Ventas: punto de venta, boleta PDF, historial y clientes.
6. Asistencia con justificaciones y horarios.
7. Comunicados y notificaciones; API móvil.
8. Página pública: portada, productos, carrito, intro, SEO.
9. Pedidos web y su confirmación como venta con boleta.
10. Tema azul en toda la app, revisión de contraste y accesibilidad, Pint y la suite completa.

Al terminar, muéstrame un resumen corto con los archivos creados, lo que decidiste y lo que quedó pendiente.

---

## ACTUALIZACIÓN: página pública 3D y compra sin sesión

Esta sección reemplaza lo descrito arriba sobre la intro, la portada y el pago.

- **Imágenes de productos:** `php artisan storage:link` es obligatorio. `Produccion::imagenUrl()` devuelve `asset('storage/...')`.
- **Teléfono de la empresa** por defecto `941431262`; `Empresa::whatsapp()` lo convierte a `51941431262`. Los pedidos avisan a ese WhatsApp.
- **Compra sin iniciar sesión:** el carrito pide nombre, celular opcional y forma de pago (Yape, Plin o efectivo; con Yape/Plin, N.º de operación opcional). `POST /pedidos` guarda `pedidos_web` (con `metodo_pago`, `codigo_operacion` y un `token` secreto de 40 caracteres) y sus `pedido_web_items`. Responde con `url_boleta` (`/pedido/{token}/boleta`): mientras el pago no se confirma muestra un PDF "Comprobante de pedido" (`ventas/comprobante-pedido`); cuando el vendedor confirma, la misma dirección muestra la boleta oficial `B001-…`.
- **Vendedor:** menú "Compras por web" (`pages::vendedor.pedidos-web`): ve método de pago y N.º de operación, y "Confirmar y facturar" crea la venta con boleta, descuenta el stock (todo o nada) y deja el pedido ligado a `venta_id`.
- **3D (Three.js, GSAP y ScrollTrigger; sin React, la página es Blade):**
  - `resources/js/intro3d.js`: partículas en 3D, cámara que avanza, logotipo con texto en letras 3D y una corriente de leche (shader) que sube, llena la pantalla y baja revelando la portada. Una vez por visita (`sessionStorage['huataIntroVista']`), "Saltar intro", Esc y respeto a `prefers-reduced-motion`.
  - `resources/js/hero3d.js`: vaso de leche de cristal que flota, gira solo, sigue al mouse, se arrastra con inercia y responde al scroll (el hero queda fijo ~260svh; el vaso pasa al centro, crece y gira 90°).
  - `resources/js/productos3d.js`: vitrina "Elige tu sabor" con leche, queso, yogurt y mantequilla en 3D; la transición orbita la cámara, manda el producto actual al fondo, trae el nuevo desde el costado y cambia el color de fondo y los textos. El precio y el botón "Agregar al carrito" usan el catálogo real.
  - `resources/js/modelos3d.js`: los cuatro modelos hechos con geometría en código (no GLB).
  - `resources/js/interacciones.js`: cursor personalizado con "ARRASTRA PARA EXPLORAR", botones magnéticos e inclinación 3D de las tarjetas.
  - Si no hay WebGL, el hero conserva una esfera SVG de respaldo y la vitrina se oculta.
