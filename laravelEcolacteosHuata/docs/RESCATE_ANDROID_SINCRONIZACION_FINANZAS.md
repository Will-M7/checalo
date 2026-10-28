# Rescate Android: sesión, Proveedor y presentación de Finanzas

## Diagnóstico reproducido antes de corregir

Ktor **3.0.3** mantenía `BearerTokens` en su propio caché. Cambiar o eliminar el token de `TokenStorage` no actualizaba necesariamente el encabezado enviado. Una prueba HTTP aislada reprodujo el problema: después de cambiar la cuenta, el servidor seguía recibiendo la sesión anterior (HTTP 200 cuando debía rechazarla). También seguía autorizando después de borrar el token. La secuencia «prueba sin token → guardar primer token» sí funcionó; no se atribuye el problema a todos los primeros ingresos.

En `RealSyncService`, una identidad remota distinta de la sesión local activa provoca el rechazo de la sincronización y la eliminación del token guardado. Con el caché anterior, esa protección podía borrar el token recién obtenido de otra cuenta. Además, el login programaba la sincronización antes de establecer su nueva sesión local; publicar el token también podía activar la primera descarga con la identidad previa.

Una descarga inicial fallida no entraba en los reintentos de arranque, recuperación de red o temporizador, porque esos caminos exigían una sincronización anterior. El botón de Proveedor solo releía SQLite y la ausencia de fila se describía incorrectamente como un rol nuevo.

La prueba de conexión usa `/api/mobile/sync` y acepta un 401 para demostrar accesibilidad. No comprueba una sesión autenticada ni garantiza que se hayan descargado datos. Finanzas consulta su API directamente y puede funcionar independientemente del espejo local de Proveedor.

**Límite del diagnóstico:** estas causas están comprobadas en código y pruebas HTTP/SQLite. No se obtuvo Logcat del Infinix: ADB no pudo abrir su conexión en el entorno restringido. La causa específica de la instalación observada debe confirmarse con el procedimiento físico; no se afirma haber probado el teléfono.

## Corrección

- El transporte lee el token seguro vigente en cada petición, sin caché de otro usuario, sin cambiar credenciales ni actualizar dependencias. Respeta encabezados explícitos del Bridge y omite el login.
- El login establece la identidad nueva antes de publicar su token y programa la sincronización después. Una respuesta 401 atrasada no elimina un token diferente recién guardado.
- Se reintentan también las descargas iniciales al arrancar, recuperar la red y en la vuelta periódica; guardar una conexión ya autenticada permite descargar aunque la fecha sea «nunca».
- Errores HTTP 403, 404 y 5xx conservan su código y etapa; formato incompatible y timeout tienen mensajes propios. Las respuestas y credenciales no se imprimen. La cancelación deja de mostrar carga y se propaga.
- Proveedor observa los cambios locales, actualiza su perfil automáticamente y distingue descarga pendiente, sesión requerida, error y ausencia de perfil después de descargar. Su botón vuelve a sincronizar.
- El panel de conexión permite confirmar la contraseña de la cuenta activa y recuperar la descarga sin cerrar sesión ni borrar datos. La contraseña no se persiste y se limpia del campo al enviar.
- Finanzas reutiliza `HeroMetricCard`, `KpiCard`, `SectionCard`, `TituloSeccion`, `EstadoChip`, `InfoRow` y los controles/filtros existentes. Mantiene todos los importes, advertencias, filtros, paginaciones, clientes, pedidos y acciones. No se modificaron su ViewModel, transporte, endpoints ni contabilidad.
- `EcolacteosHuata/AGENTS.md` deja la regla permanente de reutilizar el diseño existente para nuevas pantallas.

## Archivos de aplicación corregidos

Todos bajo `EcolacteosHuata/shared/src/commonMain/kotlin/com/example/ecolacteoshuata/`:

- `data/remote/HttpClientFactory.kt`
- `data/remote/datasource/SyncRemoteDataSource.kt`
- `data/sync/SyncService.kt`
- `domain/usecase/AuthUseCases.kt`
- `domain/usecase/LookupUseCases.kt`
- `presentation/common/conexion/ConexionSyncViewModel.kt`
- `presentation/common/conexion/ConexionSyncSheet.kt`
- `presentation/proveedor/ProveedorRootViewModel.kt`
- `presentation/proveedor/ProveedorRootScreen.kt`
- `presentation/admin/finanzas/FinanzasScreen.kt`
- `di/ViewModelModule.kt`

Pruebas nuevas: `laravelEcolacteosHuata/tests/Feature/Api/RescateProveedorAndroidTest.php`, `EcolacteosHuata/shared/src/androidHostTest/kotlin/com/example/ecolacteoshuata/authorization/RescateSincronizacionTest.kt` y su contrato sintético `shared/src/androidHostTest/resources/huata-proveedor-sync.json`.

No se modificaron endpoints ni código operativo de Laravel. No se requieren migraciones. Las pruebas Laravel usan SQLite en memoria y bloquean peticiones financieras externas.

## Procedimiento físico en el Infinix

1. Actualizar conservando datos y solo si paquete/firma son compatibles con la instalación existente. No desinstalar, borrar datos ni forzar una firma distinta. Véase también `YAPE_INTEGRADO_HUATA.md`.
2. Mantener el servidor actual. En Huata abrir **Conexión y sincronización** y comprobar el host/puerto guardados. Una prueba exitosa solo acredita accesibilidad.
3. Si aparece **Confirmar sesión**, ingresar la contraseña de la misma cuenta y pulsar **Confirmar y sincronizar**. Si no se requiere, pulsar **Sincronizar ahora**.
4. Comprobar que la última sincronización deja de ser «nunca» y que el resumen incluye el proveedor. Si falla, anotar el código/mensaje sin copiar tokens, contraseñas ni notificaciones financieras. Reintentar desde el mismo panel.
5. Con el mismo usuario de Laravel, comprobar Panel, Mis entregas, Laboratorio y Comunicados. Elegir las mismas fechas/periodos que en Web; no se amplían filtros para simular registros. Verificar nombre, litros, importes, calidad y audiencia de avisos. Los datos deben aparecer sin cerrar sesión.
6. Interrumpir la conectividad del teléfono y reintentar: la carga debe terminar con error y conservar datos. Recuperar la conexión y sincronizar. No detener ni reiniciar servicios para esta prueba.
7. Cambiar entre una cuenta autorizada de Finanzas y Proveedor usando el cierre de sesión normal únicamente para esta prueba de cambio de cuenta. Verificar que no se descarga la identidad de la otra cuenta. Recuperar la sesión no exige salir de ella.
8. En Finanzas, comparar con Web los mismos filtros y cifras en céntimos. Consultar clientes, paginar y abrir pedidos con sus vendedores. Revisar legibilidad con texto ampliado. No crear rendiciones ficticias para probar la interfaz.
9. Abrir Acopiador y Producción y consultar registros existentes. Comprobar que la cabecera y la navegación inferior conservan su diseño.
10. En la configuración Yape, comprobar la autorización existente y el acceso al listener. No cambiar receptores, revocar instalaciones ni enviar evidencias financieras de prueba en producción.

## Preservación

No se modifican estilos globales, colores de roles, navegación inferior, diseño Web, portada, filtros compartidos, distribución de pedidos, libro financiero, Payment Core ni Bridge original/integrado. Sin commit, push, migraciones, instalación, borrado de cachés/datos o reinicio de servicios. La APK se genera una sola vez al terminar las comprobaciones.

## Validación y APK

- **5 pruebas Laravel aprobadas, 50 aserciones**: contrato de las cuatro consultas de Proveedor, volcado general, rechazo sin token, usuario desactivado y ámbito propio del usuario autenticado. SQLite en memoria; ningún movimiento operativo.
- **31 pruebas Android aprobadas, cero fallos**: sesión vigente tras cambio/borrado de token; contrato Laravel por HTTP autenticado, persistencia SQLite y enlace por `usuario_id`; actualización automática del perfil; Panel, Entregas, Laboratorio y Comunicados mediante sus ViewModels; HTTP 403/404/500, pérdida de token y reintento sin pérdida de datos; importes exactos y rendiciones offline/idempotentes; identidad y cola offline entre sesiones; permisos de Producción; los 23 casos existentes del Bridge integrado.
- La comparación SHA-256 con el inicio del trabajo confirma **458 fuentes protegidas idénticas**. Entre ellas están todo el backend operativo revisado, rutas, vistas Web, migraciones, manifiesto Android, Bridge integrado, estilos/componentes globales, pantallas de Acopiador/Producción y ViewModel/transporte de Finanzas. Son 11 archivos existentes de aplicación modificados; el resto son pruebas, contrato y documentación nuevos.
- `:androidApp:assembleDebug` finalizó **BUILD SUCCESSFUL**. Una sola generación de APK, sin instalación.
- APK: `/home/willian/Desarrollo/Huata/EcolacteosHuata/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- SHA-256 APK: `794300a4df5a00dec49743d2f9f1f20a022d1f5212f8e430115eca451159dd1c`.
- Paquete: `com.example.ecolacteoshuata.huata`, versión `1.0`, código `1`, minSdk 24, targetSdk 36.
- Firma verificada con `apksigner`: SHA-256 certificado `53bb3b0f9c147ec628c1111ab06621030e999c1b0d3d58f7a06931e6e60004ed`, igual a la APK local anterior. Esto no certifica cuál está instalada en el Infinix: comprobarla antes de actualizar conservando datos.

Comandos focalizados usados (desde cada proyecto):

```bash
# Laravel
php artisan test tests/Feature/Api/RescateProveedorAndroidTest.php tests/Feature/Api/MobileSyncTest.php --filter='proveedor descarga|volcado completo|rechaza teléfonos|hash del usuario|el token deja'

# Kotlin; usa las herramientas ya instaladas, sin descargar dependencias
ANDROID_HOME=/home/willian/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest --tests '*RescateSincronizacionTest' --tests '*FinanzasAndroidTest' --tests '*Huata*Test' --tests '*AutorizacionMultiplataformaTest.loginRemoto*' --tests '*AutorizacionMultiplataformaTest.reconectar*' --tests '*AutorizacionMultiplataformaTest.pendientesDeOtraSesion*' --tests '*IntegracionRolesAdministrativosTest.supervisorSinEnum*' --offline --no-daemon
```

Pendiente exclusivamente de validación física: el Infinix, su instalación concreta, conectividad, Logcat y la revisión visual con su escala de texto. Si Laravel deniega una sesión real, confirmar la contraseña en el panel; si una descarga autenticada correcta no devuelve el perfil, el administrador debe revisar la asociación y permisos de esa cuenta. No se crean perfiles ni se cambian credenciales para ocultar el problema.

