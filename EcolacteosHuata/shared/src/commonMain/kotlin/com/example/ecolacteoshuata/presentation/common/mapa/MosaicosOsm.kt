package com.example.ecolacteoshuata.presentation.common.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Mosaicos de OpenStreetMap, los mismos que usa el mapa de Laravel (Leaflet). Se descargan
 * cuando hay internet y se guardan en memoria; sin conexión el mapa sigue funcionando con el
 * fondo dibujado debajo.
 */
class MosaicosOsm(private val scope: CoroutineScope) {
    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
        }
    }

    /** Leído mientras se dibuja: cuando llega un mosaico el lienzo se vuelve a pintar solo. */
    private val cargados = mutableStateMapOf<String, ImageBitmap>()
    private val orden = ArrayDeque<String>()
    private val pedidos = mutableSetOf<String>()

    /** Devuelve el mosaico si ya está en memoria; si no, lo pide y devuelve `null` por ahora. */
    fun obtener(z: Int, x: Int, y: Int): ImageBitmap? {
        val clave = "$z/$x/$y"
        cargados[clave]?.let { return it }
        if (pedidos.add(clave)) {
            scope.launch(Dispatchers.Default) {
                val imagen = runCatching { descargar(clave) }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (imagen == null) {
                        pedidos.remove(clave) // se reintenta la próxima vez que se dibuje
                    } else {
                        cargados[clave] = imagen
                        orden.addLast(clave)
                        while (orden.size > MAX_EN_MEMORIA) {
                            val viejo = orden.removeFirst()
                            cargados.remove(viejo)
                            pedidos.remove(viejo)
                        }
                    }
                }
            }
        }
        return null
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun descargar(clave: String): ImageBitmap? {
        val respuesta = client.get("https://tile.openstreetmap.org/$clave.png") {
            // La política de uso de OpenStreetMap pide identificar la aplicación.
            header(HttpHeaders.UserAgent, "EcolacteosHuata/1.0 (app movil de acopio)")
        }
        if (!respuesta.status.isSuccess()) return null
        return respuesta.readRawBytes().decodeToImageBitmap()
    }

    fun cerrar() = client.close()

    private companion object {
        const val MAX_EN_MEMORIA = 160
    }
}

@Composable
fun rememberMosaicosOsm(): MosaicosOsm {
    val scope = rememberCoroutineScope()
    val mosaicos = remember { MosaicosOsm(scope) }
    DisposableEffect(mosaicos) { onDispose { mosaicos.cerrar() } }
    return mosaicos
}

private const val METROS_POR_PIXEL_Z0 = 156_543.03392
private const val MAX_MOSAICOS = 90

private fun columna(lng: Double, n: Int): Double = (lng + 180.0) / 360.0 * n
private fun fila(lat: Double, n: Int): Double {
    val rad = lat * PI / 180
    return (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2 * n
}
private fun lngDeColumna(x: Int, n: Int): Double = x.toDouble() / n * 360.0 - 180.0
private fun latDeFila(y: Int, n: Int): Double = atan(sinh(PI * (1 - 2.0 * y / n))) * 180 / PI

/**
 * Pinta los mosaicos visibles. Cada mosaico se ubica proyectando sus esquinas con la misma
 * [aPantalla] que usan las paradas, así el mapa y los pines coinciden siempre.
 */
internal fun DrawScope.dibujarMosaicos(
    mosaicos: MosaicosOsm,
    aPantalla: (PuntoGeo) -> Offset,
    aGeo: (Offset) -> PuntoGeo,
    metrosPorPixel: Double,
    medidor: TextMeasurer,
) {
    val arribaIzq = aGeo(Offset.Zero)
    val abajoDer = aGeo(Offset(size.width, size.height))
    val latCentro = (arribaIzq.lat + abajoDer.lat) / 2
    // Nivel de zoom en el que un pixel del mosaico mide más o menos lo mismo que un dp en pantalla.
    val metrosPorDp = metrosPorPixel * density
    val z = (log2(METROS_POR_PIXEL_Z0 * cos(latCentro * PI / 180) / metrosPorDp).roundToInt() + 1).coerceIn(2, 19)
    val n = 1 shl z

    val x0 = floor(columna(arribaIzq.lng, n)).toInt()
    val x1 = floor(columna(abajoDer.lng, n)).toInt()
    val y0 = floor(fila(arribaIzq.lat, n)).toInt().coerceAtLeast(0)
    val y1 = floor(fila(abajoDer.lat, n)).toInt().coerceAtMost(n - 1)
    if ((x1 - x0 + 1) * (y1 - y0 + 1) > MAX_MOSAICOS) return

    for (x in x0..x1) {
        for (y in y0..y1) {
            val imagen = mosaicos.obtener(z, ((x % n) + n) % n, y) ?: continue
            val a = aPantalla(PuntoGeo(latDeFila(y, n), lngDeColumna(x, n)))
            val b = aPantalla(PuntoGeo(latDeFila(y + 1, n), lngDeColumna(x + 1, n)))
            val destino = IntOffset(a.x.toInt(), a.y.toInt())
            drawImage(
                image = imagen,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(imagen.width, imagen.height),
                dstOffset = destino,
                // +1 px para que no se vean rayas entre mosaicos vecinos.
                dstSize = IntSize(b.x.toInt() - destino.x + 1, b.y.toInt() - destino.y + 1),
            )
        }
    }

    val credito = medidor.measure("© OpenStreetMap", TextStyle(fontSize = 9.sp, color = Color(0xFF14211B)))
    val pad = 4.dp.toPx()
    val esquina = Offset(size.width - credito.size.width - pad * 3, size.height - credito.size.height - pad * 2)
    drawRect(Color.White.copy(alpha = 0.8f), esquina, androidx.compose.ui.geometry.Size(credito.size.width + pad * 2, credito.size.height + pad))
    drawText(credito, topLeft = esquina + Offset(pad, pad / 2))
}
