package com.example.ecolacteoshuata.presentation.common.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.domain.model.PuntoGeo

/** Centro por defecto del mapa de ubicación, el mismo que usa Laravel (`x-mapa-ubicacion`). */
private val CENTRO_LARAVEL = PuntoGeo(-15.8202, -70.0212)

/**
 * Mapa para marcar la finca con un toque, como `x-mapa-ubicacion` de Laravel: si ya hay
 * ubicación se ve de cerca con su marcador; si no, se ve Huata completo. Cada toque mueve el
 * marcador y llama a [onElegir]; con [onElegir] `null` el mapa es de solo lectura.
 */
@Composable
fun MapaUbicacion(
    ubicacion: PuntoGeo?,
    onElegir: ((PuntoGeo) -> Unit)?,
    modifier: Modifier = Modifier,
    rutas: List<List<PuntoGeo>> = emptyList(),
) {
    val camara = rememberEstadoCamaraMapa()
    // El encuadre se recalcula solo cuando cambian las rutas (otro acopiador), no al tocar el mapa,
    // para que la vista no salte. Como el `fitBounds` de Laravel: rutas + finca.
    val encuadre = remember(rutas) {
        camara.centrar()
        val puntos = rutas.flatten() + listOfNotNull(ubicacion)
        when {
            puntos.size > 1 -> puntos
            else -> {
                val centro = puntos.firstOrNull() ?: CENTRO_LARAVEL
                val radio = if (puntos.isNotEmpty()) 0.004 else 0.03
                listOf(PuntoGeo(centro.lat - radio, centro.lng - radio), PuntoGeo(centro.lat + radio, centro.lng + radio))
            }
        }
    }
    val marcadores = remember(ubicacion) {
        listOfNotNull(ubicacion?.let { MarcadorMapa(id = "finca", numero = 1, punto = it, color = VerdePrimario, resaltado = true) })
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Borde, RoundedCornerShape(12.dp)),
        ) {
            MapaCanvas(
                marcadores = marcadores,
                trazo = emptyList(),
                colorTrazo = VerdePrimario,
                camara = camara,
                encuadre = encuadre,
                trazosReferencia = rutas,
                onTocarMapa = onElegir,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp))
                    .background(Blanco, RoundedCornerShape(14.dp)),
            ) {
                BotonMini(Icons.Filled.Add, "Acercar", Negro) { camara.acercar() }
                BotonMini(Icons.Filled.Remove, "Alejar", Negro) { camara.alejar() }
                BotonMini(Icons.Filled.CenterFocusStrong, "Centrar", VerdePrimario) { camara.centrar() }
            }
        }
        if (onElegir != null) {
            Text(
                "Toca el mapa para marcar la ubicación exacta de la finca.",
                style = CuerpoStyle,
                color = GrisTexto,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
