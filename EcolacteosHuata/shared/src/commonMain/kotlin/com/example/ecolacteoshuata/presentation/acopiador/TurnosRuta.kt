package com.example.ecolacteoshuata.presentation.acopiador

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.ui.LocalTemaRol
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.Turno

fun EstadoLeche.etiqueta(): String = when (this) {
    EstadoLeche.NORMAL -> "Normal"
    EstadoLeche.SOLEADO -> "Soleado"
    EstadoLeche.ACIDIFICADO -> "Acidificado"
    EstadoLeche.AGUADO -> "Aguado"
    EstadoLeche.OTRO -> "Otro"
}

/** "Mañana (05:30 - 08:00)", como se muestra el turno en Laravel. */
fun Turno.etiquetaConHorario(): String = "$nombre ($horaInicio - $horaFin)"

/**
 * Selector de turnos de ruta: cada turno es una tarjeta con su nombre y su horario; el
 * seleccionado se pinta con el color del rol.
 */
@Composable
fun SelectorTurnosRuta(
    turnos: List<Turno>,
    seleccionado: Turno?,
    onSeleccionar: (Turno) -> Unit,
    modifier: Modifier = Modifier,
) {
    val acento = LocalTemaRol.current.acento
    Column(modifier = modifier.fillMaxWidth()) {
        TituloSeccion("Turnos de ruta")
        if (turnos.isEmpty()) {
            Text(
                "No hay turnos de ruta registrados. Sincroniza con el servidor.",
                style = CuerpoStyle,
                color = GrisTexto,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState())) {
            turnos.forEach { turno ->
                val activo = turno.id == seleccionado?.id
                val forma = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clip(forma)
                        .background(if (activo) acento else Blanco, forma)
                        .border(1.dp, if (activo) acento else acento.copy(alpha = 0.25f), forma)
                        .clickable { onSeleccionar(turno) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(turno.nombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = if (activo) Blanco else Negro)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = if (activo) Blanco.copy(alpha = 0.85f) else GrisTexto,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "${turno.horaInicio} - ${turno.horaFin}",
                            style = CuerpoStyle.copy(fontSize = 12.sp),
                            color = if (activo) Blanco.copy(alpha = 0.85f) else GrisTexto,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
