package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.AmbarFondo
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.AmbarTexto
import com.example.ecolacteoshuata.core.theme.CelesteClaro
import com.example.ecolacteoshuata.core.theme.CelesteTexto
import com.example.ecolacteoshuata.core.theme.Dimens
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro

enum class EstiloChip { EXITO, ADVERTENCIA, ERROR, INFO }

@Composable
fun EstadoChip(texto: String, estilo: EstiloChip, modifier: Modifier = Modifier) {
    val (fondo, color) = when (estilo) {
        EstiloChip.EXITO -> VerdeClaro to VerdeOscuro
        EstiloChip.ADVERTENCIA -> AmbarFondo to AmbarTexto
        EstiloChip.ERROR -> RojoFondo to RojoTexto
        EstiloChip.INFO -> CelesteClaro to CelesteTexto
    }
    Text(
        text = texto.uppercase(),
        style = EtiquetaMayusculaStyle,
        color = color,
        modifier = modifier
            .background(fondo, RoundedCornerShape(Dimens.chipRadius))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Fondo de una opción seleccionable (periodo, turno, estado…) en el color del rol. */
@Composable
fun fondoOpcion(activa: Boolean): Color =
    if (activa) LocalTemaRol.current.acento else LocalTemaRol.current.acento.copy(alpha = 0.10f)

/** Color del texto de una opción seleccionable, a juego con [fondoOpcion]. */
@Composable
fun textoOpcion(activa: Boolean): Color = if (activa) Blanco else LocalTemaRol.current.acento
