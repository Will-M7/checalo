package com.example.ecolacteoshuata.presentation.proveedor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.SecondaryButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ecolacteoshuata.core.theme.TemaProveedor
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.RootScaffold
import com.example.ecolacteoshuata.presentation.navigation.Screen
import com.example.ecolacteoshuata.presentation.proveedor.comunicados.ProveedorComunicadosScreen
import com.example.ecolacteoshuata.presentation.proveedor.comunicados.ProveedorComunicadosViewModel
import com.example.ecolacteoshuata.presentation.proveedor.dashboard.ProveedorDashboardScreen
import com.example.ecolacteoshuata.presentation.proveedor.dashboard.ProveedorDashboardViewModel
import com.example.ecolacteoshuata.presentation.proveedor.entregas.ProveedorEntregasScreen
import com.example.ecolacteoshuata.presentation.proveedor.entregas.ProveedorEntregasViewModel
import com.example.ecolacteoshuata.presentation.proveedor.laboratorio.ProveedorLaboratorioScreen
import com.example.ecolacteoshuata.presentation.proveedor.laboratorio.ProveedorLaboratorioViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProveedorRootScreen(usuario: Usuario, onCerrarSesion: () -> Unit, soloContenido: Boolean = false, estadoPantalla: androidx.compose.runtime.MutableState<Screen>? = null, modulosExternos: List<ModuloItem>? = null) {
    val rootViewModel = koinViewModel<ProveedorRootViewModel>(key = usuario.id) { parametersOf(usuario.id) }
    val rootState by rootViewModel.state.collectAsState()

    var pantalla by (estadoPantalla ?: remember { mutableStateOf<Screen>(Screen.ProveedorDashboard) })

    val modulosDelRol = listOf(
        ModuloItem("Mis entregas", Icons.Filled.LocalShipping, "Litros y pagos por periodo") { pantalla = Screen.ProveedorEntregas },
        ModuloItem("Laboratorio", Icons.Filled.Science, "Calidad de mi leche") { pantalla = Screen.ProveedorLaboratorio },
        ModuloItem("Comunicados", Icons.Filled.Campaign, "Avisos de la asociación") { pantalla = Screen.ProveedorComunicados },
    )

    val modulos = modulosExternos ?: modulosDelRol

    val (titulo, subtitulo) = when (pantalla) {
        Screen.ProveedorDashboard -> "Panel del proveedor" to "Bienvenido, ${usuario.nombreCompleto}"
        Screen.ProveedorEntregas -> "Mis entregas" to "Leche entregada y estado de pago"
        Screen.ProveedorLaboratorio -> "Resultados de laboratorio" to "Calidad de mi leche"
        Screen.ProveedorComunicados -> "Comunicados y asesoramiento" to "Avisos publicados por la planta"
        else -> "Ecolácteos Huata" to null
    }

    // Solo cuatro módulos: caben todos en la barra inferior, sin hoja "Más".
    val contenido: @Composable () -> Unit = {
        val proveedor = rootState.proveedor
        if (proveedor == null && rootState.cargado) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val descargando = rootState.estadoSync == com.example.ecolacteoshuata.domain.model.EstadoSincronizacion.SINCRONIZANDO
                val tituloEstado = when {
                    descargando -> "Descargando tu información"
                    rootState.requiereSesion -> "Confirma tu sesión con Laravel"
                    rootState.error != null -> "No se pudo completar la descarga"
                    !rootState.descargado -> "Perfil pendiente de descarga"
                    else -> "Esta cuenta no tiene perfil de proveedor"
                }
                val detalle = when {
                    descargando -> "Los datos aparecerán automáticamente al terminar."
                    rootState.error != null -> rootState.error!!
                    rootState.requiereSesion -> "Abre Conexión y sincronización para confirmar tu sesión y descargar los datos."
                    !rootState.descargado -> "Sincroniza para consultar el perfil registrado en Laravel."
                    else -> "Laravel no devolvió un perfil de proveedor para esta cuenta. Pide al administrador revisar la asociación del usuario y sus pantallas autorizadas."
                }
                EmptyState(tituloEstado, detalle)
                if (descargando) CircularProgressIndicator(Modifier.padding(top = 12.dp))
                else SecondaryButton("Sincronizar y reintentar", onClick = rootViewModel::cargarProveedor, modifier = Modifier.padding(top = 12.dp))
            }
        } else if (proveedor == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            when (pantalla) {
                Screen.ProveedorDashboard -> {
                    val vm = koinViewModel<ProveedorDashboardViewModel>(key = proveedor.id) { parametersOf(proveedor.id) }
                    val state by vm.state.collectAsState()
                    ProveedorDashboardScreen(state = state, modulos = modulos)
                }
                Screen.ProveedorEntregas -> {
                    val vm = koinViewModel<ProveedorEntregasViewModel>(key = "entregas-${proveedor.id}") { parametersOf(proveedor.id) }
                    val state by vm.state.collectAsState()
                    ProveedorEntregasScreen(state = state, onPeriodo = vm::onPeriodo, onFecha = vm::onFecha, onVerDetalle = vm::verDetalle, onCerrarDetalle = vm::cerrarDetalle)
                }
                Screen.ProveedorLaboratorio -> {
                    val vm = koinViewModel<ProveedorLaboratorioViewModel>(key = "laboratorio-${proveedor.id}") { parametersOf(proveedor.id) }
                    val state by vm.state.collectAsState()
                    ProveedorLaboratorioScreen(state = state, onPeriodo = vm::onPeriodo, onFecha = vm::onFecha)
                }
                Screen.ProveedorComunicados -> {
                    val vm = koinViewModel<ProveedorComunicadosViewModel>()
                    val comunicados by vm.comunicados.collectAsState()
                    val filtroTipo by vm.filtroTipo.collectAsState()
                    ProveedorComunicadosScreen(comunicados = comunicados, filtroTipo = filtroTipo, onFiltrar = vm::filtrar)
                }
                else -> Unit
            }
        }

    }
    if (soloContenido) contenido() else {
    RootScaffold(
        usuario = usuario,
        rolEtiqueta = "Proveedor",
        tema = TemaProveedor,
        estadoSync = rootState.estadoSync,
        pendientes = rootState.pendientes,
        onCerrarSesion = onCerrarSesion,
        tituloPantalla = titulo,
        subtituloPantalla = subtitulo,
        bottomNavItems = listOf(
            BottomNavItem("Panel", Icons.Filled.Dashboard),
            BottomNavItem("Entregas", Icons.Filled.LocalShipping),
            BottomNavItem("Laboratorio", Icons.Filled.Science),
            BottomNavItem("Avisos", Icons.Filled.Campaign),
        ),
        bottomNavSeleccionado = when (pantalla) {
            Screen.ProveedorDashboard -> 0
            Screen.ProveedorEntregas -> 1
            Screen.ProveedorLaboratorio -> 2
            Screen.ProveedorComunicados -> 3
            else -> -1
        },
        onBottomNavSeleccionar = { index ->
            pantalla = when (index) {
                0 -> Screen.ProveedorDashboard
                1 -> Screen.ProveedorEntregas
                2 -> Screen.ProveedorLaboratorio
                3 -> Screen.ProveedorComunicados
                else -> pantalla
            }
        },
        content = contenido,
    )
    }
}
