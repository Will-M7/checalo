package com.example.ecolacteoshuata.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.ecolacteoshuata.domain.platform.YapeReception
import com.example.ecolacteoshuata.presentation.common.conexion.SolicitudRecepcionYape
import com.example.ecolacteoshuata.presentation.common.conexion.RecepcionYapeScreen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import com.example.ecolacteoshuata.core.theme.*
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.model.RolAsignado
import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.presentation.common.*
import com.example.ecolacteoshuata.presentation.common.asistencia.MiAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.TomarAsistenciaHost
import org.koin.compose.koinInject
import com.example.ecolacteoshuata.presentation.admin.AdminRootScreen
import com.example.ecolacteoshuata.presentation.planta.PlantaRootScreen
import com.example.ecolacteoshuata.presentation.acopiador.AcopiadorRootScreen
import com.example.ecolacteoshuata.presentation.proveedor.ProveedorRootScreen
import com.example.ecolacteoshuata.presentation.produccion.ProduccionRootScreen
import com.example.ecolacteoshuata.presentation.vendedor.VendedorRootScreen

object PantallasNativas {
    val pantallas: Map<String, Screen> = mapOf(
        "admin.panel" to Screen.AdminDashboard,
        "admin.usuarios" to Screen.AdminUsuarios,
        "admin.asignar-roles" to Screen.AdminAsignarRoles,
        "admin.finanzas" to Screen.AdminFinanzas,
        "admin.roles" to Screen.AdminRoles,
        "admin.descuentos" to Screen.AdminDescuentos,
        "admin.precios" to Screen.AdminPrecios,
        "admin.turnos" to Screen.AdminTurnos,
        "admin.empresa" to Screen.AdminEmpresa,
        "planta.panel" to Screen.PlantaDashboard,
        "planta.laboratorio" to Screen.PlantaLaboratorio,
        "planta.entregas" to Screen.PlantaRecepcion,
        "planta.pagos" to Screen.PlantaPagos,
        "planta.rutas" to Screen.PlantaRutas,
        "planta.comunicados" to Screen.PlantaComunicados,
        "acopiador.panel" to Screen.AcopiadorDashboard,
        "acopiador.recojo" to Screen.AcopiadorRecojo,
        "acopiador.mapa-recojo" to Screen.AcopiadorMapaRecojo,
        "acopiador.entrega" to Screen.AcopiadorEntrega,
        "acopiador.inventario" to Screen.AcopiadorInventario,
        "proveedor.panel" to Screen.ProveedorDashboard,
        "proveedor.entregas" to Screen.ProveedorEntregas,
        "proveedor.laboratorio" to Screen.ProveedorLaboratorio,
        "proveedor.comunicados" to Screen.ProveedorComunicados,
        "vendedor.panel" to Screen.VendedorDashboard,
        "vendedor.ventas" to Screen.VendedorVenta,
        "vendedor.produccion" to Screen.VendedorProductos,
        "vendedor.historial" to Screen.VendedorHistorialVentas,
        "vendedor.clientes" to Screen.VendedorClientes,
        "vendedor.inventario" to Screen.VendedorInventario,
        "produccion.panel" to Screen.ProduccionDashboard,
        "produccion.registros" to Screen.ProduccionRegistros,
        "produccion.recetas" to Screen.ProduccionRecetas,
        "produccion.insumos" to Screen.ProduccionInsumos,
        "produccion.unidades" to Screen.ProduccionUnidades,
        "produccion.productos" to Screen.ProduccionProductos,
        "produccion.compras" to Screen.ProduccionCompras,
        "produccion.inventario" to Screen.ProduccionInventario,
        "asistencia.tomar" to Screen.TomarAsistencia,
        "asistencia.mis" to Screen.MiAsistencia,
    )
    data class Aspecto(val etiqueta: String, val icono: androidx.compose.ui.graphics.vector.ImageVector, val descripcion: String)
    val aspecto = mapOf(
        "admin.finanzas" to Aspecto("Finanzas", Icons.Filled.AttachMoney, "Fondos, rendiciones, clientes y saldos"),
        "planta.laboratorio" to Aspecto("Laboratorio", Icons.Filled.Science, "Foto de resultados + OCR"),
        "planta.entregas" to Aspecto("Entregas de acopiadores", Icons.Filled.LocalShipping, "Recepción de leche"),
        "planta.pagos" to Aspecto("Pago a proveedores", Icons.Filled.AttachMoney, "Pagos por periodo"),
        "planta.rutas" to Aspecto("Rutas", Icons.Filled.Route, "Rutas, proveedores y recorrido"),
        "planta.comunicados" to Aspecto("Comunicados y asesoramiento", Icons.Filled.Campaign, "Avisos para la asociación"),
        "acopiador.recojo" to Aspecto("Recojo de ruta", Icons.Filled.LocalShipping, "Registra la leche de cada proveedor"),
        "acopiador.mapa-recojo" to Aspecto("Mapa de recojo", Icons.Filled.Map, "Sigue el camino y tus paradas"),
        "acopiador.entrega" to Aspecto("Entrega a planta", Icons.Filled.Factory, "Cierra el turno en planta"),
        "acopiador.inventario" to Aspecto("Inventario", Icons.Filled.Inventory, "Leche pendiente de entregar"),
        "admin.roles" to Aspecto("Roles y funciones", Icons.Filled.Shield, "Configuración central de funciones"),
        "admin.usuarios" to Aspecto("Usuarios", Icons.Filled.People, "Cuentas del sistema"),
        "admin.asignar-roles" to Aspecto("Asignar rol a usuarios", Icons.Filled.AssignmentInd, "Uno o varios roles por usuario"),
        "admin.descuentos" to Aspecto("Descuentos", Icons.Filled.Discount, "Para el punto de venta"),
        "admin.precios" to Aspecto("Precios de la leche", Icons.Filled.AttachMoney, "Precio vigente por litro"),
        "admin.turnos" to Aspecto("Turnos de rutas y producción", Icons.Filled.Schedule, "Horarios de recojo y planta"),
        "admin.empresa" to Aspecto("Empresa", Icons.Filled.Storefront, "Datos de la asociación"),
        "vendedor.ventas" to Aspecto("Venta", Icons.Filled.PointOfSale, "Cobra al público en segundos"),
        "vendedor.produccion" to Aspecto("Productos", Icons.Filled.Storefront, "Lo producido y publicado para la venta"),
        "vendedor.historial" to Aspecto("Historial de ventas", Icons.Filled.History, "Boletas emitidas"),
        "vendedor.clientes" to Aspecto("Clientes", Icons.Filled.Groups, "Tu base de clientes"),
        "vendedor.inventario" to Aspecto("Inventario", Icons.Filled.Inventory, "Listo para la venta"),
        "proveedor.entregas" to Aspecto("Mis entregas", Icons.Filled.LocalShipping, "Litros y pagos por periodo"),
        "proveedor.laboratorio" to Aspecto("Laboratorio", Icons.Filled.Science, "Calidad de mi leche"),
        "proveedor.comunicados" to Aspecto("Comunicados", Icons.Filled.Campaign, "Avisos de la asociación"),
        "produccion.registros" to Aspecto("Producción", Icons.Filled.Science, "Registra y publica producciones"),
        "produccion.insumos" to Aspecto("Insumos", Icons.Filled.ShoppingCart, "Catálogo, costos y compras"),
        "produccion.unidades" to Aspecto("Unidades de medida", Icons.Filled.Scale, "kg, L, unidades…"),
        "produccion.productos" to Aspecto("Productos", Icons.Filled.Storefront, "Listos para vender"),
        "produccion.compras" to Aspecto("Compras", Icons.Filled.LocalShipping, "Compras de insumos e ingresos de stock"),
        "produccion.inventario" to Aspecto("Inventario", Icons.Filled.Inventory, "Stock de insumos"),
        "admin.panel" to Aspecto("Panel", Icons.Filled.Dashboard, ""),
        "planta.panel" to Aspecto("Panel", Icons.Filled.Dashboard, ""),
        "acopiador.panel" to Aspecto("Panel", Icons.Filled.Dashboard, ""),
        "proveedor.panel" to Aspecto("Panel", Icons.Filled.Dashboard, ""),
        "vendedor.panel" to Aspecto("Panel", Icons.Filled.Dashboard, ""),
        "produccion.panel" to Aspecto("Panel", Icons.Filled.Dashboard, ""),
        "asistencia.mis" to Aspecto("Asistencia", Icons.Filled.CalendarMonth, "Tus asistencias y justificaciones"),
        "asistencia.tomar" to Aspecto("Tomar asistencia", Icons.Filled.CalendarMonth, "Asistencia de los trabajadores"),
    )
    val etiquetas = aspecto.mapValues { it.value.etiqueta }
    val barraHistorica = mapOf(
        "administrador" to listOf("admin.panel", "admin.usuarios", "planta.rutas", "admin.turnos"),
        "personal_planta" to listOf("planta.panel", "planta.laboratorio", "planta.entregas", "planta.rutas"),
        "acopiador" to listOf("acopiador.panel", "acopiador.recojo", "acopiador.mapa-recojo", "acopiador.entrega"),
        "proveedor" to listOf("proveedor.panel", "proveedor.entregas", "proveedor.laboratorio", "proveedor.comunicados"),
        "vendedor" to listOf("vendedor.panel", "vendedor.ventas", "vendedor.historial", "vendedor.clientes"),
        "produccion" to listOf("produccion.panel", "produccion.registros", "produccion.recetas", "produccion.insumos"),
    )
    fun encabezado(id: String?, usuario: Usuario): Pair<String, String?> = when (id) {
        "planta.panel" -> "Panel de planta" to "Resumen del día"
        "planta.entregas" -> "Entregas de acopiadores" to "Recepción de leche"
        "planta.laboratorio" -> "Laboratorio" to "Resultados de calidad de leche"
        "planta.rutas" -> "Rutas" to "Proveedores y recorrido del camioncito"
        "planta.pagos" -> "Pago a proveedores" to "Por periodo"
        "planta.comunicados" -> "Comunicados y asesoramiento" to "Avisos para la asociación"
        "asistencia.tomar" -> "Tomar asistencia" to "Asistencia de los trabajadores"
        "acopiador.panel" -> "Panel del acopiador" to "Tu ruta de hoy"
        "acopiador.recojo" -> "Recojo de ruta" to "Selecciona un proveedor"
        "acopiador.mapa-recojo" -> "Mapa de recojo" to "Tus paradas y el camino"
        "acopiador.entrega" -> "Entrega a planta" to "Cierre de turno"
        "acopiador.inventario" -> "Inventario" to "Leche pendiente de entregar"
        "asistencia.mis" -> "Asistencia" to "Tus asistencias y justificaciones"
        "admin.panel" -> "Panel de administración" to "Resumen general de la asociación"
        "admin.usuarios" -> "Usuarios" to "Gestión de cuentas del sistema"
        "admin.asignar-roles" -> "Asignar rol a usuarios" to "Uno o varios roles por usuario"
        "admin.descuentos" -> "Descuentos" to "Descuentos del punto de venta"
        "admin.precios" -> "Precios" to "Precio de la leche"
        "admin.turnos" -> "Turnos" to "De rutas y de producción"
        "admin.empresa" -> "Empresa" to "Datos de la asociación"
        "vendedor.panel" -> "Panel de control" to "Bienvenido, ${usuario.nombreCompleto}"
        "vendedor.ventas" -> "Venta" to "Registrar venta al público"
        "vendedor.produccion" -> "Productos" to "Lo producido y publicado para la venta"
        "vendedor.historial" -> "Historial de ventas" to "Boletas emitidas y su PDF"
        "vendedor.clientes" -> "Clientes" to "Compran en el punto de venta"
        "vendedor.inventario" -> "Inventario" to "Stock listo para la venta"
        "proveedor.panel" -> "Panel del proveedor" to "Bienvenido, ${usuario.nombreCompleto}"
        "proveedor.entregas" -> "Mis entregas" to "Leche entregada y estado de pago"
        "proveedor.laboratorio" -> "Resultados de laboratorio" to "Calidad de mi leche"
        "proveedor.comunicados" -> "Comunicados y asesoramiento" to "Avisos publicados por la planta"
        "produccion.panel" -> "Panel de producción" to "Resumen del día"
        "produccion.registros" -> "Producción" to "Registros de lotes producidos"
        "produccion.recetas" -> "Recetas" to "Insumos por receta"
        "produccion.insumos" -> "Insumos" to "Catálogo de insumos"
        "produccion.unidades" -> "Unidades de medida" to "kg, L, unidades…"
        "produccion.productos" -> "Productos" to "Listos para vender"
        "produccion.compras" -> "Compras de insumos" to "Ingresos de stock y precios"
        "produccion.inventario" -> "Inventario" to "Stock de insumos"
        else -> (etiquetas[id] ?: "Ecolácteos Huata") to null
    }
    fun id(screen: Screen): String? = when (screen) {
        Screen.AdminRutas, is Screen.AdminRutaDetalle, is Screen.PlantaRutaDetalle -> "planta.rutas"
        Screen.AdminComunicados -> "planta.comunicados"
        is Screen.PlantaLaboratorioRegistro -> "planta.laboratorio"
        is Screen.AcopiadorRecojoForm -> "acopiador.recojo"
        else -> pantallas.entries.firstOrNull { it.value == screen }?.key
    }
}

/** Usa RootScaffold y los seis hosts existentes. El enum de negocio no decide el acceso. */
@Composable
fun RootAutorizado(usuario: Usuario, rol: RolAsignado, onCerrarSesion: () -> Unit) {
    val reception = koinInject<YapeReception>()
    val receptionState by reception.state.collectAsState()
    LaunchedEffect(usuario.id) { reception.refresh() }
    SolicitudRecepcionYape()
    val a = usuario.autorizacion
    val ids = (rol.pantallas + a?.pantallas_adicionales.orEmpty()).distinct().filter {
        a?.permite(it) == true && PantallasNativas.pantallas.containsKey(it) &&
            !(it == "asistencia.mis" && ("asistencia.tomar" in a.pantallas_adicionales || rol.id == "administrador") && !usuario.esAcopiador)
    }
    val sync = koinInject<SyncService>()
    val estado by sync.estado.collectAsState()
    val pendientes by sync.pendientes.collectAsState()
    if (ids.isEmpty()) {
        var recepcionAbierta by remember(usuario.id) { mutableStateOf(false) }
        RootScaffold(usuario, rol.nombre, TemaAdmin, estado, pendientes, onCerrarSesion,
            "Funciones", bottomNavItems = emptyList(), bottomNavSeleccionado = -1, onBottomNavSeleccionar = {}) {
            if (recepcionAbierta && receptionState.authorized) {
                Column { androidx.compose.material3.TextButton(onClick = { recepcionAbierta = false }) { androidx.compose.material3.Text("Volver a funciones") }; RecepcionYapeScreen() }
            } else Column(Modifier.padding(18.dp)) {
                EmptyState("Sin funciones móviles asignadas", "Este rol no tiene pantallas nativas habilitadas. Revisa su asignación en Laravel.")
                if (receptionState.authorized) ModulosGrid(listOf(ModuloItem("Recepción Yape", Icons.Filled.NotificationsActive, "Estado del receptor y sus notificaciones") { recepcionAbierta = true }))
            }
        }
        return
    }
    val inicial = PantallasNativas.barraHistorica[rol.id]?.firstOrNull()?.takeIf { it in ids } ?: ids.first()
    val pantallas = (listOf(inicial) + ids).distinct().mapNotNull { PantallasNativas.pantallas[it] }
    val estadoPantalla = remember(usuario.id, rol.id) { mutableStateOf(pantallas.first()) }
    val actual = estadoPantalla.value.takeIf { PantallasNativas.id(it) in ids || (it == Screen.RecepcionYape && receptionState.authorized) } ?: pantallas.first()
    SideEffect { if (estadoPantalla.value != actual) estadoPantalla.value = actual }
    val modulosBase = ids.filterNot { it == PantallasNativas.barraHistorica[rol.id]?.firstOrNull() }.map { id ->
        val aspecto = PantallasNativas.aspecto[id]
        ModuloItem(aspecto?.etiqueta ?: id, aspecto?.icono ?: Icons.Filled.Apps, aspecto?.descripcion ?: "") {
            if (usuario.autorizacion?.permite(id) == true) estadoPantalla.value = PantallasNativas.pantallas.getValue(id)
        }
    }
    val tema = when (rol.id) {
        "personal_planta" -> TemaPlanta; "acopiador" -> TemaAcopiador; "proveedor" -> TemaProveedor
        "vendedor" -> TemaVendedor; "produccion" -> TemaProduccion; else -> TemaAdmin
    }
    val modulos = modulosBase + if (receptionState.authorized) listOf(ModuloItem("Recepción Yape", Icons.Filled.NotificationsActive, "Estado del receptor y sus notificaciones", tema.acento) { estadoPantalla.value = Screen.RecepcionYape }) else emptyList()
    val barra = ((PantallasNativas.barraHistorica[rol.id].orEmpty().filter { it in ids }) + ids).distinct().take(4)
    val idActual = PantallasNativas.id(actual)
    val baseCabecera = PantallasNativas.encabezado(idActual, usuario)
    data class Cabecera(val titulo: String, val subtitulo: String?, val volver: Boolean, val onVolver: () -> Unit)
    var cabecera by remember(actual) { mutableStateOf<Cabecera?>(null) }
    RootScaffold(
        usuario = usuario, rolEtiqueta = rol.nombre, tema = tema, estadoSync = estado, pendientes = pendientes,
        onCerrarSesion = onCerrarSesion,
        tituloPantalla = if (actual == Screen.RecepcionYape) "Recepción Yape" else cabecera?.titulo ?: baseCabecera.first,
        subtituloPantalla = cabecera?.subtitulo ?: baseCabecera.second,
        showBack = actual == Screen.RecepcionYape || cabecera?.volver == true,
        onBack = if (actual == Screen.RecepcionYape) ({ estadoPantalla.value = pantallas.first() }) else cabecera?.onVolver,
        expandirCabecera = idActual?.endsWith(".panel") == true,
        bottomNavItems = barra.map { BottomNavItem(PantallasNativas.etiquetas[it] ?: it, PantallasNativas.aspecto[it]?.icono ?: Icons.Filled.Apps) },
        bottomNavSeleccionado = barra.indexOf(idActual),
        onBottomNavSeleccionar = { estadoPantalla.value = PantallasNativas.pantallas.getValue(barra[it]) },
        masSheetContent = { dismiss -> MasSheet(titulo = "Módulos", items = modulos, onDismiss = dismiss) },
    ) {
        key(a?.version, rol.id) {
            when {
                actual == Screen.RecepcionYape -> RecepcionYapeScreen()
                idActual == "asistencia.mis" -> MiAsistenciaHost(usuario)
                idActual == "asistencia.tomar" -> TomarAsistenciaHost(usuario)
                idActual?.startsWith("admin.") == true -> AdminRootScreen(usuario, onCerrarSesion, true, estadoPantalla, modulos)
                idActual?.startsWith("planta.") == true -> PlantaRootScreen(usuario, onCerrarSesion, true, estadoPantalla, modulos) { titulo, subtitulo, volver, accion ->
                    if (cabecera?.titulo != titulo || cabecera?.subtitulo != subtitulo || cabecera?.volver != volver) cabecera = Cabecera(titulo, subtitulo, volver, accion)
                }
                idActual?.startsWith("produccion.") == true -> ProduccionRootScreen(usuario, onCerrarSesion, true, estadoPantalla, modulos)
                idActual?.startsWith("vendedor.") == true -> VendedorRootScreen(usuario, onCerrarSesion, true, estadoPantalla, modulos)
                idActual?.startsWith("acopiador.") == true -> AcopiadorRootScreen(usuario, onCerrarSesion, true, estadoPantalla, modulos)
                idActual?.startsWith("proveedor.") == true -> ProveedorRootScreen(usuario, onCerrarSesion, true, estadoPantalla, modulos)
            }
        }
    }
}
