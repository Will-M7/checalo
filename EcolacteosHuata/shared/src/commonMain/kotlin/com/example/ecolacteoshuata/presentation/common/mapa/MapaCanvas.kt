package com.example.ecolacteoshuata.presentation.common.mapa

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Controla zoom y desplazamiento del mapa desde botones externos (+, −, centrar). */
class EstadoCamaraMapa {
    var zoom by mutableFloatStateOf(1f)
    var desplazamiento by mutableStateOf(Offset.Zero)

    fun acercar() { zoom = (zoom * 1.4f).coerceAtMost(ZOOM_MAX) }
    fun alejar() { zoom = (zoom / 1.4f).coerceAtLeast(ZOOM_MIN) }
    fun centrar() { zoom = 1f; desplazamiento = Offset.Zero }

    companion object {
        const val ZOOM_MIN = 0.6f
        const val ZOOM_MAX = 12f
    }
}

@Composable
fun rememberEstadoCamaraMapa(): EstadoCamaraMapa = remember { EstadoCamaraMapa() }

data class MarcadorMapa(
    val id: String,
    val numero: Int,
    val punto: PuntoGeo,
    val color: Color,
    val resaltado: Boolean = false,
    val etiqueta: String? = null,
    /** Texto del pin en lugar del número (p. ej. iniciales de un proveedor que no es parada). */
    val rotulo: String? = null,
)

/** Centro por defecto: Huata, Puno (cuando todavía no hay ningún punto que mostrar). */
private val CENTRO_HUATA = PuntoGeo(-15.787, -70.02)
private const val METROS_POR_GRADO = 111_320.0

/** Azul de las rutas de referencia, el mismo que usa Laravel (`#2563eb`). */
val AZUL_RUTA = Color(0xFF2563EB)

/**
 * Proyección equirectangular simple (suficiente para una ruta de pocos km) que encaja todos
 * los puntos en el lienzo. Con internet se pintan debajo los mosaicos de OpenStreetMap
 * ([dibujarMosaicos]); sin conexión queda el fondo de relieve estilizado.
 */
private class Proyeccion(puntos: List<PuntoGeo>, private val lienzo: Size, private val zoom: Float, private val pan: Offset) {
    private val minLat: Double
    private val maxLat: Double
    private val minLng: Double
    private val maxLng: Double
    private val cosLat: Double
    val escala: Double // píxeles por grado de longitud "corregida"

    init {
        val pts = puntos.ifEmpty { listOf(CENTRO_HUATA) }
        var aLat = pts.minOf { it.lat }
        var bLat = pts.maxOf { it.lat }
        var aLng = pts.minOf { it.lng }
        var bLng = pts.maxOf { it.lng }
        val spanMin = 0.006
        if (bLat - aLat < spanMin) { val c = (aLat + bLat) / 2; aLat = c - spanMin / 2; bLat = c + spanMin / 2 }
        if (bLng - aLng < spanMin) { val c = (aLng + bLng) / 2; aLng = c - spanMin / 2; bLng = c + spanMin / 2 }
        val padLat = (bLat - aLat) * 0.18
        val padLng = (bLng - aLng) * 0.18
        minLat = aLat - padLat; maxLat = bLat + padLat
        minLng = aLng - padLng; maxLng = bLng + padLng
        cosLat = cos(((minLat + maxLat) / 2) * PI / 180)
        val spanX = (maxLng - minLng) * cosLat
        val spanY = maxLat - minLat
        escala = min(lienzo.width / spanX, lienzo.height / spanY)
    }

    private val base = Offset(
        ((lienzo.width - (maxLng - minLng) * cosLat * escala) / 2).toFloat(),
        ((lienzo.height - (maxLat - minLat) * escala) / 2).toFloat(),
    )
    private val centro = Offset(lienzo.width / 2, lienzo.height / 2)

    fun aPantalla(p: PuntoGeo): Offset {
        val x = base.x + ((p.lng - minLng) * cosLat * escala).toFloat()
        val y = base.y + ((maxLat - p.lat) * escala).toFloat()
        return (Offset(x, y) - centro) * zoom + centro + pan
    }

    fun aGeo(o: Offset): PuntoGeo {
        val sinZoom = (o - pan - centro) / zoom + centro
        val lng = minLng + (sinZoom.x - base.x) / (cosLat * escala)
        val lat = maxLat - (sinZoom.y - base.y) / escala
        return PuntoGeo(lat, lng)
    }

    /** Metros que representa un píxel con el zoom actual. */
    val metrosPorPixel: Double get() = METROS_POR_GRADO / (escala * zoom)
}

@Composable
fun MapaCanvas(
    marcadores: List<MarcadorMapa>,
    trazo: List<PuntoGeo>,
    colorTrazo: Color,
    camara: EstadoCamaraMapa,
    modifier: Modifier = Modifier,
    trazoEnEdicion: Boolean = false,
    onTocarMarcador: (String) -> Unit = {},
    onTocarMapa: ((PuntoGeo) -> Unit)? = null,
    /** Zona fija que encuadra la vista; si es `null` se encuadran los marcadores y el camino. */
    encuadre: List<PuntoGeo>? = null,
    /** Caminos de referencia en azul, debajo de todo (p. ej. las rutas del acopiador elegido). */
    trazosReferencia: List<List<PuntoGeo>> = emptyList(),
) {
    val medidor = rememberTextMeasurer()
    val mosaicos = rememberMosaicosOsm()
    val transicion = rememberInfiniteTransition(label = "mapa")
    val fase by transicion.animateFloat(0f, 40f, infiniteRepeatable(tween(1400)), label = "flujo")
    val pulso by transicion.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Restart), label = "pulso")

    // Los límites se calculan con todos los puntos para que la vista no "salte" al moverlos.
    val puntosEncuadre = remember(marcadores, trazo, trazoEnEdicion, encuadre) {
        encuadre ?: (marcadores.map { it.punto } + if (trazoEnEdicion) emptyList() else trazo)
    }
    var tamano by remember { mutableStateOf(Size.Zero) }
    val marcadoresActuales by rememberUpdatedState(marcadores)
    val onTocarMapaActual by rememberUpdatedState(onTocarMapa)
    val onTocarMarcadorActual by rememberUpdatedState(onTocarMarcador)
    val encuadreActual by rememberUpdatedState(puntosEncuadre)

    Canvas(
        modifier = modifier
            // El tamaño se guarda al medir, no dentro del dibujo (escribir estado mientras se
            // dibuja fuerza recomposiciones extra).
            .onSizeChanged { tamano = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    camara.zoom = (camara.zoom * zoom).coerceIn(EstadoCamaraMapa.ZOOM_MIN, EstadoCamaraMapa.ZOOM_MAX)
                    camara.desplazamiento += pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { toque ->
                    val proy = Proyeccion(encuadreActual, tamano, camara.zoom, camara.desplazamiento)
                    val radio = 26.dp.toPx()
                    val cercano = marcadoresActuales
                        .map { it to (proy.aPantalla(it.punto) - toque).getDistance() }
                        .filter { it.second <= radio }
                        .minByOrNull { it.second }
                    val accionMapa = onTocarMapaActual
                    when {
                        accionMapa != null -> accionMapa(proy.aGeo(toque))
                        cercano != null -> onTocarMarcadorActual(cercano.first.id)
                    }
                }
            },
    ) {
        val proy = Proyeccion(puntosEncuadre, size, camara.zoom, camara.desplazamiento)

        dibujarFondo(proy, Proyeccion(puntosEncuadre, size, 1f, Offset.Zero), camara.zoom)
        // Encima del fondo, el mapa real de OpenStreetMap (si hay internet), como en Laravel.
        dibujarMosaicos(mosaicos, proy::aPantalla, proy::aGeo, proy.metrosPorPixel, medidor)

        trazosReferencia.filter { it.size >= 2 }.forEach { camino ->
            val path = Path()
            camino.forEachIndexed { i, p ->
                val o = proy.aPantalla(p)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, AZUL_RUTA.copy(alpha = 0.8f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Camino: borde blanco + color del rol + flechas de flujo animadas (sentido del recorrido).
        if (trazo.size >= 2) {
            val path = Path()
            trazo.forEachIndexed { i, p ->
                val o = proy.aPantalla(p)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, Color.White, style = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, colorTrazo, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(
                path,
                Color.White.copy(alpha = 0.85f),
                style = Stroke(
                    2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 14.dp.toPx()), -fase.dp.toPx()),
                ),
            )
        }
        if (trazoEnEdicion) {
            trazo.forEach { p ->
                val o = proy.aPantalla(p)
                drawCircle(Color.White, 6.dp.toPx(), o)
                drawCircle(colorTrazo, 4.dp.toPx(), o)
            }
            trazo.firstOrNull()?.let { dibujarBandera(proy.aPantalla(it), colorTrazo, medidor, "Inicio") }
        } else if (trazo.isNotEmpty()) {
            dibujarBandera(proy.aPantalla(trazo.first()), colorTrazo, medidor, "Inicio")
        }

        // Paradas (los resaltados al final para que queden encima).
        marcadores.sortedBy { it.resaltado }.forEach { m ->
            val o = proy.aPantalla(m.punto)
            if (m.resaltado) {
                drawCircle(m.color.copy(alpha = 0.35f * (1 - pulso)), (18 + 22 * pulso).dp.toPx(), o)
            }
            dibujarPin(o, m, medidor)
        }

        dibujarBrujula(medidor)
        dibujarEscala(proy, medidor)
    }
}

private fun DrawScope.dibujarFondo(proy: Proyeccion, proyBase: Proyeccion, zoom: Float) {
    // Pampa del altiplano: degradado verde-ocre con "curvas de nivel" y una cuadrícula tenue.
    drawRect(Brush.linearGradient(listOf(Color(0xFFEAF4E4), Color(0xFFF4F1E2), Color(0xFFE3F0EC))))
    val centros = listOf(Offset(0.2f, 0.3f), Offset(0.78f, 0.22f), Offset(0.62f, 0.78f), Offset(0.12f, 0.85f))
    centros.forEachIndexed { idx, c ->
        // Anclado a coordenadas reales para que el relieve se mueva junto con el mapa.
        val centro = proy.aPantalla(proyBase.aGeo(Offset(size.width * c.x, size.height * c.y)))
        for (anillo in 1..6) {
            val r = (anillo * 26 + idx * 9).dp.toPx() * zoom
            val path = Path()
            var a = 0.0
            while (a <= 2 * PI + 0.1) {
                val ondulacion = 1 + 0.08 * sin(a * 5 + idx + anillo)
                val x = centro.x + (r * ondulacion * cos(a)).toFloat()
                val y = centro.y + (r * 0.7 * ondulacion * sin(a)).toFloat()
                if (a == 0.0) path.moveTo(x, y) else path.lineTo(x, y)
                a += 0.15
            }
            path.close()
            drawPath(path, Color(0xFF7FA37A).copy(alpha = 0.16f), style = Stroke(1.dp.toPx()))
        }
    }
    val paso = 48.dp.toPx()
    var x = 0f
    while (x < size.width) { drawLine(Color(0x0F1E9455), Offset(x, 0f), Offset(x, size.height)); x += paso }
    var y = 0f
    while (y < size.height) { drawLine(Color(0x0F1E9455), Offset(0f, y), Offset(size.width, y)); y += paso }
}

private fun DrawScope.dibujarPin(o: Offset, m: MarcadorMapa, medidor: TextMeasurer) {
    val r = (if (m.resaltado) 17 else 14).dp.toPx()
    // Sombra + gota
    drawCircle(Color.Black.copy(alpha = 0.18f), r, o + Offset(0f, 3.dp.toPx()))
    val gota = Path().apply {
        moveTo(o.x, o.y + r + 7.dp.toPx())
        lineTo(o.x - r * 0.6f, o.y + r * 0.6f)
        lineTo(o.x + r * 0.6f, o.y + r * 0.6f)
        close()
    }
    drawPath(gota, m.color)
    drawCircle(Color.White, r, o)
    drawCircle(m.color, r - 2.5.dp.toPx(), o)
    val texto = medidor.measure(m.rotulo ?: m.numero.toString(), TextStyle(color = Color.White, fontSize = if (m.rotulo != null) 10.sp else 12.sp, fontWeight = FontWeight.Bold))
    drawText(texto, topLeft = o - Offset(texto.size.width / 2f, texto.size.height / 2f))

    val etiqueta = m.etiqueta ?: return
    val medida = medidor.measure(etiqueta, TextStyle(color = Color(0xFF14211B), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    val pad = 6.dp.toPx()
    val ancho = medida.size.width + pad * 2
    val alto = medida.size.height + pad
    val esquina = Offset(o.x - ancho / 2, o.y - r - alto - 6.dp.toPx())
    drawRoundRect(Color.White, esquina, Size(ancho, alto), androidx.compose.ui.geometry.CornerRadius(alto / 2))
    drawRoundRect(m.color, esquina, Size(ancho, alto), androidx.compose.ui.geometry.CornerRadius(alto / 2), style = Stroke(1.5.dp.toPx()))
    drawText(medida, topLeft = esquina + Offset(pad, pad / 2))
}

private fun DrawScope.dibujarBandera(o: Offset, color: Color, medidor: TextMeasurer, texto: String) {
    val alto = 26.dp.toPx()
    drawLine(Color(0xFF14211B), o, o - Offset(0f, alto), strokeWidth = 2.dp.toPx())
    val medida = medidor.measure(texto, TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    val pad = 5.dp.toPx()
    drawRect(color, o - Offset(0f, alto), Size(medida.size.width + pad * 2, medida.size.height + pad))
    drawText(medida, topLeft = o - Offset(-pad, alto - pad / 2))
    drawCircle(Color(0xFF14211B), 3.dp.toPx(), o)
}

private fun DrawScope.dibujarBrujula(medidor: TextMeasurer) {
    val c = Offset(size.width - 26.dp.toPx(), 26.dp.toPx())
    val r = 15.dp.toPx()
    drawCircle(Color.White.copy(alpha = 0.92f), r, c)
    val norte = Path().apply { moveTo(c.x, c.y - r * 0.75f); lineTo(c.x - r * 0.3f, c.y); lineTo(c.x + r * 0.3f, c.y); close() }
    val sur = Path().apply { moveTo(c.x, c.y + r * 0.75f); lineTo(c.x - r * 0.3f, c.y); lineTo(c.x + r * 0.3f, c.y); close() }
    drawPath(norte, Color(0xFFD14343))
    drawPath(sur, Color(0xFF7C9086))
    val n = medidor.measure("N", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14211B)))
    drawText(n, topLeft = Offset(c.x - n.size.width / 2f, c.y - r - n.size.height))
}

private fun DrawScope.dibujarEscala(proy: Proyeccion, medidor: TextMeasurer) {
    // Barra de escala "redonda" (50 m, 100 m, 200 m, 500 m, 1 km, ...) de hasta ~90 dp.
    val maxPx = 90.dp.toPx()
    val metrosMax = proy.metrosPorPixel * maxPx
    val magnitud = 10.0.pow(kotlin.math.floor(kotlin.math.log10(metrosMax)))
    val metros = listOf(5.0, 2.0, 1.0).map { it * magnitud }.first { it <= metrosMax }
    val px = (metros / proy.metrosPorPixel).toFloat()
    val inicio = Offset(12.dp.toPx(), size.height - 14.dp.toPx())
    drawLine(Color(0xFF14211B), inicio, inicio + Offset(px, 0f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color(0xFF14211B), inicio, inicio - Offset(0f, 6.dp.toPx()), strokeWidth = 2.dp.toPx())
    drawLine(Color(0xFF14211B), inicio + Offset(px, 0f), inicio + Offset(px, -6.dp.toPx()), strokeWidth = 2.dp.toPx())
    val texto = if (metros >= 1000) "${formatear(metros / 1000)} km" else "${metros.toInt()} m"
    val medida = medidor.measure(texto, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14211B)))
    drawText(medida, topLeft = inicio + Offset(px + 6.dp.toPx(), -medida.size.height / 2f - 2.dp.toPx()))
}

private fun formatear(v: Double): String = if (abs(v - v.toInt()) < 1e-6) v.toInt().toString() else ((v * 10).toInt() / 10.0).toString()

/** Distancia aproximada en km entre dos puntos (haversine), para mostrar el largo del camino. */
fun distanciaKm(a: PuntoGeo, b: PuntoGeo): Double {
    val r = 6371.0
    val dLat = (b.lat - a.lat) * PI / 180
    val dLng = (b.lng - a.lng) * PI / 180
    val h = sin(dLat / 2).pow(2) + cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLng / 2).pow(2)
    return 2 * r * kotlin.math.asin(sqrt(min(1.0, max(0.0, h))))
}

fun largoKm(puntos: List<PuntoGeo>): Double = puntos.zipWithNext { a, b -> distanciaKm(a, b) }.sum()
