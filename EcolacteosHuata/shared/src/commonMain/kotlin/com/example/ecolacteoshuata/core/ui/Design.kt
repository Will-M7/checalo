package com.example.ecolacteoshuata.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.SoraFontFamily
import com.example.ecolacteoshuata.core.theme.TemaAdmin
import com.example.ecolacteoshuata.core.theme.TemaRol
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.Usuario
import ecolacteoshuata.shared.generated.resources.Res
import ecolacteoshuata.shared.generated.resources.fondo_huata
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

/** Tema del rol con sesión iniciada; lo leen las tarjetas y la navegación para teñirse. */
val LocalTemaRol = staticCompositionLocalOf { TemaAdmin }

@Composable
fun ProveerTemaRol(tema: TemaRol, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTemaRol provides tema, content = content)
}

fun TemaRol.degradado(): Brush = Brush.linearGradient(listOf(degradadoInicio, degradadoFin))

private fun saludo(): String = when (nowLocalDateTime().hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

/**
 * Cabecera con la foto de Huata teñida con el color del rol. En el panel se muestra amplia
 * (saludo, fecha y estado de sincronización); en los demás módulos se compacta para dejar
 * más espacio al contenido.
 */
@Composable
fun HeroHeader(
    usuario: Usuario,
    rolEtiqueta: String,
    estadoSync: EstadoSincronizacion,
    pendientes: Int,
    notificacionesCount: Int,
    titulo: String,
    subtitulo: String?,
    expandido: Boolean,
    showBack: Boolean,
    onBack: (() -> Unit)?,
    onPerfilClick: () -> Unit,
    onNotificacionesClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onConexionClick: () -> Unit = {},
    /** Cuántos roles más tiene el usuario y cómo cambiar a ellos (0 = un solo rol). */
    otrosRoles: Int = 0,
    onCambiarRol: (() -> Unit)? = null,
) {
    val tema = LocalTemaRol.current
    val forma = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, forma, ambientColor = tema.degradadoInicio, spotColor = tema.degradadoInicio)
            .clip(forma),
    ) {
        Image(
            painter = painterResource(Res.drawable.fondo_huata),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(tema.degradadoInicio.copy(alpha = 0.96f), tema.degradadoFin.copy(alpha = 0.82f)),
                    ),
                ),
        )
        // Onda de leche decorativa en el borde inferior.
        OndasDecorativas(modifier = Modifier.matchParentSize(), color = Blanco.copy(alpha = 0.07f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = if (expandido) 20.dp else 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBack && onBack != null) {
                    BotonCristal(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onBack)
                    Spacer(Modifier.width(10.dp))
                }
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onPerfilClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(usuario.iniciales, if (expandido) 46.dp else 38.dp)
                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            if (expandido) "${saludo()} · ${nowLocalDateTime().date.formatDdMmmYyyy()}" else usuario.nombreCompleto,
                            color = Blanco.copy(alpha = if (expandido) 0.8f else 1f),
                            style = CuerpoMedioStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (expandido) {
                            Text(
                                usuario.nombres.substringBefore(" "),
                                color = Blanco,
                                style = TituloPantallaStyle.copy(fontSize = 20.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        ChipRol(rolEtiqueta, otrosRoles, onCambiarRol)
                    }
                }
                // Conexión con el servidor de la red local (Laravel): abre host / IP y puerto.
                BotonCristal(Icons.Filled.SettingsEthernet, "Servidor", onConexionClick)
                Spacer(Modifier.width(6.dp))
                Box {
                    BotonCristal(Icons.Filled.Notifications, "Notificaciones", onNotificacionesClick)
                    if (notificacionesCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6B4A))
                                .border(2.dp, tema.degradadoInicio, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (notificacionesCount > 9) "9+" else notificacionesCount.toString(),
                                color = Blanco,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                BotonCristal(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión", onLogoutClick)
            }

            Text(
                titulo,
                color = Blanco,
                style = TituloPantallaStyle.copy(fontSize = if (expandido) 24.sp else 21.sp),
                modifier = Modifier.padding(top = if (expandido) 14.dp else 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitulo.isNullOrBlank()) {
                Text(subtitulo, color = Blanco.copy(alpha = 0.78f), style = CuerpoMedioStyle, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun Avatar(iniciales: String, tamano: Dp) {
    Box(
        modifier = Modifier
            .size(tamano)
            .clip(CircleShape)
            .background(Blanco.copy(alpha = 0.18f))
            .border(2.dp, Blanco.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(iniciales, color = Blanco, style = TituloPantallaStyle.copy(fontSize = (tamano.value * 0.36f).sp))
    }
}

/** Rol actual; con varios roles se puede tocar para cambiar (muestra "⇄ +N"). */
@Composable
private fun ChipRol(texto: String, otrosRoles: Int, onCambiarRol: (() -> Unit)?) {
    val forma = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .padding(top = 3.dp)
            .clip(forma)
            .background(Blanco.copy(alpha = if (otrosRoles > 0) 0.24f else 0.16f), forma)
            .then(if (otrosRoles > 0 && onCambiarRol != null) Modifier.border(1.dp, Blanco.copy(alpha = 0.35f), forma).clickable(onClick = onCambiarRol) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto.uppercase(), color = Blanco, style = EtiquetaMayusculaStyle.copy(fontSize = 9.sp))
        if (otrosRoles > 0) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Cambiar de rol", tint = Blanco, modifier = Modifier.padding(start = 4.dp).size(12.dp))
            Text("+$otrosRoles", color = Blanco, style = EtiquetaMayusculaStyle.copy(fontSize = 9.sp), modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
private fun BotonCristal(icono: ImageVector, descripcion: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Blanco.copy(alpha = 0.14f))
            .border(1.dp, Blanco.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = Blanco, modifier = Modifier.size(20.dp))
    }
}

/** Ondas suaves (como leche vertida) dibujadas en el fondo de cabeceras y tarjetas. */
@Composable
fun OndasDecorativas(modifier: Modifier = Modifier, color: Color = Blanco.copy(alpha = 0.1f)) {
    val fase by rememberInfiniteTransition(label = "ondas").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(9000)), label = "ondas-fase",
    )
    Canvas(modifier = modifier) {
        for (capa in 0..2) {
            val path = Path()
            val base = size.height * (0.62f + capa * 0.13f)
            val amplitud = 10f + capa * 4f
            path.moveTo(0f, size.height)
            path.lineTo(0f, base)
            var x = 0f
            while (x <= size.width) {
                val y = base + amplitud * sin((x / size.width) * 2 * PI.toFloat() * (1.2f + capa * 0.4f) + fase + capa)
                path.lineTo(x, y)
                x += 8f
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, color)
        }
        drawCircle(color, radius = size.minDimension * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.05f))
    }
}



/** Barra inferior flotante: el ítem activo se ilumina con el degradado del rol. */
@Composable
fun FloatingNavBar(items: List<BottomNavItem>, seleccionado: Int, onSeleccionar: (Int) -> Unit) {
    val tema = LocalTemaRol.current
    val forma = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, forma, ambientColor = tema.acento, spotColor = tema.acento)
                .background(Blanco, forma)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEachIndexed { index, item ->
                val activo = index == seleccionado
                val escala by animateFloatAsState(if (activo) 1f else 0.86f, tween(250), label = "nav-escala")
                val colorTexto by animateColorAsState(if (activo) tema.acento else GrisTexto, label = "nav-color")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSeleccionar(index) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp * escala, height = 32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (activo) tema.degradado() else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icono, contentDescription = item.etiqueta, tint = if (activo) Blanco else GrisTexto, modifier = Modifier.size(21.dp))
                    }
                    Text(
                        item.etiqueta,
                        style = EtiquetaMayusculaStyle.copy(fontSize = 9.5.sp, letterSpacing = 0.sp),
                        color = colorTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta principal de cada panel: la cifra más importante del rol sobre el degradado del
 * rol, con un anillo de progreso opcional (p. ej. proveedores recogidos / total).
 */
@Composable
fun HeroMetricCard(
    etiqueta: String,
    valor: String,
    detalle: String?,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    progreso: Float? = null,
    textoProgreso: String? = null,
) {
    val tema = LocalTemaRol.current
    val forma = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, forma, ambientColor = tema.acento, spotColor = tema.acento)
            .clip(forma)
            .background(tema.degradado()),
    ) {
        OndasDecorativas(Modifier.matchParentSize(), Blanco.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).background(Blanco.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icono, null, tint = Blanco, modifier = Modifier.size(17.dp)) }
                    Text(
                        etiqueta.uppercase(),
                        style = EtiquetaMayusculaStyle,
                        color = Blanco.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    valor,
                    color = Blanco,
                    style = TituloPantallaStyle.copy(fontFamily = SoraFontFamily, fontSize = 34.sp),
                    modifier = Modifier.padding(top = 10.dp),
                    maxLines = 1,
                )
                if (!detalle.isNullOrBlank()) {
                    Text(detalle, color = Blanco.copy(alpha = 0.8f), style = CuerpoMedioStyle, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (progreso != null) {
                AnilloProgreso(progreso.coerceIn(0f, 1f), textoProgreso)
            }
        }
    }
}

@Composable
private fun AnilloProgreso(progreso: Float, texto: String?) {
    val animado by animateFloatAsState(progreso, tween(900), label = "anillo")
    Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grosor = 9.dp.toPx()
            drawArc(Blanco.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(grosor))
            drawArc(Blanco, -90f, 360f * animado, false, style = Stroke(grosor, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(animado * 100).toInt()}%", color = Blanco, style = TituloPantallaStyle.copy(fontSize = 17.sp))
            if (texto != null) Text(texto, color = Blanco.copy(alpha = 0.8f), style = EtiquetaMayusculaStyle.copy(fontSize = 8.sp))
        }
    }
}

/** Título de sección con una barrita del color del rol. */
@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier, accion: (@Composable () -> Unit)? = null) {
    val tema = LocalTemaRol.current
    Row(modifier = modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).background(tema.degradado(), RoundedCornerShape(2.dp)))
        Text(texto, style = TituloPantallaStyle, color = Negro, modifier = Modifier.padding(start = 8.dp).weight(1f))
        accion?.invoke()
    }
}

/** Burbuja con icono y degradado de un color, usada en módulos y KPIs. */
@Composable
fun BurbujaIcono(icono: ImageVector, color: Color, tamano: Dp = 44.dp) {
    val brush = remember(color) { Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))) }
    Box(
        modifier = Modifier
            .size(tamano)
            .shadow(6.dp, RoundedCornerShape(tamano * 0.34f), ambientColor = color, spotColor = color)
            .background(brush, RoundedCornerShape(tamano * 0.34f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, tint = Blanco, modifier = Modifier.size(tamano * 0.5f))
    }
}

@Composable
fun EspacioInferior() {
    Spacer(Modifier.height(12.dp))
}
