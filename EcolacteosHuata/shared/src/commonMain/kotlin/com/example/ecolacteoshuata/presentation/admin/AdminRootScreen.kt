package com.example.ecolacteoshuata.presentation.admin

import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.example.ecolacteoshuata.core.theme.TemaAdmin
import com.example.ecolacteoshuata.core.ui.BottomNavItem
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.presentation.admin.config.AdminConfiguracionScreen
import com.example.ecolacteoshuata.presentation.admin.config.AdminConfiguracionViewModel
import com.example.ecolacteoshuata.presentation.admin.dashboard.AdminDashboardScreen
import com.example.ecolacteoshuata.presentation.admin.dashboard.AdminDashboardViewModel
import com.example.ecolacteoshuata.presentation.admin.descuentos.AdminDescuentosScreen
import com.example.ecolacteoshuata.presentation.admin.descuentos.AdminDescuentosViewModel
import com.example.ecolacteoshuata.presentation.admin.precios.AdminPreciosScreen
import com.example.ecolacteoshuata.presentation.admin.precios.AdminPreciosViewModel
import com.example.ecolacteoshuata.presentation.admin.roles.AdminAsignarRolesScreen
import com.example.ecolacteoshuata.presentation.admin.roles.AdminAsignarRolesViewModel
import com.example.ecolacteoshuata.presentation.admin.rutas.RutasHost
import com.example.ecolacteoshuata.presentation.admin.turnos.AdminTurnosScreen
import com.example.ecolacteoshuata.presentation.admin.turnos.AdminTurnosViewModel
import com.example.ecolacteoshuata.presentation.admin.usuarios.AdminUsuarioFormSheet
import com.example.ecolacteoshuata.presentation.admin.usuarios.AdminUsuariosScreen
import com.example.ecolacteoshuata.presentation.admin.usuarios.AdminUsuariosViewModel
import com.example.ecolacteoshuata.presentation.common.MasSheet
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.RootScaffold
import com.example.ecolacteoshuata.presentation.common.asistencia.TomarAsistenciaHost
import com.example.ecolacteoshuata.presentation.common.asistencia.moduloTomarAsistencia
import com.example.ecolacteoshuata.presentation.common.asistencia.rememberPuedeTomarAsistencia
import com.example.ecolacteoshuata.presentation.navigation.Screen
import com.example.ecolacteoshuata.presentation.planta.comunicados.PlantaComunicadosScreen
import com.example.ecolacteoshuata.presentation.planta.comunicados.PlantaComunicadosViewModel
import com.example.ecolacteoshuata.presentation.admin.roles.AdminRolesViewModel
import com.example.ecolacteoshuata.presentation.admin.roles.AdminRolesScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AdminRootScreen(usuario: Usuario, onCerrarSesion: () -> Unit, soloContenido: Boolean = false, estadoPantalla: androidx.compose.runtime.MutableState<Screen>? = null, modulosExternos: List<ModuloItem>? = null) {
    val rootViewModel = koinViewModel<AdminRootViewModel>()
    val rootState by rootViewModel.state.collectAsState()

    var pantalla by (estadoPantalla ?: remember { mutableStateOf<Screen>(Screen.AdminDashboard) })
    var usuarioFormId by remember { mutableStateOf<String?>(null) }
    var mostrarUsuarioForm by remember { mutableStateOf(false) }
    var usuarioFormSesion by remember { mutableStateOf(0) }

    val puedeTomarAsistencia = rememberPuedeTomarAsistencia(usuario)
    val modulosDelRol = buildList {
        add(ModuloItem("Usuarios", Icons.Filled.People, "Cuentas del sistema") { pantalla = Screen.AdminUsuarios })
        add(ModuloItem("Roles y funciones", Icons.Filled.Shield, "Configuración central de funciones") { pantalla = Screen.AdminRoles })
        add(ModuloItem("Asignar rol a usuarios", Icons.Filled.AssignmentInd, "Uno o varios roles por usuario") { pantalla = Screen.AdminAsignarRoles })
        add(ModuloItem("Descuentos", Icons.Filled.Discount, "Para el punto de venta") { pantalla = Screen.AdminDescuentos })
        add(ModuloItem("Precios de la leche", Icons.Filled.AttachMoney, "Precio vigente por litro") { pantalla = Screen.AdminPrecios })
        add(ModuloItem("Rutas", Icons.Filled.Route, "Rutas, proveedores y recorrido") { pantalla = Screen.AdminRutas })
        add(ModuloItem("Turnos de rutas y producción", Icons.Filled.Schedule, "Horarios de recojo y planta") { pantalla = Screen.AdminTurnos })
        add(ModuloItem("Empresa", Icons.Filled.Storefront, "Datos de la asociación") { pantalla = Screen.AdminEmpresa })
        if (puedeTomarAsistencia) add(moduloTomarAsistencia { pantalla = Screen.TomarAsistencia })
        add(ModuloItem("Comunicados y asesoramiento", Icons.Filled.Campaign, "Avisos para la asociación") { pantalla = Screen.AdminComunicados })
    }

    val modulos = modulosExternos ?: modulosDelRol

    val (titulo, subtitulo) = when (pantalla) {
        Screen.AdminDashboard -> "Panel de administración" to "Resumen general de la asociación"
        Screen.AdminUsuarios -> "Usuarios" to "Gestión de cuentas del sistema"
        Screen.AdminRoles -> "Roles y funciones" to "Configuración central de Laravel"
        Screen.AdminAsignarRoles -> "Asignar rol a usuarios" to "Uno o varios roles por usuario"
        Screen.AdminDescuentos -> "Descuentos" to "Descuentos del punto de venta"
        Screen.AdminPrecios -> "Precios" to "Precio de la leche"
        Screen.AdminRutas -> "Rutas" to "Proveedores y recorrido del camioncito"
        Screen.AdminTurnos -> "Turnos" to "De rutas y de producción"
        Screen.AdminEmpresa -> "Empresa" to "Datos de la asociación"
        Screen.AdminComunicados -> "Comunicados y asesoramiento" to "Avisos para la asociación"
        Screen.TomarAsistencia -> "Tomar asistencia" to "Asistencia de los trabajadores"
        else -> "Ecolácteos Huata" to null
    }

    val contenido: @Composable () -> Unit = {
        when (pantalla) {
            Screen.AdminDashboard -> {
                val vm = koinViewModel<AdminDashboardViewModel>()
                val state by vm.state.collectAsState()
                AdminDashboardScreen(state = state, modulos = modulos)
            }
            Screen.AdminUsuarios -> {
                val vm = koinViewModel<AdminUsuariosViewModel>()
                val usuarios by vm.usuarios.collectAsState()
                AdminUsuariosScreen(
                    catalogoRoles = usuario.autorizacion?.catalogo_roles.orEmpty(),
                    usuarios = usuarios,
                    onAgregar = { usuarioFormId = null; usuarioFormSesion++; mostrarUsuarioForm = true },
                    onEditar = { usuarioFormId = it.id; usuarioFormSesion++; mostrarUsuarioForm = true },
                    onEliminar = { vm.eliminar(it.id) },
                )
            }
            Screen.AdminFinanzas -> {
                val vm = koinViewModel<com.example.ecolacteoshuata.presentation.admin.finanzas.FinanzasViewModel>()
                com.example.ecolacteoshuata.presentation.admin.finanzas.FinanzasScreen(vm)
            }
            Screen.AdminRoles -> {
                val vm = koinViewModel<AdminRolesViewModel>()
                val rolesState by vm.state.collectAsState()
                AdminRolesScreen(rolesState, vm::nuevo, vm::editar, vm::cargar, vm::cerrar, vm::nombre, vm::codigo,
                    vm::activo, vm::pantalla, vm::guardar, vm::consumirMensaje,
                    if (usuario.autorizacion?.permite("admin.asignar-roles") == true) ({ pantalla = Screen.AdminAsignarRoles }) else null)
            }
            Screen.AdminAsignarRoles -> {
                val vm = koinViewModel<AdminAsignarRolesViewModel>()
                val usuarios by vm.usuarios.collectAsState()
                val form by vm.form.collectAsState()
                val mensaje by vm.mensaje.collectAsState()
                AdminAsignarRolesScreen(
                    catalogoRoles = usuario.autorizacion?.catalogo_roles.orEmpty(),
                    usuarios = usuarios, form = form, mensaje = mensaje,
                    onAbrir = vm::abrir, onCerrar = vm::cerrar, onAlternarRol = vm::alternarRol,
                    onGuardar = vm::guardar, onConsumirMensaje = vm::consumirMensaje,
                )
            }
            Screen.AdminDescuentos -> {
                val vm = koinViewModel<AdminDescuentosViewModel>()
                val descuentos by vm.descuentos.collectAsState()
                val form by vm.form.collectAsState()
                val mensaje by vm.mensaje.collectAsState()
                AdminDescuentosScreen(
                    descuentos = descuentos, form = form, mensaje = mensaje,
                    onNuevo = vm::nuevo, onEditar = vm::editar, onAlternarActivo = vm::alternarActivo, onEliminar = vm::eliminar,
                    onCerrar = vm::cerrar, onNombre = vm::onNombre, onPorcentaje = vm::onPorcentaje, onActivo = vm::onActivo,
                    onGuardar = vm::guardar, onConsumirMensaje = vm::consumirMensaje,
                )
            }
            Screen.AdminRutas -> RutasHost()
            Screen.AdminTurnos -> {
                val vm = koinViewModel<AdminTurnosViewModel>()
                val turnosRuta by vm.turnosRuta.collectAsState()
                val turnosProduccion by vm.turnosProduccion.collectAsState()
                val form by vm.formVisible.collectAsState()
                val error by vm.error.collectAsState()
                AdminTurnosScreen(
                    turnosRuta = turnosRuta, turnosProduccion = turnosProduccion,
                    formVisible = form, error = error,
                    onAbrirForm = vm::abrirForm, onCerrarForm = vm::cerrarForm,
                    onAgregarRuta = vm::agregarTurnoRuta, onAgregarProduccion = vm::agregarTurnoProduccion,
                    onEliminarRuta = vm::eliminarTurnoRuta, onEliminarProduccion = vm::eliminarTurnoProduccion,
                )
            }
            Screen.AdminPrecios -> {
                val vm = koinViewModel<AdminPreciosViewModel>()
                val precio by vm.precioVigente.collectAsState()
                val historial by vm.historial.collectAsState()
                val form by vm.form.collectAsState()
                val mensaje by vm.mensaje.collectAsState()
                AdminPreciosScreen(
                    precioVigente = precio, historial = historial, form = form, mensaje = mensaje,
                    onPrecio = vm::onPrecio, onVigenteDesde = vm::onVigenteDesde,
                    onRegistrar = vm::registrarPrecio, onConsumirMensaje = vm::consumirMensaje,
                )
            }
            Screen.AdminEmpresa -> {
                val vm = koinViewModel<AdminConfiguracionViewModel>()
                val empresa by vm.empresa.collectAsState()
                AdminConfiguracionScreen(empresa = empresa, onGuardar = vm::guardar)
            }
            Screen.TomarAsistencia -> TomarAsistenciaHost(usuario)
            Screen.AdminComunicados -> {
                val vm = koinViewModel<PlantaComunicadosViewModel>(key = "admin-${usuario.id}") { parametersOf(usuario.id) }
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
    if (soloContenido) contenido() else {
    RootScaffold(
        usuario = usuario,
        rolEtiqueta = "Administrador",
        tema = TemaAdmin,
        estadoSync = rootState.estadoSync,
        pendientes = rootState.pendientes,
        onCerrarSesion = onCerrarSesion,
        tituloPantalla = titulo,
        subtituloPantalla = subtitulo,
        bottomNavItems = listOf(
            BottomNavItem("Panel", Icons.Filled.Dashboard),
            BottomNavItem("Usuarios", Icons.Filled.People),
            BottomNavItem("Rutas", Icons.Filled.Route),
            BottomNavItem("Turnos", Icons.Filled.Schedule),
        ),
        bottomNavSeleccionado = when (pantalla) {
            Screen.AdminDashboard -> 0
            Screen.AdminUsuarios -> 1
            Screen.AdminRutas -> 2
            Screen.AdminTurnos -> 3
            else -> -1
        },
        onBottomNavSeleccionar = { index ->
            pantalla = when (index) {
                0 -> Screen.AdminDashboard
                1 -> Screen.AdminUsuarios
                2 -> Screen.AdminRutas
                3 -> Screen.AdminTurnos
                else -> pantalla
            }
        },
        masSheetContent = { onDismiss ->
            MasSheet(titulo = "Módulos de administración", items = modulos, onDismiss = onDismiss)
        },
        content = contenido,
    )
    }

    if (mostrarUsuarioForm) {
        AdminUsuarioFormSheet(usuarioId = usuarioFormId, sesion = usuarioFormSesion, onDismiss = { mostrarUsuarioForm = false })
    }
}
