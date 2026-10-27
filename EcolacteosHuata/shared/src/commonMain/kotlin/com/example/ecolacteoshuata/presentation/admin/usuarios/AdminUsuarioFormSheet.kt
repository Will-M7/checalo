package com.example.ecolacteoshuata.presentation.admin.usuarios

import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AdminUsuarioFormSheet(usuarioId: String?, sesion: Int, onDismiss: () -> Unit) {
    // Clave nueva en cada apertura: si no, el ViewModel de "nuevo" se reutilizaba con los datos
    // del usuario anterior y `guardadoOk = true`, y el formulario se cerraba apenas se abría.
    val viewModel = koinViewModel<AdminUsuarioFormViewModel>(key = "usuario-form-$sesion") { parametersOf(usuarioId) }
    val state by viewModel.state.collectAsState()
    val acopiadores by viewModel.acopiadores.collectAsState()

    LaunchedEffect(state.guardadoOk) {
        if (state.guardadoOk) onDismiss()
    }

    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            EncabezadoFormulario(if (usuarioId == null) "Nuevo usuario" else "Editar usuario")
            AdminUsuarioFormScreen(
                state = state,
                acopiadores = acopiadores,
                onNombresChange = viewModel::onNombresChange,
                onApellidosChange = viewModel::onApellidosChange,
                onDocumentoChange = viewModel::onDocumentoChange,
                onCelularChange = viewModel::onCelularChange,
                onRolChange = viewModel::onRolChange,
                onUsuarioChange = viewModel::onUsuarioChange,
                onPasswordChange = viewModel::onPasswordChange,
                onActivoChange = viewModel::onActivoChange,
                onAcopiadorChange = viewModel::onAcopiadorChange,
                onFincaUbicacionChange = viewModel::onFincaUbicacionChange,
                onFincaReferenciaChange = viewModel::onFincaReferenciaChange,
                onUbicacionChange = viewModel::onUbicacionChange,
                onGuardar = viewModel::guardar,
            )
        }
    }
}
