package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle

/**
 * Botón de acción pequeño (icono + texto) para las tarjetas de una lista: Editar, Eliminar,
 * Publicar… Equivale a los botones de la columna "Acciones" de las tablas de Laravel.
 */
@Composable
fun BotonAccion(texto: String, icono: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(texto, style = CuerpoMedioStyle, color = color, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Confirmación antes de una acción que no se puede deshacer (como `x-confirm-modal` de Laravel). */
@Composable
fun ConfirmarDialog(
    titulo: String,
    mensaje: String,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    colorConfirmar: Color = Rojo,
) {
    val peligro = colorConfirmar == Rojo
    DialogoConfirmacion(
        titulo = titulo,
        mensaje = mensaje,
        icono = when {
            titulo.contains("elimin", ignoreCase = true) || titulo.contains("borr", ignoreCase = true) -> Icons.Filled.DeleteForever
            peligro -> Icons.Filled.WarningAmber
            else -> Icons.AutoMirrored.Filled.HelpOutline
        },
        textoConfirmar = textoConfirmar,
        onConfirmar = onConfirmar,
        onCancelar = onCancelar,
        tono = if (peligro) TonoDialogo.PELIGRO else TonoDialogo.NORMAL,
    )
}
