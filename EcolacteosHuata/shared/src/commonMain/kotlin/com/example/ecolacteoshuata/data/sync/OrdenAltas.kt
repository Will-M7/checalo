package com.example.ecolacteoshuata.data.sync

import app.cash.sqldelight.db.SqlDriver

/** Solo metadatos de presentación. Una fecha desconocida permanece NULL. */
object OrdenAltas {
    val tablas = listOf("usuario", "acopiador", "proveedor", "descuento", "cliente", "unidad_medida", "insumo", "receta", "comunicado", "recojo", "resultado_laboratorio", "entrega_planta", "compra", "compra_detalle", "produccion", "venta", "producto")
    fun instalar(driver: SqlDriver) {
        tablas.forEach { tabla ->
            driver.execute(null, "INSERT OR IGNORE INTO orden_alta(tabla,registro_id,creado_en) SELECT '$tabla',id,NULL FROM $tabla WHERE NOT EXISTS (SELECT 1 FROM orden_alta WHERE tabla='$tabla' AND registro_id=$tabla.id) ORDER BY rowid", 0)
            driver.execute(null, "CREATE TRIGGER IF NOT EXISTS orden_alta_${tabla}_ins AFTER INSERT ON $tabla WHEN NOT EXISTS (SELECT 1 FROM orden_alta WHERE tabla='$tabla' AND registro_id=NEW.id) BEGIN " +
                "INSERT INTO orden_alta(tabla,registro_id,creado_en) VALUES ('$tabla',NEW.id," +
                "CASE WHEN COALESCE((SELECT aplicando FROM sync_control WHERE id=1),0)=0 THEN strftime('%Y-%m-%dT%H:%M:%fZ','now') ELSE NULL END); END", 0)
            driver.execute(null, "CREATE TRIGGER IF NOT EXISTS orden_alta_${tabla}_id AFTER UPDATE OF id ON $tabla WHEN OLD.id != NEW.id AND NOT EXISTS (SELECT 1 FROM orden_alta WHERE tabla='$tabla' AND registro_id=NEW.id) BEGIN " +
                "UPDATE orden_alta SET registro_id=NEW.id WHERE tabla='$tabla' AND registro_id=OLD.id; END", 0)
        }
    }
}
