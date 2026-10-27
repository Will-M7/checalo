package com.example.ecolacteoshuata.presentation.login

import com.example.ecolacteoshuata.core.ui.MensajeError
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.SoraFontFamily
import com.example.ecolacteoshuata.core.theme.VerdeMenta
import com.example.ecolacteoshuata.core.theme.VerdeProfundo
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.LogoHuata
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.presentation.common.conexion.AvisoConexionHost
import com.example.ecolacteoshuata.presentation.common.conexion.ConexionSyncSheet
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import ecolacteoshuata.shared.generated.resources.Res
import ecolacteoshuata.shared.generated.resources.fondo_huata
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsuarioChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onIngresar: () -> Unit,
) {
    var mostrarConexion by remember { mutableStateOf(false) }

    if (mostrarConexion) {
        ConexionSyncSheet(onDismiss = { mostrarConexion = false })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.fondo_huata),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            VerdeProfundo.copy(alpha = 0.80f),
                            VerdeProfundo.copy(alpha = 0.94f),
                        ),
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                // La app es edge-to-edge: sin esto el banner queda bajo la barra de estado y el
                // teclado tapa los campos de usuario/contraseña.
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                // Acceso siempre visible a la conexión con el servidor Laravel.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Blanco.copy(alpha = 0.16f))
                        .clickable { mostrarConexion = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.SettingsEthernet, contentDescription = null, tint = Blanco, modifier = Modifier.size(16.dp))
                    Text("Servidor", color = Blanco, style = EtiquetaMayusculaStyle, modifier = Modifier.padding(start = 6.dp))
                }
            }

            // Desplazable: en pantallas bajas (o con el teclado abierto) nada queda cortado;
            // si sobra espacio, el contenido se centra igual que antes.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val altoDisponible = maxHeight
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = altoDisponible),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("HUATA · PUNO", style = EtiquetaMayusculaStyle, color = VerdeMenta)
                LogoHuata(tamano = 104.dp, modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Ecolácteos Huata",
                    style = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp),
                    color = Blanco,
                    modifier = Modifier.padding(top = 14.dp),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Bienvenido a la asociación de lácteos de Huata",
                    style = CuerpoStyle,
                    color = VerdeMenta,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )

                SectionCard {
                    AppTextField(valor = state.usuario, onValorCambia = onUsuarioChange, etiqueta = "Usuario")
                    AppTextField(
                        valor = state.password,
                        onValorCambia = onPasswordChange,
                        etiqueta = "Contraseña",
                        teclado = KeyboardType.Password,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (state.error != null) {
                        MensajeError(state.error, Modifier.padding(top = 10.dp))
                    }
                    BotonIngresar(cargando = state.cargando, onClick = onIngresar, modifier = Modifier.padding(top = 14.dp))
                }

            }
            }
        }
        // Avisos de conexión (sin Internet, se perdió, reconectando, restablecida...).
        AvisoConexionHost(Modifier.align(Alignment.TopCenter).systemBarsPadding())
    }
}
