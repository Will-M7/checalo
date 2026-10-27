# Validación de la publicación

## Actualización de fuentes posterior al respaldo e3e6c928af57d39738ebe79716fc6a3ba85e77fc

La actualización conserva el historial de `main`, las instrucciones de instalación, el seeder inicial y los cuatro componentes publicados. Incorpora las fuentes locales acumuladas de pedidos/saldo, Finanzas Web y Android, clientes/saldos, disponibilidad, roles/distribuciones y filtros, incluidos archivos de datos Kotlin que faltaban en el respaldo anterior.

Se verifican inclusión y coincidencia de fuentes, migraciones y documentación, exclusión de secretos/artefactos operativos y el manifiesto SHA-256. Se conservan las adaptaciones portables de las herramientas de integración y las configuraciones públicas de ejemplo. Core y Bridge mantienen sus fuentes publicadas: coinciden con las fuentes locales correspondientes; no se publica la ruta personal de Java ni muestras privadas.

Las validaciones funcionales previas a esta publicación están documentadas en `laravelEcolacteosHuata/docs/FINANZAS_ANDROID_DISPONIBILIDAD_WEB.md`: 33 pruebas Laravel focalizadas y 11 Kotlin aprobadas. Durante la publicación no se ejecutan migraciones, suites, builds de APK ni reinicios de servicios. No se atribuye al árbol nuevo la instalación limpia descrita en el registro histórico siguiente.

## Registro histórico de la publicación inicial

Fecha: 27/10/2026. Instalación preparada en carpeta y SQLite nuevas de `/tmp`, sin copiar `.env`,
bases, claves o dependencias de la instalación operativa.

- `composer install --no-interaction --prefer-dist`: aprobado desde composer.lock.
- `composer check-platform-reqs`: aprobado (entorno de prueba PHP 8.5.4).
- `npm ci --no-audit --no-fund` y `npm run build`: aprobados (Node 24.21.0).
- Todas las migraciones SQLite: aprobadas desde base vacía.
- `php artisan db:seed` dos veces: una cuenta admin, mismo hash, rol y membresía.
- Validación sobre esa SQLite nueva: login admin/1234, administración crítica, alta de rol nuevo,
  creación de otro usuario mediante el servicio existente, asignación dinámica y pertenencia a Teams.
- Catálogo: 41 pantallas y seis roles base. Distribuciones Inicial y En desarrollo presentes.
- Laravel: 515 casos, 509 aprobados, 2128 aserciones; seis opt-in omitidos en el pase normal.
- Integración Laravel–Core opt-in: seis aprobadas, 80 aserciones, puerto aleatorio y datos ficticios.
- Core copiado: 274 pruebas aprobadas y `installDist` generado en la copia de validación.
- Cinco pruebas nuevas comprueban admin inicial, idempotencia/preservación de cuentas existentes,
  rechazo sobre instalaciones pobladas, bloqueo de contraseña de prueba y contraseña propia en producción.
- La primera pasada de PHP arrancó antes de terminar los assets y se descartó por manifiesto Vite
  ausente; el resultado aprobado anterior corresponde a la repetición con el build completado.

No se arrancaron Laravel 8000, Core operativo 8087 ni Scheduler. El Core temporal de integración
se detuvo al terminar. No se instalaron APK ni se hicieron pagos reales. Las APK no se recompilaron
para esta publicación: sus fuentes actuales se conservan; el primer acceso administrativo no
requiere Android, SDK, claves de firma, Bridge ni un receptor Yape habilitado. iOS requiere macOS/Xcode
para enlazar su aplicación y su firma propia.

La publicación no habilita cobros reales: la inscripción de Bridge y la verificación física del
receptor siguen pendientes en cada instalación propia. Las escenas 3D y los módulos comerciales
mantienen sus fuentes y diseños; esta entrega no realiza mejoras financieras.
