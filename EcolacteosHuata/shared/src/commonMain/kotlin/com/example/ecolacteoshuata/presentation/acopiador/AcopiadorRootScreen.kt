package com.example.ecolacteoshuata.presentation.acopiador

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ecolacteoshuata.core.theme.TemaAcopiador
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.presentation.acopiador.dashboard.AcopiadorDashboardScreen
import com.example.ecolacteoshuata.presentation.acopiador.dashboard.AcopiadorDashboardViewModel
import com.example.ecolacteoshuata.presentation.acopiador.entrega.AcopiadorEntregaScreen
import com.example.ecolacteoshuata.presentation.acopiador.entrega.AcopiadorEntregaViewModel
import com.example.ecolacteoshuata.presentation.acopiador.inventario.AcopiadorInventarioScreen
import com.example.ecolacteoshuata.presentation.acopiador.inventario.AcopiadorInventarioViewModel
import com.example.ecolacteoshuata.presentation.acopiador.recojo.AcopiadorRecojoScreen
import com.example.ecolacteoshuata.presentation.acopiador.recojo.AcopiadorRecojoViewModel
import com.example.ecolacteoshuata.presentation.common.MasSheet
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.RootScaffold
import com.example.ecolacteoshuata.presentation.common.asistencia.TomarAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.MiAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloMiAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloTomarAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.rememberPuedeTomarAsistencia
import com.example.ecolacteoshuata.presentation.common.mapa.MapaRutaHost
import com.example.ecolacteoshuata.presentation.navigation.Screen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AcopiadorRootScreen(usuario: Usuario, onCerrarSesion: () -> Unit, soloContenido: Boolean = false, estadoPantalla: androidx.compose.runtime.MutableState<Screen>? = null, modulosExternos: List<ModuloItem>? = null) {
    val rootViewModel = koinViewModel<AcopiadorRootViewModel>(key = usuario.id) { parametersOf(usuario.id) }
    val rootState by rootViewModel.state.collectAsState()

    var pantalla by (estadoPantalla ?: remember { mutableStateOf<Screen>(Screen.AcopiadorDashboard) })

    val puedeTomarAsistencia = rememberPuedeTomarAsistencia(usuario)
    val modulosDelRol = listOfNotNull(
        ModuloItem("Recojo de ruta", Icons.Filled.LocalShipping, "Registra la leche de cada proveedor") { pantalla = Screen.AcopiadorRecojo },
        ModuloItem("Mapa de recojo", Icons.Filled.Map, "Sigue el camino y tus paradas") { pantalla = Screen.AcopiadorMapaRecojo },
        ModuloItem("Entrega a planta", Icons.Filled.Factory, "Cierra el turno en planta") { pantalla = Screen.AcopiadorEntrega },
        if (puedeTomarAsistencia) moduloTomarAsistencia { pantalla = Screen.TomarAsistencia } else null,
        moduloMiAsistencia { pantalla = Screen.MiAsistencia },
        ModuloItem("Inventario", Icons.Filled.Inventory, "Leche pendiente de entregar") { pantalla = Screen.AcopiadorInventario },
    )

    val modulos = modulosExternos ?: modulosDelRol

    val (titulo, subtitulo) = when (pantalla) {
        Screen.AcopiadorDashboard -> "Panel del acopiador" to "Tu ruta de hoy"
        Screen.AcopiadorRecojo -> "Recojo de ruta" to "Selecciona un proveedor"
        Screen.AcopiadorMapaRecojo -> "Mapa de recojo" to "Tus paradas y el camino"
        Screen.AcopiadorEntrega -> "Entrega a planta" to "Cierre de turno"
        Screen.AcopiadorInventario -> "Inventario" to "Leche pendiente de entregar"
        Screen.TomarAsistencia -> "Tomar asistencia" to "Asistencia de los trabajadores"
        Screen.MiAsistencia -> "Asistencia" to "Marca tu asistencia y justifica faltas"
        else -> "Ecolácteos Huata" to null
    }

    val contenido: @Composable () -> Unit = {
        val acopiadorId = rootState.acopiadorId
        if (acopiadorId == null && rootState.cargado) {
            com.example.ecolacteoshuata.core.ui.EmptyState("Perfil de acopiador pendiente", "Completa el perfil en Usuarios y sincroniza con Laravel.")
        } else if (acopiadorId == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            when (pantalla) {
                Screen.AcopiadorDashboard -> {
                    val vm = koinViewModel<AcopiadorDashboardViewModel>(key = acopiadorId) { parametersOf(acopiadorId) }
                    val state by vm.state.collectAsState()
                    AcopiadorDashboardScreen(state = state, modulos = modulos)
                }
                Screen.AcopiadorRecojo -> {
                    val vm = koinViewModel<AcopiadorRecojoViewModel>(key = acopiadorId) { parametersOf(acopiadorId) }
                    val state by vm.state.collectAsState()
                    AcopiadorRecojoScreen(
                        state = state,
                        onSeleccionarTurno = vm::seleccionarTurno,
                        onAbrirFormulario = vm::abrirFormulario,
                        onCerrarFormulario = vm::cerrarFormulario,
                        onTurnoFormularioChange = vm::onTurnoFormularioChange,
                        onLitrosChange = vm::onLitrosChange,
                        onEstadoChange = vm::onEstadoChange,
                        onEstadoOtroChange = vm::onEstadoOtroChange,
                        onGuardar = vm::guardar,
                    )
                }
                Screen.AcopiadorMapaRecojo -> MapaRutaHost(
                    editable = false,
                    acopiadorId = acopiadorId,
                    onIrARecojo = { pantalla = Screen.AcopiadorRecojo },
                )
                Screen.AcopiadorEntrega -> {
                    val vm = koinViewModel<AcopiadorEntregaViewModel>(key = acopiadorId) { parametersOf(acopiadorId) }
                    val state by vm.state.collectAsState()
                    AcopiadorEntregaScreen(
                        state = state,
                        onSeleccionarTurno = vm::seleccionarTurno,
                        onPedirConfirmacion = vm::pedirConfirmacion,
                        onEntregar = vm::entregar,
                        onConfirmacionConsumida = vm::confirmacionConsumida,
                    )
                }
                Screen.TomarAsistencia -> TomarAsistenciaHost(usuario)
                Screen.MiAsistencia -> MiAsistenciaHost(usuario)
                Screen.AcopiadorInventario -> {
                    val vm = koinViewModel<AcopiadorInventarioViewModel>(key = acopiadorId) { parametersOf(acopiadorId) }
                    val pendientes by vm.pendientes.collectAsState()
                    val turnos by vm.turnos.collectAsState()
                    AcopiadorInventarioScreen(pendientes = pendientes, turnos = turnos)
                }
                else -> Unit
            }
        }

    }
    if (soloContenido) contenido() else {
    RootScaffold(
        usuario = usuario,
        rolEtiqueta = "Acopiador",
        tema = TemaAcopiador,
        estadoSync = rootState.estadoSync,
        pendientes = rootState.pendientes,
        onCerrarSesion = onCerrarSesion,
        tituloPantalla = titulo,
        subtituloPantalla = subtitulo,
        bottomNavItems = listOf(
            BottomNavItem("Panel", Icons.Filled.Dashboard),
            BottomNavItem("Recojo", Icons.Filled.LocalShipping),
            BottomNavItem("Mapa", Icons.Filled.Map),
            BottomNavItem("Entrega", Icons.Filled.Factory),
        ),
        bottomNavSeleccionado = when (pantalla) {
            Screen.AcopiadorDashboard -> 0
            Screen.AcopiadorRecojo -> 1
            Screen.AcopiadorMapaRecojo -> 2
            Screen.AcopiadorEntrega -> 3
            else -> -1
        },
        onBottomNavSeleccionar = { index ->
            pantalla = when (index) {
                0 -> Screen.AcopiadorDashboard
                1 -> Screen.AcopiadorRecojo
                2 -> Screen.AcopiadorMapaRecojo
                3 -> Screen.AcopiadorEntrega
                else -> pantalla
            }
        },
        masSheetContent = { onDismiss ->
            MasSheet(titulo = "Módulos del acopiador", items = modulos, onDismiss = onDismiss)
        },
        content = contenido,
    )
    }
}
