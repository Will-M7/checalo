package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle

/** Estado vacío: burbuja con icono en el color del rol, título y una indicación de qué hacer. */
@Composable
fun EmptyState(titulo: String, mensaje: String, modifier: Modifier = Modifier, icono: ImageVector = Icons.Filled.Inbox) {
    val tema = LocalTemaRol.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).background(tema.acento.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(58.dp).background(tema.acento.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = tema.acento, modifier = Modifier.size(30.dp))
            }
        }
        Text(titulo, style = TituloPantallaStyle, color = Negro, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(
            mensaje,
            style = CuerpoMedioStyle,
            color = GrisTexto,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
