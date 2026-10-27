# Configuración comercial de ejemplo

El JSON tiene campos secretos vacíos y no puede arrancar hasta completarlos; no contiene credenciales válidas. No reutilices secretos de otra instalación.
Genera dos valores independientes con `python3 -c 'import secrets; print(secrets.token_hex(32))'`,
guarda el archivo resultante fuera de `public` y de Git con permisos 0600 y usa un tercer archivo
0600 para la credencial de aplicación Laravel (`PAYMENT_CORE_KEY_FILE`). No muestres esos valores
ni los publiques. `receivers` y `devices` están vacíos: no hay receptor listo para cobrar.

Para registrar metadatos propios conservando receptores deshabilitados, también existe
`tools/create_commercial_config.py`; consulta `--help` y `COMMERCIAL.md`. La verificación física,
inscripción y habilitación siguen siendo pasos distintos. No uses configuraciones operativas
ajenas ni marques PAID para probar el acceso administrativo.
