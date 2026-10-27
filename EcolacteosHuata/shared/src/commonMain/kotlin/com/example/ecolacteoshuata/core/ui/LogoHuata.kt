package com.example.ecolacteoshuata.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.VerdeMenta
import ecolacteoshuata.shared.generated.resources.Res
import ecolacteoshuata.shared.generated.resources.logo_huata
import org.jetbrains.compose.resources.painterResource

private val CremaLeche = Color(0xFFFFFCF3)

/**
 * Logo del ternero completo (PNG sin fondo) sobre un disco color leche. Con [animado], unos
 * anillos se expanden como gotas cayendo en leche y el logo "respira" suavemente.
 */
@Composable
fun LogoHuata(tamano: Dp, modifier: Modifier = Modifier, animado: Boolean = true) {
    val transicion = rememberInfiniteTransition(label = "logo")
    val onda by transicion.animateFloat(0f, 1f, infiniteRepeatable(tween(2600)), label = "logo-onda")
    val respiro by transicion.animateFloat(1f, 1.035f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "logo-respiro")

    Box(modifier = modifier.size(tamano * 1.45f), contentAlignment = Alignment.Center) {
        if (animado) {
            Canvas(Modifier.fillMaxSize()) {
                val radioBase = size.minDimension / 2 / 1.45f
                for (i in 0..1) {
                    val t = (onda + i * 0.5f) % 1f
                    drawCircle(
                        color = VerdeMenta.copy(alpha = 0.55f * (1 - t)),
                        radius = radioBase * (1f + 0.45f * t),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(tamano)
                .scale(if (animado) respiro else 1f)
                .shadow(14.dp, CircleShape, ambientColor = VerdeMenta, spotColor = VerdeMenta)
                .background(CremaLeche, CircleShape)
                .border(3.dp, VerdeMenta.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.logo_huata),
                contentDescription = "Logo Ecolácteos Huata",
                // Fit: se ve el logo entero (ternero y cintas de colores), sin recortarlo.
                modifier = Modifier.fillMaxSize().padding(tamano * 0.13f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
