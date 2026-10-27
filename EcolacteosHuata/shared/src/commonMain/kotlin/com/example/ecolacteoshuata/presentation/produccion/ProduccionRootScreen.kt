package com.example.ecolacteoshuata.presentation.produccion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ecolacteoshuata.core.theme.TemaProduccion
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.presentation.produccion.productos.ProduccionProductosScreen
import com.example.ecolacteoshuata.presentation.produccion.productos.ProduccionProductosViewModel
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.MasSheet
import com.example.ecolacteoshuata.presentation.common.RootScaffold
import com.example.ecolacteoshuata.presentation.common.asistencia.TomarAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.MiAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloMiAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloTomarAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.rememberPuedeTomarAsistencia
import com.example.ecolacteoshuata.presentation.navigation.Screen
import com.example.ecolacteoshuata.presentation.produccion.compras.ProduccionComprasScreen
import com.example.ecolacteoshuata.presentation.produccion.compras.ProduccionComprasViewModel
import com.example.ecolacteoshuata.presentation.produccion.dashboard.ProduccionDashboardScreen
import com.example.ecolacteoshuata.presentation.produccion.dashboard.ProduccionDashboardViewModel
import com.example.ecolacteoshuata.presentation.produccion.insumos.ProduccionInsumosScreen
import com.example.ecolacteoshuata.presentation.produccion.insumos.ProduccionInsumosViewModel
import com.example.ecolacteoshuata.presentation.produccion.inventario.ProduccionInventarioScreen
import com.example.ecolacteoshuata.presentation.produccion.inventario.ProduccionInventarioViewModel
import com.example.ecolacteoshuata.presentation.produccion.recetas.ProduccionRecetasScreen
import com.example.ecolacteoshuata.presentation.produccion.recetas.ProduccionRecetasViewModel
import com.example.ecolacteoshuata.presentation.produccion.registros.ProduccionRegistrosScreen
import com.example.ecolacteoshuata.presentation.produccion.registros.ProduccionRegistrosViewModel
import com.example.ecolacteoshuata.presentation.produccion.unidades.ProduccionUnidadesScreen
import com.example.ecolacteoshuata.presentation.produccion.unidades.ProduccionUnidadesViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProduccionRootScreen(usuario: Usuario, onCerrarSesion: () -> Unit, soloContenido: Boolean = false, estadoPantalla: androidx.compose.runtime.MutableState<Screen>? = null, modulosExternos: List<ModuloItem>? = null) {
    val rootViewModel = koinViewModel<ProduccionRootViewModel>()
    val rootState by rootViewModel.state.collectAsState()

    var pantalla by (estadoPantalla ?: remember { mutableStateOf<Screen>(Screen.ProduccionDashboard) })

    val puedeTomarAsistencia = rememberPuedeTomarAsistencia(usuario)
    val modulosDelRol = listOfNotNull(
        ModuloItem("Producción", Icons.Filled.Science, "Registra y publica producciones") { pantalla = Screen.ProduccionRegistros },
        ModuloItem("Recetas", Icons.AutoMirrored.Filled.MenuBook, "Insumos por receta") { pantalla = Screen.ProduccionRecetas },
        ModuloItem("Insumos", Icons.Filled.ShoppingCart, "Catálogo, costos y compras") { pantalla = Screen.ProduccionInsumos },
        ModuloItem("Unidades de medida", Icons.Filled.Scale, "kg, L, unidades…") { pantalla = Screen.ProduccionUnidades },
        ModuloItem("Productos", Icons.Filled.Storefront, "Listos para vender") { pantalla = Screen.ProduccionProductos },
        ModuloItem("Compras", Icons.Filled.LocalShipping, "Compras de insumos e ingresos de stock") { pantalla = Screen.ProduccionCompras },
        // Como en Laravel: a quien se le asignó "Tomar asistencia", ese módulo reemplaza a su "Asistencia".
        if (puedeTomarAsistencia) {
            moduloTomarAsistencia { pantalla = Screen.TomarAsistencia }
        } else {
            moduloMiAsistencia("Tus asistencias y justificaciones") { pantalla = Screen.MiAsistencia }
        },
        ModuloItem("Inventario", Icons.Filled.Inventory, "Stock de insumos") { pantalla = Screen.ProduccionInventario },
    )

    val modulos = modulosExternos ?: modulosDelRol

    val (titulo, subtitulo) = when (pantalla) {
        Screen.ProduccionDashboard -> "Panel de producción" to "Resumen del día"
        Screen.ProduccionRegistros -> "Producción" to "Registros de lotes producidos"
        Screen.ProduccionRecetas -> "Recetas" to "Insumos por receta"
        Screen.ProduccionInsumos -> "Insumos" to "Catálogo de insumos"
        Screen.ProduccionUnidades -> "Unidades de medida" to "kg, L, unidades…"
        Screen.ProduccionProductos -> "Productos" to "Listos para vender"
        Screen.ProduccionCompras -> "Compras de insumos" to "Ingresos de stock y precios"
        Screen.ProduccionInventario -> "Inventario" to "Stock de insumos"
        Screen.TomarAsistencia -> "Tomar asistencia" to "Asistencia de los trabajadores"
        Screen.MiAsistencia -> "Asistencia" to "Tus asistencias y justificaciones"
        else -> "Ecolácteos Huata" to null
    }

    val contenido: @Composable () -> Unit = {
        when (pantalla) {
            Screen.ProduccionDashboard -> {
                val vm = koinViewModel<ProduccionDashboardViewModel>()
                val state by vm.state.collectAsState()
                ProduccionDashboardScreen(state = state, modulos = modulos)
            }
            Screen.ProduccionRegistros -> {
                val vm = koinViewModel<ProduccionRegistrosViewModel>()
                val registros by vm.registros.collectAsState()
                val recetas by vm.recetas.collectAsState()
                val turnos by vm.turnos.collectAsState()
                val form by vm.formVisible.collectAsState()
                val error by vm.error.collectAsState()
                val actualizar by vm.actualizar.collectAsState()
                ProduccionRegistrosScreen(
                    registros = registros, recetas = recetas, turnos = turnos, formVisible = form, error = error,
                    onAbrirNuevo = vm::abrirNuevo, onCerrarForm = vm::cerrarForm,
                    onAgregarLinea = vm::agregarLinea, onQuitarLinea = vm::quitarLinea,
                    onRecetaChange = vm::onRecetaChange, onCantidadChange = vm::onCantidadChange,
                    onTurnoChange = vm::onTurnoChange, onGuardar = vm::guardar,
                    onEditar = vm::abrirEditar, onEliminar = vm::eliminar, onPublicar = vm::publicar,
                    actualizar = actualizar, onAbrirActualizar = vm::abrirActualizar, onCerrarActualizar = vm::cerrarActualizar,
                    onActualizarCantidadChange = vm::onActualizarCantidadChange,
                    onActualizarTurnoChange = vm::onActualizarTurnoChange, onGuardarActualizacion = vm::guardarActualizacion,
                )
            }
            Screen.ProduccionRecetas -> {
                val vm = koinViewModel<ProduccionRecetasViewModel>()
                val recetas by vm.recetas.collectAsState()
                val insumos by vm.insumos.collectAsState()
                val form by vm.formVisible.collectAsState()
                val error by vm.error.collectAsState()
                ProduccionRecetasScreen(
                    recetas = recetas, insumos = insumos, formVisible = form, error = error,
                    onAbrirNuevo = vm::abrirNuevo, onAbrirEditar = vm::abrirEditar, onCerrarForm = vm::cerrarForm,
                    onNombreChange = vm::onNombreChange, onAgregarInsumo = vm::agregarInsumo,
                    onQuitarInsumo = vm::quitarInsumo, onGuardar = vm::guardar, onEliminar = vm::eliminar,
                )
            }
            Screen.ProduccionInsumos -> {
                val vm = koinViewModel<ProduccionInsumosViewModel>()
                val insumos by vm.insumos.collectAsState()
                val unidades by vm.unidades.collectAsState()
                val form by vm.formVisible.collectAsState()
                val error by vm.error.collectAsState()
                val compra by vm.compraInsumo.collectAsState()
                ProduccionInsumosScreen(
                    insumos = insumos, unidades = unidades, formVisible = form, error = error,
                    onAbrirNuevo = vm::abrirNuevo, onAbrirEditar = vm::abrirEditar, onCerrarForm = vm::cerrarForm,
                    onGuardar = vm::guardar, onEliminar = vm::eliminar,
                    compraInsumo = compra, onAbrirCompra = vm::abrirCompra, onCerrarCompra = vm::cerrarCompra,
                    onRegistrarCompra = vm::registrarCompra,
                )
            }
            Screen.ProduccionUnidades -> {
                val vm = koinViewModel<ProduccionUnidadesViewModel>()
                val unidades by vm.unidades.collectAsState()
                val form by vm.formVisible.collectAsState()
                val error by vm.error.collectAsState()
                val aviso by vm.avisoLista.collectAsState()
                ProduccionUnidadesScreen(
                    unidades = unidades, formVisible = form, error = error, avisoLista = aviso,
                    onAbrirNuevo = vm::abrirNuevo, onAbrirEditar = vm::abrirEditar, onCerrarForm = vm::cerrarForm,
                    onGuardar = vm::guardar, onEliminar = vm::eliminar, onCerrarAviso = vm::cerrarAviso,
                )
            }
            Screen.ProduccionProductos -> {
                val vm = koinViewModel<ProduccionProductosViewModel>()
                val productos by vm.productos.collectAsState()
                val busqueda by vm.busqueda.collectAsState()
                ProduccionProductosScreen(productos = productos, busqueda = busqueda, onBusquedaChange = vm::onBusquedaChange)
            }
            Screen.ProduccionCompras -> {
                val vm = koinViewModel<ProduccionComprasViewModel>()
                val historial by vm.historial.collectAsState()
                val insumos by vm.insumos.collectAsState()
                val form by vm.formVisible.collectAsState()
                val error by vm.error.collectAsState()
                ProduccionComprasScreen(
                    historial = historial, insumos = insumos, formVisible = form, error = error,
                    onSeleccionarPeriodo = vm::seleccionarPeriodo, onSeleccionarInsumo = vm::seleccionarInsumo,
                    onAbrirNuevo = vm::abrirNuevo, onCerrarForm = vm::cerrarForm,
                    onRegistrar = vm::registrar,
                )
            }
            Screen.TomarAsistencia -> TomarAsistenciaHost(usuario)
            Screen.MiAsistencia -> MiAsistenciaHost(usuario)
            Screen.ProduccionInventario -> {
                val vm = koinViewModel<ProduccionInventarioViewModel>()
                val items by vm.items.collectAsState()
                ProduccionInventarioScreen(items = items)
            }
            else -> Unit
        }

    }
    if (soloContenido) contenido() else {
    RootScaffold(
        usuario = usuario,
        rolEtiqueta = "Producción",
        tema = TemaProduccion,
        estadoSync = rootState.estadoSync,
        pendientes = rootState.pendientes,
        onCerrarSesion = onCerrarSesion,
        tituloPantalla = titulo,
        subtituloPantalla = subtitulo,
        bottomNavItems = listOf(
            BottomNavItem("Panel", Icons.Filled.Dashboard),
            BottomNavItem("Producción", Icons.Filled.Science),
            BottomNavItem("Recetas", Icons.AutoMirrored.Filled.MenuBook),
            BottomNavItem("Insumos", Icons.Filled.ShoppingCart),
        ),
        bottomNavSeleccionado = when (pantalla) {
            Screen.ProduccionDashboard -> 0
            Screen.ProduccionRegistros -> 1
            Screen.ProduccionRecetas -> 2
            Screen.ProduccionInsumos -> 3
            else -> -1
        },
        onBottomNavSeleccionar = { index ->
            pantalla = when (index) {
                0 -> Screen.ProduccionDashboard
                1 -> Screen.ProduccionRegistros
                2 -> Screen.ProduccionRecetas
                3 -> Screen.ProduccionInsumos
                else -> pantalla
            }
        },
        masSheetContent = { onDismiss ->
            MasSheet(titulo = "Módulos de producción", items = modulos, onDismiss = onDismiss)
        },
        content = contenido,
    )
    }
}
