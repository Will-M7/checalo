package com.example.ecolacteoshuata.presentation.common

import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.InfoRow
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.platform.deviceInfo
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.presentation.common.mapa.AZUL_RUTA
import com.example.ecolacteoshuata.presentation.common.mapa.MapaUbicacion
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PerfilSheet(usuario: Usuario, onDismiss: () -> Unit) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            TituloSeccion("Mi perfil")
            InfoRow("Nombre", usuario.nombreCompleto)
            InfoRow("Documento", usuario.documento)
            InfoRow("Celular", usuario.celular)
            InfoRow("Usuario", usuario.usuario)
            InfoRow("Rol", usuario.rolesAsignados.joinToString(", ") { it.etiqueta })
            // "Acerca de este dispositivo": deviceInfo() viene de cada plataforma (expect/actual).
            InfoRow("Dispositivo", deviceInfo().resumen)
            if (usuario.tieneRol(Rol.PROVEEDOR)) {
                DividerLine(modifier = Modifier.padding(vertical = 16.dp))
                UbicacionFincaSeccion(usuario.id)
            }
        }
    }
}

/** Igual que la sección "Ubicación de mi finca" del perfil en Laravel (también la abre el panel del proveedor). */
@Composable
fun UbicacionFincaSeccion(usuarioId: String) {
    val vm = koinViewModel<UbicacionFincaViewModel>(key = "ubicacion-finca-$usuarioId") { parametersOf(usuarioId) }
    val state by vm.state.collectAsState()
    val acopiadores by vm.acopiadores.collectAsState()
    val rutas by vm.rutasDelAcopiador.collectAsState()

    TituloSeccion("Ubicación de mi finca")
    Text("El acopiador la usa para ubicar tu finca en la ruta de recojo.", style = CuerpoStyle, color = GrisTexto)
    if (!state.cargado) return

    AppTextField(state.fincaUbicacion, vm::onFincaUbicacion, "Ubicación / dirección", placeholder = "Comunidad de Huata", modifier = Modifier.padding(top = 12.dp))
    AppTextField(state.fincaReferencia, vm::onFincaReferencia, "Referencia", placeholder = "A 200m de la escuela...", modifier = Modifier.padding(top = 10.dp))
    AppDropdown(
        seleccionado = acopiadores.firstOrNull { it.id == state.acopiadorId },
        opciones = acopiadores,
        etiqueta = "Acopiador que recoge tu leche",
        textoOpcion = { it.nombreCompleto },
        onSeleccionar = vm::onAcopiador,
        modifier = Modifier.padding(top = 10.dp),
    )
    if (state.acopiadorId != null) {
        Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp, end = 6.dp).size(width = 20.dp, height = 4.dp).background(AZUL_RUTA, RoundedCornerShape(2.dp)))
            Text(
                "La línea azul es la ruta por donde recoge este acopiador. Marca tu finca y guarda: quedarás en su ruta automáticamente.",
                style = CuerpoStyle,
                color = GrisTexto,
            )
        }
    }
    MapaUbicacion(ubicacion = state.ubicacion, onElegir = vm::onUbicacion, rutas = rutas, modifier = Modifier.padding(top = 10.dp))

    state.error?.let {
        MensajeError(it, Modifier.padding(top = 10.dp))
    }
    state.mensaje?.let {
        Text(
            it,
            color = VerdeOscuro,
            style = CuerpoMedioStyle,
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().background(VerdeClaro, RoundedCornerShape(10.dp)).padding(10.dp),
        )
    }
    PrimaryButton(texto = "Guardar ubicación", onClick = vm::guardar, cargando = state.guardando, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}
