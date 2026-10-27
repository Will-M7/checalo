package com.example.ecolacteoshuata

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.presentation.common.LocalSelectorRol
import com.example.ecolacteoshuata.presentation.common.SelectorRol
import androidx.compose.ui.Modifier
import com.example.ecolacteoshuata.core.theme.EcolacteosHuataTheme
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.presentation.acopiador.AcopiadorRootScreen
import com.example.ecolacteoshuata.presentation.admin.AdminRootScreen
import com.example.ecolacteoshuata.presentation.login.LoginScreen
import com.example.ecolacteoshuata.presentation.login.LoginViewModel
import com.example.ecolacteoshuata.presentation.login.PrimeraDescargaScreen
import com.example.ecolacteoshuata.presentation.login.PrimeraDescargaViewModel
import com.example.ecolacteoshuata.presentation.planta.PlantaRootScreen
import com.example.ecolacteoshuata.presentation.produccion.ProduccionRootScreen
import com.example.ecolacteoshuata.presentation.proveedor.ProveedorRootScreen
import com.example.ecolacteoshuata.presentation.vendedor.VendedorRootScreen
import com.example.ecolacteoshuata.domain.usecase.LogoutUseCase
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    EcolacteosHuataTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            val loginViewModel = koinViewModel<LoginViewModel>()
            val logoutUseCase = koinInject<LogoutUseCase>()
            val state by loginViewModel.state.collectAsState()

            val cerrarSesion: () -> Unit = {
                logoutUseCase()
                loginViewModel.navegacionConsumida()
            }

            val descargaViewModel = koinViewModel<PrimeraDescargaViewModel>()
            val descarga by descargaViewModel.state.collectAsState()

            if (descarga.visible) {
                PrimeraDescargaScreen(
                    state = descarga,
                    onReintentar = descargaViewModel::reintentar,
                    onContinuar = descargaViewModel::continuar,
                    onOmitir = descargaViewModel::omitir,
                )
            } else if (state.usuarioLogueado == null) {
                LoginScreen(
                    state = state,
                    onUsuarioChange = loginViewModel::onUsuarioChange,
                    onPasswordChange = loginViewModel::onPasswordChange,
                    onIngresar = loginViewModel::ingresar,
                )
            } else {
                val repositorioSesion = koinInject<com.example.ecolacteoshuata.domain.repository.SessionRepository>()
                val sesionSincronizada by repositorioSesion.usuarioActual.collectAsState()
                val logueado = sesionSincronizada ?: state.usuarioLogueado!!
                // El usuario tal como está en el teléfono: si el administrador le asigna (o quita)
                // un rol y llega con el sync, se refleja sin volver a iniciar sesión.
                val usuarioRepository = koinInject<UsuarioRepository>()
                val enVivo by remember(logueado.id) { usuarioRepository.observeById(logueado.id) }.collectAsState(initial = logueado)
                val usuario = enVivo ?: logueado
                val activas = usuario.autorizacion?.roles.orEmpty().filter { it.activo }
                val roles = activas.filterNot { it.auxiliar }.ifEmpty { activas }
                var rolElegido by remember(logueado.id) { mutableStateOf(roles.firstOrNull()?.id) }
                val rolActivo = roles.firstOrNull { it.id == rolElegido } ?: roles.firstOrNull()
                if (rolActivo == null) {
                    com.example.ecolacteoshuata.presentation.navigation.RootAutorizado(usuario,
                        com.example.ecolacteoshuata.domain.model.RolAsignado("sin_asignaciones", "Funciones"), cerrarSesion)
                } else {
                    CompositionLocalProvider(
                        LocalSelectorRol provides SelectorRol(roles, rolActivo) { rolElegido = it.id },
                    ) {
                        com.example.ecolacteoshuata.presentation.navigation.RootAutorizado(usuario, rolActivo, cerrarSesion)
                    }
                }
            }
        }
    }
}
