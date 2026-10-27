package com.example.ecolacteoshuata.data.sync

import app.cash.sqldelight.db.SqlDriver

/** Solo tras comprobar la identidad remota; ejecutar dentro de la transacción de datos del servidor. */
fun migrarIdentidadUsuario(driver: SqlDriver, anterior: String, nuevo: String) {
    driver.execute(null, "PRAGMA defer_foreign_keys = ON", 0)
    val referencias = listOf(
        "usuario" to "id", "acopiador" to "usuario_id", "proveedor" to "usuario_id", "comunicado" to "autor_id",
        "venta" to "vendedor_id", "produccion" to "responsable_id", "asistencia" to "usuario_id",
        "asistencia" to "registrado_por", "cambio_pendiente" to "actor_id", "cambio_revision" to "actor_id",
        "autorizacion_sesion" to "usuario_id", "borrador_venta_yape" to "vendedor_id",
    )
    referencias.forEach { (tabla, columna) -> driver.execute(null, "UPDATE OR REPLACE $tabla SET $columna = ? WHERE $columna = ?", 2) { bindString(0, nuevo); bindString(1, anterior) } }
    driver.execute(null, "UPDATE cambio_pendiente SET registro_id = ? WHERE tabla = 'usuario' AND registro_id = ?", 2) { bindString(0, nuevo); bindString(1, anterior) }
}
