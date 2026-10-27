package com.example.ecolacteoshuata.presentation.common.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Mapa listo para usar en un rol: de solo lectura y filtrado por [acopiadorId] para el acopiador
 * ("Mapa de recojo"). Planta y administrador lo editan desde Rutas (ver RutasHost).
 */
@Composable
fun MapaRutaHost(editable: Boolean, acopiadorId: String? = null, onIrARecojo: (() -> Unit)? = null) {
    val vm = koinViewModel<MapaRutaViewModel>(key = "mapa-$editable-$acopiadorId") { parametersOf(editable, acopiadorId) }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refrescarRecogidos() }
    MapaRutaScreen(
        state = state,
        onSeleccionarRuta = vm::seleccionarRuta,
        onEnfocarParada = vm::enfocarParada,
        onTocarMapa = vm::tocarMapa,
        onAlternarUbicar = vm::alternarUbicar,
        onEmpezarDibujo = vm::empezarDibujo,
        onDeshacerPunto = vm::deshacerPunto,
        onGuardarDibujo = vm::guardarDibujo,
        onCancelarModo = vm::cancelarModo,
        onTrazarPorParadas = vm::trazarPorParadas,
        onLimpiarTrazo = vm::limpiarTrazo,
        onMoverParada = vm::moverParada,
        onConsumirMensaje = vm::consumirMensaje,
        onBuscar = vm::buscar,
        onIrARecojo = onIrARecojo,
    )
}
