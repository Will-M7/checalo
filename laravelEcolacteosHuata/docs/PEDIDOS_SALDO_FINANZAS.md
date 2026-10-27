# Pedidos, saldo y finanzas operativas

> Actualización posterior: [Finanzas Android y disponibilidad web](FINANZAS_ANDROID_DISPONIBILIDAD_WEB.md). La ampliación incorpora Android, disponibilidad comercial y clientes/saldos; las restricciones de plataforma y la política de recargas descritas abajo corresponden a la entrega anterior.

Implementación exclusivamente Laravel. No altera Android, Payment Core, Bridge ni las distribuciones guardadas. La portada, hero, layouts, fondos y estilos globales se conservan.

## Uso

1. Ingresar como comprador, agregar productos y confirmar el carrito. Se valida la cotización en servidor, se asigna vendedor y se guardan líneas y precios. El pedido queda pendiente de pago, sin intención Yape, reserva de stock ni débito. Un reintento con la misma referencia recupera el pedido. Los clientes antiguos que todavía envíen un método al endpoint también obtienen un pedido pendiente.
2. Mi cuenta muestra pedidos, productos, cantidades, total, vendedor y estados reales. PAGAR CON SALDO confirma la venta y descuenta stock y saldo dentro de una transacción. Los reintentos no duplican la venta. Sin stock o saldo suficiente, todo se revierte y el pedido permanece guardado.
3. RECARGAR SALDO abre el modal. Se permite corregir el teléfono de contacto, normalizado por el servidor; no constituye identidad bancaria. Yape es el único método de recarga habilitado: Plin y efectivo no tienen un flujo de conciliación de recarga implementado. El saldo nunca sirve para recargarse a sí mismo.
4. El vendedor del último pedido determina el receptor de recarga. Si aún no hay pedidos, se conserva el vendedor de la primera recarga. La primera asignación sin antecedentes utiliza el reparto existente. Cada intención congela su receptor y conserva su referencia al reintentar. Un vendedor sin receptor disponible bloquea el pago; no se cambia silenciosamente a otro.
5. El QR aparece después del alta canónica de la intención y de comprobar disponibilidad local/remota, identidad del receptor, teléfono, huella QR, habilitación, verificación y Bridge comunicado. Introducir los tres dígitos únicamente si Yape los muestra. Consultar no acredita por importe/hora ni por una notificación ambigua. Solo el PAID canónico autenticado con evidencia única aplica el ingreso. Tras aplicarse se actualiza Mi cuenta mediante GET, sin repetir el POST.
6. Un pedido con cualquier intento externo previo requiere revisar ese cobro antes de cambiar a saldo. No se combinan métodos ni se descarta evidencia vencida. Los pedidos Yape históricos mantienen su flujo y precios originales.
7. Tras un pedido pagado, ABRIR WHATSAPP prepara un mensaje de productos, cantidades y total para el vendedor original. El número comercial del vendedor se congela por separado del receptor financiero. No incluye códigos, tokens ni credenciales; no envía automáticamente. Si el contacto falta, no se inventa un número.

## Finanzas

Ruta `/{current_team}/admin/finanzas`, pantalla dinámica `admin.finanzas`, exclusivamente web. La migración añade únicamente esa pantalla al rol Administrador activo; otros roles requieren asignación explícita desde el gestor existente. Se conservan las distribuciones base históricas de 41 pantallas; el catálogo tiene ahora 42, con 39 correspondencias Android sin cambios. Aplicar posteriormente una distribución anterior puede retirar Finanzas como cualquier asignación; el administrador conserva el gestor de roles para volver a asignarla.

La tabla incremental `movimientos_financieros` utiliza céntimos enteros, claves de origen únicas, relaciones a cliente/pedido/venta/pago/receptor/custodio/actor y movimientos relacionados. Las correcciones no editan ni eliminan asientos.

- PAID Core: un ingreso externo, custodia del propietario de la cuenta receptora, y obligación de saldo cuando se acredita al cliente. El receptor central tampoco se presupone patrimonio empresarial disponible: hace falta confirmar su entrega.
- Recarga S/100, compra con saldo S/30: recaudación S/100, venta S/30, saldo/pasivo S/70. No hay otro ingreso externo por la compra.
- Venta efectiva en efectivo registrada por el flujo presencial autorizado: ingreso bajo custodia del vendedor, descontando el vuelto. Otros medios declarados sin conciliación se identifican como no conciliados; no generan disponibilidad por mera selección.
- Entrega/rendición: seleccionar un ingreso con fondos pendientes, indicar importe y constancia, marcar recepción real y confirmar. Se valida autorización, límite pendiente y referencia idempotente. La entrega aumenta fondos empresariales y reduce custodia; no aumenta recaudación ni ventas.
- El registro de pago a proveedores desde Planta incorpora el egreso en la misma transacción que el pago y sus recojos. La misma fuente no produce otro asiento al reintentarlo. No se convierte una compra de insumos en egreso sin confirmación de pago.
- Disponible operativo = máximo de cero y (entregas registradas − egresos confirmados − saldos de clientes − obligaciones de recojos entregados pendientes de pago). Se muestran déficit y custodia separados. No es saldo bancario certificado; no presupone fondos iniciales ni obligaciones futuras desconocidas.
- Los saldos reales heredados siguen formando parte del pasivo. Ventas, pagos a proveedores o PAID antiguos sin asientos se muestran como no conciliados, sin fabricar ingresos históricos. Los abonos administrativos sin ingreso conciliado también se señalan.
- Los registros de ventas/pagos incorporados por importación o sincronización que no pasen por los servicios Laravel instrumentados se muestran entre las fuentes sin asiento. No se modificó la API móvil ni Android para esta tarea; deben conciliarse antes de interpretar las cifras como cobertura histórica completa.

Las tarjetas de posición actual no se filtran: evitan presentar un saldo parcial como disponibilidad total. Los filtros fecha/vendedor/receptor/tipo se aplican a movimientos y flujos del período.

## Despliegue y validación

Migración conservadora: `2026_10_27_180000_add_finanzas_operativas.php`. No borra tablas anteriores, no cambia usuarios, contraseñas, Teams, permisos de otros módulos ni configuración de receptores. Su rollback no destruye automáticamente el libro financiero. No ejecutar `migrate:fresh` sobre la base operativa.

Pruebas aisladas con SQLite `:memory:`:

```bash
DB_CONNECTION=sqlite DB_DATABASE=:memory: DB_URL='' php vendor/bin/pest tests/Feature/PedidosSaldoFinanzasTest.php tests/Feature/PlantaPagosTest.php tests/Feature/AdminInicialSeederTest.php
```

La conciliación externa se simula únicamente dentro de las pruebas. El pago real sigue dependiendo del receptor verificado/habilitado, vinculación comercial Core y Bridge comunicado; este trabajo no los activa ni sustituye la verificación física pendiente. No se modificaron claves ni APK.

## Archivos de esta implementación

Dentro de `laravelEcolacteosHuata/`:

- `app/Http/Controllers/PedidoWebController.php`: checkout pendiente, sin cobro.
- `app/Http/Controllers/ClientesWeb/CuentaController.php`: pedidos detallados, pago con saldo, recarga JSON y estados.
- `app/Models/PedidoWeb.php`: vendedor y enlace WhatsApp tras pago.
- `app/Services/Payments/CobrosService.php`: pago posterior idempotente, receptor estable y asiento del ingreso canónico.
- `app/Services/Payments/SaldoService.php`: registro financiero de obligación/consumo.
- `app/Services/Payments/VentasService.php`: asiento de venta y selección del intento PAID vigente.
- `resources/views/welcome.blade.php`: simplificación del carrito y envío del pedido.
- `resources/views/clientes-web/cuenta.blade.php`: detalle, saldo y acciones de pedidos.
- `resources/views/clientes-web/pago.blade.php`: WhatsApp del vendedor original.
- `resources/views/clientes-web/recarga-modal.blade.php` (nuevo): recarga y verificación.
- `routes/clientes-web.php`: POST autenticado para pagar con saldo.
- `app/Models/MovimientoFinanciero.php` (nuevo): libro inmutable.
- `app/Services/Finanzas/FinanzasService.php` (nuevo): ingresos, pasivos, ventas, custodia, rendición, egresos y resumen.
- `resources/views/pages/admin/⚡finanzas.blade.php` (nuevo): panel, filtros y confirmación de entrega.
- `app/Authorization/Pantallas.php` y `routes/admin.php`: módulo dinámico Finanzas.
- `resources/views/pages/planta/⚡pagos.blade.php`: enlace transaccional del egreso al pago existente, sin cambiar su interfaz.
- `resources/views/pages/vendedor/⚡pedidos-web.blade.php`: los pedidos nuevos pendientes se pagan desde Mi cuenta; se conserva el tratamiento de los históricos.
- `database/migrations/2026_10_27_180000_add_finanzas_operativas.php` (nueva): tabla, secuencia y alta auditada de la pantalla.
- `tests/Feature/PedidosSaldoFinanzasTest.php` (nuevo): validación financiera integrada y controles de acceso.
- `tests/Feature/PagosCanonicosTest.php`: actualización del contrato de checkout.
- `tests/Feature/AdminInicialSeederTest.php` y `tests/Feature/DistribucionesRolesTest.php`: contemplan el catálogo ampliado, sin modificar las plantillas guardadas.
- `docs/PEDIDOS_SALDO_FINANZAS.md` (este documento): uso, limitaciones y validación.

Se generaron los assets mediante `npm run build`. No se modificaron los estilos fuente globales.

## Resultado local

- Migración incremental aplicada; 65 tablas previas comparadas. Solo cambiaron `migrations`, `integracion_secuencias`, `authorization_roles` (una pantalla añadida al administrador), `authorization_state` y `authorization_audit`. Usuarios, relaciones, saldos y datos comerciales conservados. Integridad SQLite: `ok`.
- Copia SQLite anterior: `/tmp/huata-antes-finanzas-20261027-123314.sqlite`, permisos 0600. No se extrajo ni reemplazó la base con esa copia.
- Libro operativo recién creado vacío: no se acreditaron recargas, registraron ventas ni fabricaron datos operativos de prueba.
- 31 pruebas focalizadas aprobadas, 229 aserciones: 21 de pedidos/saldo/finanzas, pagos de Planta y administrador inicial; 8 regresiones canónicas seleccionadas; 2 del catálogo/distribución.
- Sintaxis PHP verificada en 13 archivos y JavaScript del carrito/modal válida. Assets compilados.
- Dos receptores locales; cero receptores simultáneamente verificados y habilitados al finalizar. No se declara operativo un cobro Yape real.
- Sin commit, push, reinicio de servicios ni cambios Android, Core, Bridge, credenciales o diseños globales.
