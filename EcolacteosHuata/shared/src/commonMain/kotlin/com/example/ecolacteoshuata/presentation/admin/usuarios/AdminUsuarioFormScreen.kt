package com.example.ecolacteoshuata.presentation.admin.usuarios

import com.example.ecolacteoshuata.core.ui.MensajeError
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.presentation.common.mapa.MapaUbicacion

@Composable
fun AdminUsuarioFormScreen(
    state: AdminUsuarioFormState,
    acopiadores: List<Acopiador>,
    onNombresChange: (String) -> Unit,
    onApellidosChange: (String) -> Unit,
    onDocumentoChange: (String) -> Unit,
    onCelularChange: (String) -> Unit,
    onRolChange: (String) -> Unit,
    onUsuarioChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onActivoChange: (Boolean) -> Unit,
    onAcopiadorChange: (Acopiador) -> Unit,
    onFincaUbicacionChange: (String) -> Unit,
    onFincaReferenciaChange: (String) -> Unit,
    onUbicacionChange: (PuntoGeo) -> Unit,
    onGuardar: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppTextField(state.nombres, onNombresChange, "Nombres")
        AppTextField(state.apellidos, onApellidosChange, "Apellidos", modifier = Modifier.padding(top = 10.dp))
        AppTextField(state.documento, onDocumentoChange, "DNI/RUC", modifier = Modifier.padding(top = 10.dp), teclado = KeyboardType.Number)
        AppTextField(state.celular, onCelularChange, "Celular", modifier = Modifier.padding(top = 10.dp), teclado = KeyboardType.Phone)
        Text("Roles", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(top = 10.dp))
        state.catalogo.forEach { rol ->
            com.example.ecolacteoshuata.core.ui.CasillaTexto(rol.nombre + if (rol.activo) "" else " (Inactivo)", rol.id in state.roles) { onRolChange(rol.id) }
        }
        if ("proveedor" in state.roles) {
            DatosProveedorCampos(
                acopiadores = acopiadores,
                acopiadorId = state.acopiadorId,
                fincaUbicacion = state.fincaUbicacion,
                fincaReferencia = state.fincaReferencia,
                ubicacion = state.ubicacion,
                onAcopiadorChange = onAcopiadorChange,
                onFincaUbicacionChange = onFincaUbicacionChange,
                onFincaReferenciaChange = onFincaReferenciaChange,
                onUbicacionChange = onUbicacionChange,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        AppTextField(state.usuario, onUsuarioChange, "Usuario", placeholder = "usuario de acceso", modifier = Modifier.padding(top = 10.dp))
        AppTextField(
            state.password,
            onPasswordChange,
            "Contraseña",
            placeholder = if (state.editandoId == null) "" else "Dejar en blanco para no cambiar",
            teclado = KeyboardType.Password,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Usuario activo", style = CuerpoMedioStyle, color = Negro)
            Switch(
                checked = state.activo,
                onCheckedChange = onActivoChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Blanco, checkedTrackColor = VerdePrimario),
            )
        }
        if (state.error != null) {
            MensajeError(state.error, Modifier.padding(top = 10.dp))
        }
        PrimaryButton(texto = "Guardar", onClick = onGuardar, cargando = state.cargando, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    }
}

/** Recuadro "Acopiador + Ubicación de la finca" que Laravel muestra cuando el rol es Proveedor. */
@Composable
fun DatosProveedorCampos(
    acopiadores: List<Acopiador>,
    acopiadorId: String?,
    fincaUbicacion: String,
    fincaReferencia: String,
    ubicacion: PuntoGeo?,
    onAcopiadorChange: (Acopiador) -> Unit,
    onFincaUbicacionChange: (String) -> Unit,
    onFincaReferenciaChange: (String) -> Unit,
    onUbicacionChange: (PuntoGeo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Borde, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        AppDropdown(
            seleccionado = acopiadores.firstOrNull { it.id == acopiadorId },
            opciones = acopiadores,
            etiqueta = "Acopiador",
            textoOpcion = { it.nombreCompleto },
            onSeleccionar = onAcopiadorChange,
        )
        Text("Ubicación de la finca", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(top = 10.dp))
        AppTextField(fincaUbicacion, onFincaUbicacionChange, "Ubicación / dirección", placeholder = "Comunidad de Huata", modifier = Modifier.padding(top = 6.dp))
        AppTextField(fincaReferencia, onFincaReferenciaChange, "Referencia", placeholder = "A 200m de la escuela...", modifier = Modifier.padding(top = 10.dp))
        MapaUbicacion(ubicacion = ubicacion, onElegir = onUbicacionChange, modifier = Modifier.padding(top = 10.dp))
    }
}
