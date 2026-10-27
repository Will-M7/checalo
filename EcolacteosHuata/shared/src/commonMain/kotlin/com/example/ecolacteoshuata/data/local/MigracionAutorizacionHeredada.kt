package com.example.ecolacteoshuata.data.local

import com.example.ecolacteoshuata.data.repository.AutorizacionLocal
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.*

/** Snapshot único de los accesos del APK anterior. No se consulta para autorizar después de migrar. */
object MigracionAutorizacionHeredada {
    // Catálogo congelado de la versión 20. Las funciones futuras solo llegan desde Laravel.
    private val pantallasVersion20 = listOf(
        "admin.panel",
        "admin.usuarios",
        "admin.asignar-roles",
        "admin.descuentos",
        "admin.precios",
        "planta.rutas",
        "admin.turnos",
        "admin.empresa",
        "vendedor.configuracion-pagos",
        "asistencia.tomar",
        "vendedor.pedidos-web",
        "planta.comunicados",
        "planta.panel",
        "planta.laboratorio",
        "planta.entregas",
        "planta.pagos",
        "asistencia.mis",
        "acopiador.panel",
        "acopiador.recojo",
        "acopiador.mapa-recojo",
        "acopiador.entrega",
        "acopiador.inventario",
        "proveedor.panel",
        "proveedor.entregas",
        "proveedor.laboratorio",
        "proveedor.comunicados",
        "vendedor.panel",
        "vendedor.ventas",
        "vendedor.produccion",
        "vendedor.historial",
        "vendedor.clientes",
        "vendedor.inventario",
        "produccion.panel",
        "produccion.registros",
        "produccion.recetas",
        "produccion.insumos",
        "produccion.unidades",
        "produccion.productos",
        "produccion.compras",
        "produccion.inventario",
        "admin.roles"
    )
    fun ejecutar(db: AppDatabase) {
        if (db.autorizacionLocalQueries.estadoMigracion().executeAsOne() != 0L) return
        db.transaction {
            db.usuarioQueries.selectAll().executeAsList().forEach { u ->
                if (AutorizacionLocal.leer(db, u.id) != null) return@forEach
                val principal = runCatching { Rol.fromStoredName(u.rol) }.getOrNull()
                val categorias = (Rol.decodificarLista(u.roles) + listOfNotNull(principal)).distinct()
                val adicionales = if (u.toma_asistencia == 1L) listOf("asistencia.tomar") else emptyList()
                val roles = categorias.map { categoria ->
                    val prefijos = when (categoria) {
                        Rol.ADMIN -> listOf("admin.", "planta.", "vendedor.", "produccion.")
                        Rol.PERSONAL_PLANTA -> listOf("planta.")
                        Rol.ACOPIADOR -> listOf("acopiador.")
                        Rol.PROVEEDOR -> listOf("proveedor.")
                        Rol.VENDEDOR -> listOf("vendedor.")
                        Rol.PRODUCCION -> listOf("produccion.")
                    }
                    val funciones = (pantallasVersion20.filter { id -> prefijos.any { id.startsWith(it) } } + "asistencia.mis" +
                        if (categoria == Rol.ADMIN) listOf("asistencia.tomar") else emptyList()).distinct()
                    RolAsignado(categoria.valorServidor, categoria.etiqueta, funciones)
                } + if (adicionales.isNotEmpty()) listOf(RolAsignado("tomar_asistencia", "Tomar asistencia", adicionales, auxiliar = true)) else emptyList()
                AutorizacionLocal.guardar(db, AutorizacionUsuario(usuario_id = u.id, version = "heredada-v20", categorias_historicas = categorias.map { it.valorServidor },
                    roles = roles, pantallas = if (u.activo == 1L) (roles.flatMap { it.pantallas } + adicionales).distinct() else emptyList(),
                    pantallas_adicionales = adicionales, politica_offline = "ultima_autorizacion_heredada; reemplazar al conectar con Laravel"))
            }
            db.autorizacionLocalQueries.terminarMigracion()
        }
    }
}
