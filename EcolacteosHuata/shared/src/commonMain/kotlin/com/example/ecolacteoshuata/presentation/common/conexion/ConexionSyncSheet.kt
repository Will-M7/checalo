package com.example.ecolacteoshuata.presentation.common.conexion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import com.example.ecolacteoshuata.core.ui.DialogoConfirmacion
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Ambar
import com.example.ecolacteoshuata.core.theme.AmbarFondo
import com.example.ecolacteoshuata.core.theme.AmbarTexto
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.FondoApp
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import org.koin.compose.viewmodel.koinViewModel

/** Abre el panel de conexión desde cualquier pantalla (cabecera de cada rol o login). */
@Composable
fun ConexionSyncSheet(onDismiss: () -> Unit) {
    val vm = koinViewModel<ConexionSyncViewModel>()
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refrescar() }
    AppBottomSheet(onDismiss = onDismiss) {
        Box {
            ConexionSyncContenido(
                state = state,
                onHostChange = vm::onHostChange,
                onPuertoChange = vm::onPuertoChange,
                onActivaChange = vm::onActivaChange,
                onProbar = vm::probar,
                onGuardar = vm::guardar,
                onSincronizar = vm::sincronizar,
                onDescargarTodo = vm::descargarTodo,
                onConfirmarSesion = vm::confirmarSesion,
            )
            ConfirmToastHost(mensaje = state.mensaje, onDismiss = vm::consumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

private data class AspectoEstado(val titulo: String, val detalle: String, val color: Color, val icono: ImageVector)

private fun aspecto(s: ConexionSyncUiState): AspectoEstado = when (s.estado) {
    EstadoSincronizacion.SINCRONIZADO -> AspectoEstado(
        "Conectado y al día", "Servidor ${s.guardada.host}:${s.guardada.puerto}", VerdePrimario, Icons.Filled.CloudDone,
    )
    EstadoSincronizacion.SINCRONIZANDO -> AspectoEstado(
        "Sincronizando…", s.progreso?.etapa ?: "Conectando con el servidor", Color(0xFF1E9BC8), Icons.Filled.CloudSync,
    )
    EstadoSincronizacion.SIN_SENAL -> AspectoEstado(
        "Sin conexión con el servidor",
        if (s.guardada.token.isNullOrBlank() && s.cuenta != null) "Confirma la sesión de esta cuenta con Laravel."
        else if (s.error != null) "El último intento falló. Trabajas con los datos guardados." else "Aún no se sincroniza en esta sesión.",
        Ambar, Icons.Filled.CloudOff,
    )
    EstadoSincronizacion.MODO_LOCAL -> AspectoEstado(
        "Modo local", "Sin servidor configurado: solo datos de este teléfono.", GrisTexto, Icons.Filled.PhoneAndroid,
    )
}

@Composable
fun ConexionSyncContenido(
    state: ConexionSyncUiState,
    onHostChange: (String) -> Unit,
    onPuertoChange: (String) -> Unit,
    onActivaChange: (Boolean) -> Unit,
    onProbar: () -> Unit,
    onGuardar: () -> Unit,
    onSincronizar: () -> Unit,
    onDescargarTodo: () -> Unit,
    onConfirmarSesion: (String) -> Unit = {},
) {
    var confirmarDescarga by remember { mutableStateOf(false) }
    val a = aspecto(state)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        Text("Conexión y sincronización", style = TituloPantallaStyle.copy(fontSize = 20.sp), color = Negro)

        // ---- Estado actual ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(a.color.copy(alpha = 0.16f), a.color.copy(alpha = 0.05f))))
                .border(1.dp, a.color.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbeEstado(a.color, a.icono, girando = state.estado == EstadoSincronizacion.SINCRONIZANDO, progreso = state.progreso?.fraccion)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(a.titulo, style = TituloPantallaStyle, color = Negro)
                Text(a.detalle, style = CuerpoStyle.copy(fontSize = 12.5.sp), color = GrisTexto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Última sincronización: ${state.ultimaSincronizacionTexto ?: "nunca"}",
                    style = EtiquetaMayusculaStyle.copy(fontSize = 9.5.sp, letterSpacing = 0.sp),
                    color = a.color,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        AnimatedVisibility(visible = state.estado == EstadoSincronizacion.SINCRONIZANDO) {
            val avance by animateFloatAsState(state.progreso?.fraccion ?: 0f, tween(400), label = "avance")
            LinearProgressIndicator(
                progress = { avance },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                color = Color(0xFF1E9BC8),
                trackColor = Borde,
                strokeCap = StrokeCap.Round,
            )
        }

        if (state.error != null && state.estado != EstadoSincronizacion.SINCRONIZANDO) {
            Aviso(Icons.Filled.ErrorOutline, state.error, RojoFondo, RojoTexto)
        }

        if (state.guardada.activa && state.guardada.token.isNullOrBlank() && state.cuenta != null) {
            var password by remember { mutableStateOf("") }
            com.example.ecolacteoshuata.core.ui.SectionCard {
                Text("Confirmar sesión de ${state.cuenta}", style = TituloPantallaStyle)
                Text("Vuelve a autenticar esta cuenta con Laravel para recuperar la descarga. Los datos del teléfono se conservan.", style = CuerpoStyle)
                androidx.compose.material3.OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    enabled = !state.autenticando, modifier = Modifier.fillMaxWidth(),
                )
                com.example.ecolacteoshuata.core.ui.PrimaryButton("Confirmar y sincronizar", {
                    onConfirmarSesion(password)
                    password = ""
                }, habilitado = !state.autenticando && password.isNotBlank() && !state.sinGuardar, cargando = state.autenticando)
                if (state.sinGuardar) Text("Guarda primero los cambios de conexión.", style = CuerpoMedioStyle)
            }
        }

        // ---- Acciones de sincronización ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonGrande(
                texto = "Sincronizar ahora",
                icono = Icons.Filled.Sync,
                color = VerdePrimario,
                habilitado = state.puedeSincronizar,
                modifier = Modifier.weight(1f),
                onClick = onSincronizar,
            )
            BotonGrande(
                texto = "Descargar todo",
                icono = Icons.Filled.CloudDownload,
                color = Color(0xFF1E9BC8),
                habilitado = state.puedeSincronizar,
                modifier = Modifier.weight(1f),
                onClick = { confirmarDescarga = true },
            )
        }

        if (state.resumen.any { it.cantidad > 0 }) {
            Text("RECIBIDO EN LA ÚLTIMA SINCRONIZACIÓN", style = EtiquetaMayusculaStyle, color = GrisTexto)
            FilaFichas(state.resumen.filter { it.cantidad > 0 }.map { "${it.etiqueta} · ${it.cantidad}" })
        }

        if (state.pendientes > 0) {
            Aviso(
                Icons.Filled.Info,
                "${state.pendientes} registro(s) pendientes de enviar a Laravel. Se conservan si no hay conexión.",
                AmbarFondo,
                AmbarTexto,
            )
        }

        if (state.revisiones.isNotEmpty()) {
            Text("TRABAJO CONSERVADO PARA REVISIÓN", style = EtiquetaMayusculaStyle, color = GrisTexto)
            state.revisiones.forEach { revision ->
                val contenido = runCatching {
                    val cambio = kotlinx.serialization.json.Json.decodeFromString<com.example.ecolacteoshuata.data.remote.dto.CambioDto>(revision.documento)
                    cambio.datos.orEmpty().filterKeys { !it.contains("password") && !it.contains("token") }.entries.joinToString("\n") { "${it.key}: ${it.value.orEmpty()}" }
                }.getOrDefault("Contenido conservado en la copia local para revisión técnica.")
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Aviso(Icons.Filled.Info, "${revision.tabla} · ${revision.registro_id}: ${revision.motivo}\n$contenido", AmbarFondo, AmbarTexto)
                }
            }
        }

        // ---- Servidor ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(FondoApp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(VerdeClaro, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Router, null, tint = VerdeOscuro, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Servidor Laravel", style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                    Text(
                        if (state.activa) "Conectado a la red local" else "Desactivado (modo local)",
                        style = CuerpoStyle.copy(fontSize = 12.sp),
                        color = GrisTexto,
                    )
                }
                Switch(
                    checked = state.activa,
                    onCheckedChange = onActivaChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = VerdePrimario),
                )
            }
            if (state.activa) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        valor = state.host,
                        onValorCambia = onHostChange,
                        etiqueta = "IP del servidor",
                        placeholder = "192.168.1.10",
                        teclado = KeyboardType.Uri,
                        modifier = Modifier.weight(1.7f),
                    )
                    AppTextField(
                        valor = state.puerto,
                        onValorCambia = onPuertoChange,
                        etiqueta = "Puerto",
                        placeholder = "8000",
                        teclado = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                ResultadoPrueba(state.prueba)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonGrande(
                        texto = if (state.prueba == PruebaConexion.Probando) "Probando…" else "Probar",
                        icono = Icons.Filled.WifiTethering,
                        color = VerdeOscuro,
                        habilitado = state.host.isNotBlank() && state.prueba != PruebaConexion.Probando,
                        contorno = true,
                        modifier = Modifier.weight(1f),
                        onClick = onProbar,
                    )
                    BotonGrande(
                        texto = "Guardar",
                        icono = Icons.Filled.Save,
                        color = VerdePrimario,
                        habilitado = state.sinGuardar,
                        modifier = Modifier.weight(1f),
                        onClick = onGuardar,
                    )
                }
            } else if (state.sinGuardar) {
                BotonGrande("Guardar (usar modo local)", Icons.Filled.Save, VerdePrimario, true, Modifier.fillMaxWidth(), onClick = onGuardar)
            }
        }

        Aviso(
            Icons.Filled.Info,
            "En la PC: php artisan serve --host=0.0.0.0 --port=${state.puerto.ifBlank { "8000" }}. " +
                "Usa la IP de la PC (ipconfig) y conecta el teléfono a la misma red Wi-Fi.",
            VerdeClaro,
            VerdeOscuro,
        )
    }

    if (confirmarDescarga) {
        DialogoConfirmacion(
            titulo = "¿Descargar todo de nuevo?",
            mensaje = "Se volverán a traer todos los datos del servidor. Úsalo si notas información faltante o desactualizada. " +
                "Lo que hiciste en el teléfono se sube primero, no se pierde.",
            icono = Icons.Filled.CloudDownload,
            textoConfirmar = "Descargar",
            onConfirmar = { confirmarDescarga = false; onDescargarTodo() },
            onCancelar = { confirmarDescarga = false },
        )
    }
}

/** Círculo con el icono del estado; gira con un arco de progreso mientras sincroniza. */
@Composable
private fun OrbeEstado(color: Color, icono: ImageVector, girando: Boolean, progreso: Float?) {
    val transicion = rememberInfiniteTransition(label = "orbe")
    val giro by transicion.animateFloat(0f, 360f, infiniteRepeatable(tween(1200)), label = "giro")
    val pulso by transicion.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulso")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(if (girando) giro else 0f)) {
            drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension / 2 * (if (girando) 1f else pulso))
            if (girando) {
                drawArc(
                    color,
                    startAngle = 0f,
                    sweepAngle = 90f + 250f * (progreso ?: 0f),
                    useCenter = false,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(Modifier.size(42.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = Blanco, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ResultadoPrueba(prueba: PruebaConexion) {
    when (prueba) {
        PruebaConexion.Ninguna -> Unit
        PruebaConexion.Probando -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = VerdePrimario)
            Text("Buscando el servidor…", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(start = 8.dp))
        }
        is PruebaConexion.Exitosa -> Aviso(
            Icons.Filled.CheckCircle,
            "El servidor responde (${prueba.milisegundos} ms). Esta prueba no confirma la sesión ni la descarga de datos. Guarda la conexión y sincroniza.",
            VerdeClaro,
            VerdeOscuro,
        )
        is PruebaConexion.Fallida -> Aviso(Icons.Filled.ErrorOutline, prueba.motivo, RojoFondo, RojoTexto)
    }
}

@Composable
private fun Aviso(icono: ImageVector, texto: String, fondo: Color, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().background(fondo, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icono, null, tint = color, modifier = Modifier.size(18.dp))
        Text(texto, style = CuerpoStyle.copy(fontSize = 12.5.sp), color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FilaFichas(textos: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        textos.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fila.forEach {
                    Text(
                        it,
                        style = CuerpoMedioStyle.copy(fontSize = 11.5.sp),
                        color = VerdeOscuro,
                        maxLines = 1,
                        modifier = Modifier.background(VerdeClaro, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BotonGrande(
    texto: String,
    icono: ImageVector,
    color: Color,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    contorno: Boolean = false,
    onClick: () -> Unit,
) {
    val c = if (habilitado) color else GrisTexto.copy(alpha = 0.5f)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (contorno) Modifier.border(1.5.dp, c, RoundedCornerShape(14.dp)) else Modifier.background(c))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, null, tint = if (contorno) c else Blanco, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = CuerpoMedioStyle, color = if (contorno) c else Blanco, maxLines = 1)
    }
}
