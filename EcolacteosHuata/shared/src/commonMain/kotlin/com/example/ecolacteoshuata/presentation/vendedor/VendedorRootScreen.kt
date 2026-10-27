package com.example.ecolacteoshuata.presentation.vendedor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ecolacteoshuata.core.theme.TemaVendedor
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.presentation.admin.clientes.AdminClientesScreen
import com.example.ecolacteoshuata.presentation.admin.clientes.AdminClientesViewModel
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.MasSheet
import com.example.ecolacteoshuata.presentation.common.RootScaffold
import com.example.ecolacteoshuata.presentation.common.asistencia.TomarAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.MiAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloMiAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloTomarAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.rememberPuedeTomarAsistencia
import com.example.ecolacteoshuata.presentation.navigation.Screen
import com.example.ecolacteoshuata.presentation.vendedor.dashboard.VendedorDashboardScreen
import com.example.ecolacteoshuata.presentation.vendedor.dashboard.VendedorDashboardViewModel
import com.example.ecolacteoshuata.presentation.vendedor.historial.VendedorHistorialScreen
import com.example.ecolacteoshuata.presentation.vendedor.historial.VendedorHistorialViewModel
import com.example.ecolacteoshuata.presentation.vendedor.inventario.VendedorInventarioScreen
import com.example.ecolacteoshuata.presentation.produccion.productos.ProduccionProductosScreen
import com.example.ecolacteoshuata.presentation.produccion.productos.ProduccionProductosViewModel
import com.example.ecolacteoshuata.presentation.vendedor.venta.VendedorVentaScreen
import com.example.ecolacteoshuata.presentation.vendedor.venta.VendedorVentaViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VendedorRootScreen(usuario: Usuario, onCerrarSesion: () -> Unit, soloContenido: Boolean = false, estadoPantalla: androidx.compose.runtime.MutableState<Screen>? = null, modulosExternos: List<ModuloItem>? = null) {
    val rootViewModel = koinViewModel<VendedorRootViewModel>()
    val rootState by rootViewModel.state.collectAsState()

    var pantalla by (estadoPantalla ?: remember { mutableStateOf<Screen>(Screen.VendedorDashboard) })

    val puedeTomarAsistencia = rememberPuedeTomarAsistencia(usuario)
    val modulosDelRol = listOfNotNull(
        ModuloItem("Venta", Icons.Filled.PointOfSale, "Cobra al público en segundos") { pantalla = Screen.VendedorVenta },
        ModuloItem("Productos", Icons.Filled.Storefront, "Lo producido y publicado para la venta") { pantalla = Screen.VendedorProductos },
        ModuloItem("Historial de ventas", Icons.Filled.History, "Boletas emitidas") { pantalla = Screen.VendedorHistorialVentas },
        ModuloItem("Clientes", Icons.Filled.Groups, "Tu base de clientes") { pantalla = Screen.VendedorClientes },
        // Como en Laravel: a quien se le asignó "Tomar asistencia", ese módulo reemplaza a su "Asistencia".
        if (puedeTomarAsistencia) {
            moduloTomarAsistencia { pantalla = Screen.TomarAsistencia }
        } else {
            moduloMiAsistencia("Tus asistencias y justificaciones") { pantalla = Screen.MiAsistencia }
        },
        ModuloItem("Inventario", Icons.Filled.Inventory, "Listo para la venta") { pantalla = Screen.VendedorInventario },
    )

    val modulos = modulosExternos ?: modulosDelRol

    val (titulo, subtitulo) = when (pantalla) {
        Screen.VendedorDashboard -> "Panel de control" to "Bienvenido, ${usuario.nombreCompleto}"
        Screen.VendedorVenta -> "Venta" to "Registrar venta al público"
        Screen.VendedorProductos -> "Productos" to "Lo producido y publicado para la venta"
        Screen.VendedorHistorialVentas -> "Historial de ventas" to "Boletas emitidas y su PDF"
        Screen.VendedorClientes -> "Clientes" to "Compran en el punto de venta"
        Screen.VendedorInventario -> "Inventario" to "Stock listo para la venta"
        Screen.TomarAsistencia -> "Tomar asistencia" to "Asistencia de los trabajadores"
        Screen.MiAsistencia -> "Asistencia" to "Tus asistencias y justificaciones"
        else -> "Ecolácteos Huata" to null
    }

    val contenido: @Composable () -> Unit = {
        when (pantalla) {
            Screen.VendedorDashboard -> {
                val vm = koinViewModel<VendedorDashboardViewModel>()
                val state by vm.state.collectAsState()
                VendedorDashboardScreen(state = state, modulos = modulos) {
                    if (usuario.autorizacion?.permite("vendedor.pedidos-web") == true) DisponibilidadWebControl()
                }
            }
            Screen.VendedorVenta -> {
                val vm = koinViewModel<VendedorVentaViewModel>(key = "venta-${usuario.id}") { parametersOf(usuario.id) }
                val productos by vm.productos.collectAsState()
                val clientes by vm.clientes.collectAsState()
                val descuentos by vm.descuentosDisponibles.collectAsState()
                val state by vm.state.collectAsState()
                if ("vendedor.ventas" !in usuario.autorizacion?.capacidades_comerciales.orEmpty()) {
                    androidx.compose.material3.Text("La recepción de dinero no concede permiso para vender. Se requiere capacidad comercial explícita y actualizar la autorización del servidor.")
                } else VendedorVentaScreen(
                    productos = productos,
                    clientes = clientes,
                    descuentos = descuentos,
                    state = state,
                    onAgregarProducto = vm::agregarProducto,
                    onQuitarUnidad = vm::quitarUnidad,
                    onSeleccionarCliente = vm::seleccionarCliente,
                    onAlternarDescuento = vm::alternarDescuento,
                    onNombreLibreChange = vm::onNombreLibreChange,
                    onDocLibreChange = vm::onDocLibreChange,
                    onMetodoPagoChange = vm::onMetodoPagoChange,
                    onEfectivoChange = vm::onEfectivoChange,
                    onCobrar = vm::cobrar,
                )
            }
            Screen.VendedorProductos -> {
                val vm = koinViewModel<ProduccionProductosViewModel>()
                val productos by vm.productos.collectAsState()
                val busqueda by vm.busqueda.collectAsState()
                ProduccionProductosScreen(productos = productos, busqueda = busqueda, onBusquedaChange = vm::onBusquedaChange, estadoStock = true)
            }
            Screen.VendedorHistorialVentas -> {
                val vm = koinViewModel<VendedorHistorialViewModel>()
                val state by vm.state.collectAsState()
                VendedorHistorialScreen(
                    state = state,
                    onDesde = vm::onDesde, onHasta = vm::onHasta, onMetodo = vm::onMetodo, onBusqueda = vm::onBusqueda,
                    onVendedor = vm::onVendedor, onLimpiarFiltros = vm::limpiarFiltros,
                    onDescargarBoleta = vm::descargarBoleta,
                    onPedirLimpiar = vm::pedirLimpiar, onCancelarLimpiar = vm::cancelarLimpiar, onLimpiar = vm::limpiarHistorial,
                    onConsumirMensaje = vm::consumirMensaje,
                )
            }
            Screen.VendedorClientes -> {
                val vm = koinViewModel<AdminClientesViewModel>()
                val state by vm.state.collectAsState()
                val form by vm.formVisible.collectAsState()
                val porEliminar by vm.porEliminar.collectAsState()
                val error by vm.error.collectAsState()
                val mensaje by vm.mensaje.collectAsState()
                AdminClientesScreen(
                    state = state, formVisible = form, porEliminar = porEliminar, error = error, mensaje = mensaje,
                    onBusqueda = vm::onBusqueda, onAbrirNuevo = vm::abrirNuevo, onAbrirEditar = vm::abrirEditar,
                    onCerrarForm = vm::cerrarForm, onGuardar = vm::guardar,
                    onPedirEliminar = vm::pedirEliminar, onCancelarEliminar = vm::cancelarEliminar, onEliminar = vm::eliminar,
                    onConsumirMensaje = vm::consumirMensaje,
                )
            }
            Screen.TomarAsistencia -> TomarAsistenciaHost(usuario)
            Screen.MiAsistencia -> MiAsistenciaHost(usuario)
            Screen.VendedorInventario -> {
                // Mismo origen que "Productos" de producción: lo producido y publicado.
                val vm = koinViewModel<ProduccionProductosViewModel>()
                val productos by vm.productos.collectAsState()
                val busqueda by vm.busqueda.collectAsState()
                VendedorInventarioScreen(productos = productos, busqueda = busqueda, onBusquedaChange = vm::onBusquedaChange)
            }
            else -> Unit
        }

    }
    if (soloContenido) contenido() else {
    RootScaffold(
        usuario = usuario,
        rolEtiqueta = "Vendedor",
        tema = TemaVendedor,
        estadoSync = rootState.estadoSync,
        pendientes = rootState.pendientes,
        onCerrarSesion = onCerrarSesion,
        tituloPantalla = titulo,
        subtituloPantalla = subtitulo,
        bottomNavItems = listOf(
            BottomNavItem("Panel", Icons.Filled.Dashboard),
            BottomNavItem("Venta", Icons.Filled.PointOfSale),
            BottomNavItem("Historial", Icons.Filled.History),
            BottomNavItem("Clientes", Icons.Filled.Groups),
        ),
        bottomNavSeleccionado = when (pantalla) {
            Screen.VendedorDashboard -> 0
            Screen.VendedorVenta -> 1
            Screen.VendedorHistorialVentas -> 2
            Screen.VendedorClientes -> 3
            else -> -1
        },
        onBottomNavSeleccionar = { index ->
            pantalla = when (index) {
                0 -> Screen.VendedorDashboard
                1 -> Screen.VendedorVenta
                2 -> Screen.VendedorHistorialVentas
                3 -> Screen.VendedorClientes
                else -> pantalla
            }
        },
        masSheetContent = { onDismiss ->
            MasSheet(titulo = "Módulos de venta", items = modulos, onDismiss = onDismiss)
        },
        content = contenido,
    )
    }
}
