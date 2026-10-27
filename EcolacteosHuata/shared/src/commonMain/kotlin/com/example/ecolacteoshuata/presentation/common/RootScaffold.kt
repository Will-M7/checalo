package com.example.ecolacteoshuata.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ecolacteoshuata.core.theme.FondoApp
import com.example.ecolacteoshuata.core.theme.TemaRol
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.core.ui.FloatingNavBar
import com.example.ecolacteoshuata.core.ui.HeroHeader
import com.example.ecolacteoshuata.core.ui.ProveerTemaRol
import com.example.ecolacteoshuata.core.ui.ScreenEntrance
import com.example.ecolacteoshuata.presentation.common.conexion.ConexionSyncSheet
import com.example.ecolacteoshuata.presentation.common.conexion.AvisoConexionHost
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.Usuario

/**
 * Esqueleto común de todos los roles: cabecera con la identidad del rol, contenido animado al
 * cambiar de módulo y barra inferior flotante. Si hay [masSheetContent] se agrega el botón
 * "Más" al final de la barra para abrir el resto de módulos.
 */
@Composable
fun RootScaffold(
    usuario: Usuario,
    rolEtiqueta: String,
    tema: TemaRol,
    estadoSync: EstadoSincronizacion,
    pendientes: Int,
    onCerrarSesion: () -> Unit,
    tituloPantalla: String,
    subtituloPantalla: String? = null,
    showBack: Boolean = false,
    expandirCabecera: Boolean? = null,
    onBack: (() -> Unit)? = null,
    bottomNavItems: List<BottomNavItem>,
    bottomNavSeleccionado: Int,
    onBottomNavSeleccionar: (Int) -> Unit,
    masSheetContent: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // La campana: notificaciones de Laravel (comunicados y asesoramiento) sincronizadas.
    val notificacionesVm = koinViewModel<NotificacionesViewModel>()
    val notificaciones by notificacionesVm.notificaciones.collectAsState()
    val noLeidas by notificacionesVm.noLeidas.collectAsState()
    var mostrarNotificaciones by remember { mutableStateOf(false) }
    // Varios roles: el chip del encabezado abre "Cambiar de rol".
    val selectorRol = LocalSelectorRol.current
    var mostrarRoles by remember { mutableStateOf(false) }
    var mostrarPerfil by remember { mutableStateOf(false) }
    var mostrarMas by remember { mutableStateOf(false) }
    var mostrarConexion by remember { mutableStateOf(false) }

    ProveerTemaRol(tema) {
        Column(modifier = Modifier.fillMaxSize().background(FondoApp)) {
            HeroHeader(
                usuario = usuario,
                rolEtiqueta = rolEtiqueta,
                estadoSync = estadoSync,
                pendientes = pendientes,
                notificacionesCount = noLeidas,
                titulo = tituloPantalla,
                subtitulo = subtituloPantalla,
                expandido = (expandirCabecera ?: (bottomNavSeleccionado == 0)) && !showBack,
                showBack = showBack,
                onBack = onBack,
                onPerfilClick = { mostrarPerfil = true },
                onNotificacionesClick = { mostrarNotificaciones = true },
                onLogoutClick = onCerrarSesion,
                onConexionClick = { mostrarConexion = true },
                otrosRoles = (selectorRol?.roles?.size ?: 1) - 1,
                onCambiarRol = { mostrarRoles = true },
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                key(tituloPantalla) {
                    ScreenEntrance(Modifier.fillMaxSize()) { content() }
                }
                // Avisos de conexión (sin Internet, reconectando, restablecida...) sobre el contenido.
                AvisoConexionHost(Modifier.align(Alignment.TopCenter))
            }
            val itemsBarra = if (masSheetContent != null) bottomNavItems + BottomNavItem("Más", Icons.Filled.Apps) else bottomNavItems
            FloatingNavBar(
                items = itemsBarra,
                seleccionado = if (mostrarMas) bottomNavItems.size else bottomNavSeleccionado,
                onSeleccionar = { index ->
                    if (index == bottomNavItems.size) mostrarMas = true else onBottomNavSeleccionar(index)
                },
            )
        }

        if (mostrarRoles && selectorRol != null) {
            CambiarRolSheet(selectorRol, onDismiss = { mostrarRoles = false })
        }
        if (mostrarNotificaciones) {
            NotificacionesSheet(
                notificaciones = notificaciones,
                noLeidas = noLeidas,
                onMarcarLeida = notificacionesVm::marcarLeida,
                onMarcarTodas = notificacionesVm::marcarTodasLeidas,
                onDismiss = { mostrarNotificaciones = false },
            )
        }
        if (mostrarPerfil) {
            PerfilSheet(usuario = usuario, onDismiss = { mostrarPerfil = false })
        }
        if (mostrarConexion) {
            ConexionSyncSheet(onDismiss = { mostrarConexion = false })
        }
        if (mostrarMas && masSheetContent != null) {
            masSheetContent { mostrarMas = false }
        }
    }
}
