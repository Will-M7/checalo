package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto

// Piezas comunes de los formularios en hoja inferior, para que todos se vean y se usen igual:
// título arriba · campos · mensaje de error · [Cancelar] [Guardar] · "Eliminar" al final.

/** Título del formulario con una línea opcional que explica para qué sirve. */
@Composable
fun EncabezadoFormulario(titulo: String, subtitulo: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        TituloSeccion(titulo)
        if (!subtitulo.isNullOrBlank()) {
            Text(subtitulo, style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(top = 4.dp, start = 12.dp))
        }
    }
}

/**
 * Error de validación o de guardado: tarjeta roja suave con franja lateral, ícono en burbuja y
 * un título corto, que aparece con una pequeña sacudida para llamar la atención.
 */
@Composable
fun MensajeError(texto: String, modifier: Modifier = Modifier) {
    val sacudida = remember { Animatable(0f) }
    LaunchedEffect(texto) {
        sacudida.snapTo(0f)
        listOf(10f, -8f, 6f, -4f, 0f).forEach { sacudida.animateTo(it, tween(55)) }
    }
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = sacudida.value }
            .clip(forma)
            .background(RojoFondo)
            .border(1.dp, RojoTexto.copy(alpha = 0.18f), forma)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(RojoTexto, RojoTexto.copy(alpha = 0.5f)))))
        Box(
            Modifier.padding(start = 10.dp).size(32.dp).background(RojoTexto.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = RojoTexto, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)) {
            Text("Revisa esto", style = EtiquetaMayusculaStyle, color = RojoTexto)
            Text(texto, color = RojoTexto, style = CuerpoMedioStyle)
        }
    }
}

/** Botones finales del formulario: Cancelar (secundario) a la izquierda y Guardar a la derecha. */
@Composable
fun BotonesFormulario(
    onCancelar: () -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier,
    textoGuardar: String = "Guardar",
    habilitado: Boolean = true,
    cargando: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryButton(texto = "Cancelar", onClick = onCancelar, modifier = Modifier.weight(1f))
        PrimaryButton(texto = textoGuardar, onClick = onGuardar, habilitado = habilitado, cargando = cargando, modifier = Modifier.weight(1f))
    }
}

/** Enlace rojo centrado para eliminar el registro que se está editando. */
@Composable
fun EnlaceEliminar(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = RojoTexto, modifier = Modifier.size(18.dp))
        Text(texto, color = RojoTexto, style = CuerpoMedioStyle, modifier = Modifier.padding(start = 6.dp))
    }
}
