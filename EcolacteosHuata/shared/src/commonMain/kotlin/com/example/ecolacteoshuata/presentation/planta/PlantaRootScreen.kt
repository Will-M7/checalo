package com.example.ecolacteoshuata.presentation.planta

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ecolacteoshuata.core.theme.TemaPlanta
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.presentation.admin.rutas.RutasHost
import com.example.ecolacteoshuata.presentation.common.MasSheet
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.RootScaffold
import com.example.ecolacteoshuata.presentation.common.asistencia.TomarAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloTomarAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.rememberPuedeTomarAsistencia
import com.example.ecolacteoshuata.presentation.navigation.Screen
import com.example.ecolacteoshuata.presentation.planta.comunicados.PlantaComunicadosScreen
import com.example.ecolacteoshuata.presentation.planta.comunicados.PlantaComunicadosViewModel
import com.example.ecolacteoshuata.presentation.planta.dashboard.PlantaDashboardScreen
import com.example.ecolacteoshuata.presentation.planta.dashboard.PlantaDashboardViewModel
import com.example.ecolacteoshuata.presentation.planta.laboratorio.PlantaLaboratorioRegistroScreen
import com.example.ecolacteoshuata.presentation.planta.laboratorio.PlantaLaboratorioRegistroViewModel
import com.example.ecolacteoshuata.presentation.planta.laboratorio.PlantaLaboratorioScreen
import com.example.ecolacteoshuata.presentation.planta.laboratorio.PlantaLaboratorioViewModel
import com.example.ecolacteoshuata.presentation.planta.pagos.PlantaPagosScreen
import com.example.ecolacteoshuata.presentation.planta.pagos.PlantaPagosViewModel
import com.example.ecolacteoshuata.presentation.planta.recepcion.PlantaRecepcionScreen
import com.example.ecolacteoshuata.presentation.planta.recepcion.PlantaRecepcionViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlantaRootScreen(usuario: Usuario, onCerrarSesion: () -> Unit, soloContenido: Boolean = false, estadoPantalla: androidx.compose.runtime.MutableState<Screen>? = null, modulosExternos: List<ModuloItem>? = null, cabecera: ((String, String?, Boolean, () -> Unit) -> Unit)? = null) {
    val rootViewModel = koinViewModel<PlantaRootViewModel>()
    val rootState by rootViewModel.state.collectAsState()

    var pantalla by (estadoPantalla ?: remember { mutableStateOf<Screen>(Screen.PlantaDashboard) })
    // Registro de laboratorio abierto: (proveedor, resultado que se edita o null si es nuevo).
    var laboratorio by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val ir: (Screen) -> Unit = { laboratorio = null; pantalla = it }
    val puedeTomarAsistencia = rememberPuedeTomarAsistencia(usuario)
    val modulosDelRol = buildList {
        add(ModuloItem("Laboratorio", Icons.Filled.Science, "Foto de resultados + OCR") { ir(Screen.PlantaLaboratorio) })
        add(ModuloItem("Entregas de acopiadores", Icons.Filled.LocalShipping, "Recepción de leche") { ir(Screen.PlantaRecepcion) })
        add(ModuloItem("Pago a proveedores", Icons.Filled.AttachMoney, "Pagos por periodo") { ir(Screen.PlantaPagos) })
        add(ModuloItem("Rutas", Icons.Filled.Route, "Rutas, proveedores y recorrido") { ir(Screen.PlantaRutas) })
        if (puedeTomarAsistencia) add(moduloTomarAsistencia { ir(Screen.TomarAsistencia) })
        add(ModuloItem("Comunicados", Icons.Filled.Campaign, "Avisos para proveedores") { ir(Screen.PlantaComunicados) })
    }

    val modulos = modulosExternos ?: modulosDelRol

    val (titulo, subtitulo) = when {
        laboratorio?.second != null -> "Editar resultado" to "Corrige los datos y guarda"
        laboratorio != null -> "Nuevo resultado" to "Toma la foto y revisa los resultados"
        pantalla == Screen.PlantaDashboard -> "Panel de planta" to "Resumen del día"
        pantalla == Screen.PlantaRecepcion -> "Entregas de acopiadores" to "Recepción de leche"
        pantalla == Screen.PlantaLaboratorio -> "Laboratorio" to "Resultados de calidad de leche"
        pantalla == Screen.PlantaRutas -> "Rutas" to "Proveedores y recorrido del camioncito"
        pantalla == Screen.PlantaPagos -> "Pago a proveedores" to "Por periodo"
        pantalla == Screen.PlantaComunicados -> "Comunicados" to "Visibles para proveedores"
        pantalla == Screen.TomarAsistencia -> "Tomar asistencia" to "Asistencia de los trabajadores"
        else -> "Ecolácteos Huata" to null
    }

    androidx.compose.runtime.SideEffect { cabecera?.invoke(titulo, subtitulo, laboratorio != null) { laboratorio = null } }

    val contenido: @Composable () -> Unit = {
        val enLab = laboratorio
        if (enLab != null) {
            val (proveedorEnLab, resultadoEnLab) = enLab
            val vm = koinViewModel<PlantaLaboratorioRegistroViewModel>(key = "lab-$proveedorEnLab-${resultadoEnLab.orEmpty()}") {
                parametersOf(proveedorEnLab, resultadoEnLab.orEmpty())
            }
            val state by vm.state.collectAsState()
            // Al guardar una edición se vuelve a la lista, como al cerrar el modal en Laravel.
            LaunchedEffect(state.edicionGuardada) {
                if (state.edicionGuardada) {
                    vm.edicionCerrada()
                    laboratorio = null
                }
            }
            PlantaLaboratorioRegistroScreen(
                state = state,
                onFechaChange = vm::onFechaChange,
                onTurnoChange = vm::onTurnoChange,
                onLitrosChange = vm::onLitrosChange,
                onAnalisisChange = vm::onAnalisisChange,
                onObservacionesChange = vm::onObservacionesChange,
                onConformeChange = vm::onConformeChange,
                onOcrLeido = vm::aplicarOcr,
                onRegistrar = vm::registrar,
                onDesbloquear = vm::desbloquearProveedor,
                onCerrarMensaje = vm::confirmacionConsumida,
            )
        } else {
            when (pantalla) {
                Screen.PlantaDashboard -> {
                    val vm = koinViewModel<PlantaDashboardViewModel>()
                    val state by vm.state.collectAsState()
                    PlantaDashboardScreen(state = state, modulos = modulos)
                }
                Screen.PlantaRecepcion -> {
                    val vm = koinViewModel<PlantaRecepcionViewModel>()
                    val state by vm.state.collectAsState()
                    PlantaRecepcionScreen(state = state)
                }
                Screen.PlantaLaboratorio -> {
                    val vm = koinViewModel<PlantaLaboratorioViewModel>()
                    val state by vm.state.collectAsState()
                    PlantaLaboratorioScreen(
                        state = state,
                        onNuevo = vm::nuevoResultado,
                        onCerrarEleccion = vm::cerrarEleccion,
                        onElegirProveedor = { vm.cerrarEleccion(); laboratorio = it.id to null },
                        onVerDetalle = vm::verDetalle,
                        onCerrarDetalle = vm::cerrarDetalle,
                        onEditar = { laboratorio = it.resultado.proveedorId to it.resultado.id },
                        onPdf = vm::descargarPdf,
                        onPedirEnviar = vm::pedirEnviarProduccion,
                        onCancelarEnviar = vm::cancelarEnviar,
                        onEnviar = vm::enviarProduccion,
                        onDesbloquear = vm::desbloquear,
                        onConsumirMensaje = vm::consumirMensaje,
                    )
                }
                Screen.PlantaRutas -> RutasHost()
                Screen.PlantaPagos -> {
                    val vm = koinViewModel<PlantaPagosViewModel>()
                    val state by vm.state.collectAsState()
                    PlantaPagosScreen(state = state, onPeriodoChange = vm::seleccionarPeriodo, onPagar = { fila, metodo -> vm.pagar(fila.proveedor, metodo) })
                }
                Screen.TomarAsistencia -> TomarAsistenciaHost(usuario)
                Screen.PlantaComunicados -> {
                    val vm = koinViewModel<PlantaComunicadosViewModel>(key = usuario.id) { parametersOf(usuario.id) }
                    val comunicados by vm.comunicados.collectAsState()
                    val mostrarForm by vm.mostrarForm.collectAsState()
                    val error by vm.error.collectAsState()
                    PlantaComunicadosScreen(
                        comunicados = comunicados, mostrarForm = mostrarForm, error = error,
                        onAbrirForm = vm::abrirForm, onCerrarForm = vm::cerrarForm,
                        onPublicar = vm::publicar, onEliminar = vm::eliminar,
                    )
                }
                else -> Unit
            }
        }

    }
    if (soloContenido) contenido() else {
    RootScaffold(
        usuario = usuario,
        rolEtiqueta = "Personal de planta",
        tema = TemaPlanta,
        estadoSync = rootState.estadoSync,
        pendientes = rootState.pendientes,
        onCerrarSesion = onCerrarSesion,
        tituloPantalla = titulo,
        subtituloPantalla = subtitulo,
        showBack = laboratorio != null,
        onBack = { laboratorio = null },
        bottomNavItems = listOf(
            BottomNavItem("Panel", Icons.Filled.Dashboard),
            BottomNavItem("Laboratorio", Icons.Filled.Science),
            BottomNavItem("Entregas", Icons.Filled.LocalShipping),
            BottomNavItem("Rutas", Icons.Filled.Route),
        ),
        bottomNavSeleccionado = when (pantalla) {
            Screen.PlantaDashboard -> 0
            Screen.PlantaLaboratorio -> 1
            Screen.PlantaRecepcion -> 2
            Screen.PlantaRutas -> 3
            else -> -1
        },
        onBottomNavSeleccionar = { index ->
            when (index) {
                0 -> ir(Screen.PlantaDashboard)
                1 -> ir(Screen.PlantaLaboratorio)
                2 -> ir(Screen.PlantaRecepcion)
                3 -> ir(Screen.PlantaRutas)
            }
        },
        masSheetContent = { onDismiss ->
            MasSheet(titulo = "Módulos de planta", items = modulos, onDismiss = onDismiss)
        },
        content = contenido,
    )
    }
}
