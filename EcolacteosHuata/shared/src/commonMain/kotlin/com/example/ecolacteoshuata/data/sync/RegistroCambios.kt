package com.example.ecolacteoshuata.data.sync

import app.cash.sqldelight.db.SqlDriver

/**
 * Anota en `cambio_pendiente` todo lo que se crea, edita o elimina en el teléfono en las tablas
 * que también existen en Laravel, sin importar qué pantalla o rol lo hizo. Son triggers de
 * SQLite: no dependen de que cada repositorio se acuerde de registrar el cambio. Mientras se
 * guarda lo que llega del servidor (`sync_control.aplicando = 1`) no anotan nada.
 */
object RegistroCambios {
    /** Tablas locales que Laravel refleja, en el orden en que dependen unas de otras. */
    val TABLAS = listOf(
        "usuario", "acopiador", "proveedor", "empresa", "precio_leche", "descuento", "turno",
        "turno_produccion", "ruta", "cliente", "unidad_medida", "insumo", "receta", "receta_detalle",
        "comunicado", "recojo", "resultado_laboratorio", "entrega_planta", "pago_proveedor", "compra",
        "compra_detalle", "produccion", "venta", "horario_asistencia", "dia_asistencia_acopiador",
        "asistencia", "justificacion",
    )

    // Subir la versión obliga a recrear los triggers si cambia su definición.
    private const val VERSION = 2

    private const val SI_NO_ES_DEL_SERVIDOR =
        "WHEN COALESCE((SELECT aplicando FROM sync_control WHERE id = 1), 0) = 0"

    fun instalar(driver: SqlDriver) {
        driver.execute(null, "INSERT OR IGNORE INTO sync_control(id, aplicando) VALUES (1, 0)", 0)
        instalarAutorizacion(driver)
        TABLAS.forEach { tabla ->
            listOf("ins", "upd", "del").forEach { evento -> driver.execute(null, "DROP TRIGGER IF EXISTS cambio_${tabla}_${evento}_v1", 0) }
            trigger(driver, "${tabla}_ins", "AFTER INSERT ON $tabla", "'$tabla', NEW.id, 'guardar'")
            trigger(driver, "${tabla}_upd", "AFTER UPDATE ON $tabla", "'$tabla', NEW.id, 'guardar'")
            trigger(driver, "${tabla}_del", "AFTER DELETE ON $tabla", "'$tabla', OLD.id, 'eliminar'")
        }
        // Los proveedores de una ruta (y su orden) viajan con la ruta, y los ítems de una venta con
        // la venta (se insertan junto con ella, así que no necesitan trigger propio).
        listOf("ins", "upd", "del").forEach { driver.execute(null, "DROP TRIGGER IF EXISTS cambio_ruta_proveedor_${it}_v1", 0) }
        trigger(driver, "ruta_proveedor_ins", "AFTER INSERT ON ruta_proveedor", "'ruta', NEW.ruta_id, 'guardar'")
        trigger(driver, "ruta_proveedor_upd", "AFTER UPDATE ON ruta_proveedor", "'ruta', NEW.ruta_id, 'guardar'")
        trigger(driver, "ruta_proveedor_del", "AFTER DELETE ON ruta_proveedor", "'ruta', OLD.ruta_id, 'guardar'")
    }

    private fun instalarAutorizacion(driver: SqlDriver) {
        val permisos = mapOf(
        "empresa" to listOf("admin.empresa"),
        "precio_leche" to listOf("admin.precios"),
        "descuento" to listOf("admin.descuentos"),
        "usuario" to listOf("admin.usuarios"),
        "acopiador" to listOf("admin.usuarios"),
        "proveedor" to listOf("admin.usuarios", "proveedor.panel", "planta.laboratorio"),
        "turno" to listOf("admin.turnos"),
        "turno_produccion" to listOf("admin.turnos"),
        "ruta" to listOf("planta.rutas"),
        "ruta_proveedor" to listOf("planta.rutas"),
        "cliente" to listOf("vendedor.clientes"),
        "unidad_medida" to listOf("produccion.unidades"),
        "insumo" to listOf("produccion.insumos", "produccion.registros", "produccion.compras", "planta.laboratorio"),
        "receta" to listOf("produccion.recetas"),
        "receta_detalle" to listOf("produccion.recetas"),
        "comunicado" to listOf("planta.comunicados"),
        "recojo" to listOf("acopiador.recojo", "acopiador.entrega", "planta.pagos"),
        "resultado_laboratorio" to listOf("planta.laboratorio"),
        "entrega_planta" to listOf("acopiador.entrega"),
        "pago_proveedor" to listOf("planta.pagos", "acopiador.recojo"),
        "compra" to listOf("produccion.compras"),
        "compra_detalle" to listOf("produccion.compras"),
        "produccion" to listOf("produccion.registros", "vendedor.ventas"),
        "venta" to listOf("vendedor.ventas", "vendedor.historial"),
        "venta_item" to listOf("vendedor.ventas", "vendedor.historial"),
        "horario_asistencia" to listOf("asistencia.tomar"),
        "dia_asistencia_acopiador" to listOf("asistencia.tomar"),
        "asistencia" to listOf("asistencia.tomar", "asistencia.mis"),
        "justificacion" to listOf("asistencia.tomar", "asistencia.mis"),
        )
        permisos.forEach { (tabla, pantallas) ->
            val lista = pantallas.joinToString { "'$it'" }
            listOf("INSERT", "UPDATE", "DELETE").forEach { evento ->
                val perfilPropio = if (tabla == "usuario" && evento == "UPDATE") " AND OLD.id != (SELECT usuario_id FROM autorizacion_sesion WHERE id = 1)" else ""
                driver.execute(null, "CREATE TRIGGER IF NOT EXISTS permiso_${tabla}_${evento}_v1 BEFORE $evento ON $tabla " +
                    "WHEN COALESCE((SELECT aplicando FROM sync_control WHERE id = 1), 0) = 0 " +
                    "AND (SELECT usuario_id FROM autorizacion_sesion WHERE id = 1) IS NOT NULL" + perfilPropio +
                    " AND NOT EXISTS (SELECT 1 FROM autorizacion_pantalla WHERE usuario_id = (SELECT usuario_id FROM autorizacion_sesion WHERE id = 1) AND pantalla IN ($lista)) " +
                    "BEGIN SELECT RAISE(ABORT, 'La función no está asignada en la autorización local.'); END", 0)
            }
        }
    }

    private fun trigger(driver: SqlDriver, nombre: String, evento: String, valores: String) {
        driver.execute(
            null,
            "CREATE TRIGGER IF NOT EXISTS cambio_${nombre}_v$VERSION $evento $SI_NO_ES_DEL_SERVIDOR BEGIN " +
                "INSERT INTO cambio_pendiente(tabla, registro_id, operacion, actor_id) VALUES ($valores, (SELECT usuario_id FROM autorizacion_sesion WHERE id = 1)); END",
            0,
        )
    }
}
