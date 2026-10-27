# Procedencia del código

- Repositorio de origen Huata: https://github.com/Kazuto1253/Empresa-de-Leche
- Rama clonada: `web-laravel-huata`.
- Commit original: `0fd6a98e3010187a041baa1ce60cf79333f582d4` (25/10/2026).
- Esta publicación es una instantánea del estado de trabajo posterior, incluidos archivos
  modificados y nuevos, más inicialización administrativa segura para desarrollo.
- `payment-core/` procede del proyecto Kotlin/JVM externo `qr`; `yape-bridge/` del proyecto
  Android externo `yape-bridge`. Se copiaron sus fuentes sin modificar los directorios originales.
- No se importa el historial completo de otros repositorios ni configuraciones o datos privados.
  Se conserva esta referencia verificable al origen y las migraciones/pruebas de la evolución.
- Se omiten dependencias, builds, APK, bases, colas, sesiones, credenciales, claves de firma,
  salidas QR reales del laboratorio y el QR bancario estático antiguo que ya no usa el checkout.
  El informe académico DOCX no forma parte del código funcional. Las configuraciones de ejemplo
  no activan receptores ni sustituyen la verificación física de pagos.
