package com.example.ecolacteoshuata.presentation.common.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Ambar
import com.example.ecolacteoshuata.core.theme.AmbarFondo
import com.example.ecolacteoshuata.core.theme.AmbarTexto
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.DialogoConfirmacion
import com.example.ecolacteoshuata.core.ui.TonoDialogo
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.LocalTemaRol
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.degradado
import com.example.ecolacteoshuata.domain.model.PuntoGeo

@Composable
fun MapaRutaScreen(
    state: MapaRutaUiState,
    onSeleccionarRuta: (String) -> Unit,
    onEnfocarParada: (String?) -> Unit,
    onTocarMapa: (PuntoGeo) -> Unit,
    onAlternarUbicar: (String) -> Unit,
    onEmpezarDibujo: () -> Unit,
    onDeshacerPunto: () -> Unit,
    onGuardarDibujo: () -> Unit,
    onCancelarModo: () -> Unit,
    onTrazarPorParadas: () -> Unit,
    onLimpiarTrazo: () -> Unit,
    onMoverParada: (String, Int) -> Unit,
    onConsumirMensaje: () -> Unit,
    onBuscar: (String) -> Unit = {},
    onIrARecojo: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        if (state.rutas.isEmpty()) {
            EmptyState(
                titulo = "Sin rutas",
                mensaje = if (state.editable) "Todavía no hay rutas creadas. Usa \"Nueva ruta\" para crear la primera."
                else "Todavía no tienes una ruta activa asignada. Pide a planta que te asigne una.",
                modifier = Modifier.padding(24.dp),
            )
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 14.dp)) {
            RecorridoRuta(
                state = state,
                onSeleccionarRuta = onSeleccionarRuta,
                onEnfocarParada = onEnfocarParada,
                onTocarMapa = onTocarMapa,
                onAlternarUbicar = onAlternarUbicar,
                onEmpezarDibujo = onEmpezarDibujo,
                onDeshacerPunto = onDeshacerPunto,
                onGuardarDibujo = onGuardarDibujo,
                onCancelarModo = onCancelarModo,
                onTrazarPorParadas = onTrazarPorParadas,
                onLimpiarTrazo = onLimpiarTrazo,
                onMoverParada = onMoverParada,
                onBuscar = onBuscar,
                onIrARecojo = onIrARecojo,
            )
        }

        ConfirmToastHost(mensaje = state.mensaje, onDismiss = onConsumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Selector de ruta, mapa, acciones y paradas, sin scroll propio: lo usan el "Mapa de recojo" del
 * acopiador y la sección "Recorrido del camioncito" de Rutas (planta / administrador).
 */
@Composable
fun RecorridoRuta(
    state: MapaRutaUiState,
    onSeleccionarRuta: (String) -> Unit,
    onEnfocarParada: (String?) -> Unit,
    onTocarMapa: (PuntoGeo) -> Unit,
    onAlternarUbicar: (String) -> Unit,
    onEmpezarDibujo: () -> Unit,
    onDeshacerPunto: () -> Unit,
    onGuardarDibujo: () -> Unit,
    onCancelarModo: () -> Unit,
    onTrazarPorParadas: () -> Unit,
    onLimpiarTrazo: () -> Unit,
    onMoverParada: (String, Int) -> Unit,
    onBuscar: (String) -> Unit = {},
    onIrARecojo: (() -> Unit)? = null,
) {
    val tema = LocalTemaRol.current
    val uri = LocalUriHandler.current
    val camara = rememberEstadoCamaraMapa()
    var confirmarBorrado by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Selector de ruta
            if (state.rutas.size > 1 || state.editable) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.rutas.forEach { r ->
                        val activa = r.id == state.ruta?.id
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (activa) tema.degradado() else androidx.compose.ui.graphics.SolidColor(Blanco))
                                .border(1.dp, if (activa) Color.Transparent else Borde, RoundedCornerShape(16.dp))
                                .clickable { onSeleccionarRuta(r.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(r.nombre, style = CuerpoMedioStyle, color = if (activa) Blanco else Negro, maxLines = 1)
                            Text(
                                state.nombresAcopiador[r.acopiadorId] ?: "Sin acopiador",
                                style = EtiquetaMayusculaStyle.copy(fontSize = 9.sp),
                                color = if (activa) Blanco.copy(alpha = 0.8f) else GrisTexto,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Estado de la ruta
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Insignia(Icons.Filled.Map, state.ruta?.nombre.orEmpty(), tema.acento)
                if (!state.editable) {
                    Insignia(Icons.Filled.CheckCircle, "${state.recogidos} / ${state.paradas.size} recogidos", VerdePrimario)
                } else {
                    Insignia(Icons.Filled.PinDrop, "${state.paradas.size} paradas", tema.acento)
                }
                val km = largoKm(state.ruta?.trazo.orEmpty())
                if (km > 0) Insignia(Icons.Filled.Route, "Camino ≈ ${(km * 10).toInt() / 10.0} km", tema.acento)
                if (state.ruta?.trazo.isNullOrEmpty()) {
                    Insignia(Icons.Filled.Warning, if (state.editable) "Sin camino trazado" else "Planta aún no trazó el camino", Rojo)
                }
                if (state.sinUbicacion > 0) Insignia(Icons.Filled.LocationOff, "${state.sinUbicacion} sin punto en el mapa", Ambar)
            }

            // Mapa
            val forma = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(360.dp)
                    .shadow(8.dp, forma, ambientColor = tema.acento, spotColor = tema.acento)
                    .clip(forma)
                    .border(2.dp, Blanco, forma),
            ) {
                val ubicandoId = (state.modo as? ModoMapa.Ubicando)?.proveedorId
                val marcadores = state.paradas.mapNotNull { parada ->
                    val punto = parada.punto ?: return@mapNotNull null
                    MarcadorMapa(
                        id = parada.proveedor.id,
                        numero = parada.numero,
                        punto = punto,
                        color = when {
                            parada.proveedor.id == ubicandoId -> Ambar
                            !state.editable && parada.recogido -> VerdePrimario
                            !state.editable -> Rojo
                            else -> tema.acento
                        },
                        resaltado = parada.proveedor.id == state.paradaEnfocadaId || parada.proveedor.id == ubicandoId,
                        etiqueta = if (parada.proveedor.id == state.paradaEnfocadaId) parada.proveedor.nombreCompleto else null,
                    )
                } + state.otrosProveedores.mapNotNull { item ->
                    val punto = item.punto ?: return@mapNotNull null
                    val id = item.proveedor.id
                    MarcadorMapa(
                        id = id,
                        numero = 0,
                        punto = punto,
                        color = if (id == ubicandoId) Ambar else GrisTexto,
                        resaltado = id == state.paradaEnfocadaId || id == ubicandoId,
                        etiqueta = if (id == state.paradaEnfocadaId) item.proveedor.nombreCompleto else null,
                        rotulo = iniciales(item.proveedor.nombreCompleto),
                    )
                }
                MapaCanvas(
                    marcadores = marcadores,
                    trazo = state.trazoVisible,
                    colorTrazo = tema.acento,
                    camara = camara,
                    trazoEnEdicion = state.modo is ModoMapa.Dibujando,
                    onTocarMarcador = { id -> onEnfocarParada(if (state.paradaEnfocadaId == id) null else id) },
                    onTocarMapa = if (state.modo == ModoMapa.Navegar) null else onTocarMapa,
                    trazosReferencia = state.otrosTrazos,
                    modifier = Modifier.fillMaxSize(),
                )

                // Aviso del modo de edición
                val modo = state.modo
                if (modo != ModoMapa.Navegar) Box(modifier = Modifier.align(Alignment.TopCenter).padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .shadow(6.dp, RoundedCornerShape(16.dp))
                            .background(AmbarFondo, RoundedCornerShape(16.dp))
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val texto = when (modo) {
                            is ModoMapa.Ubicando -> "Toca la finca de ${state.nombreUbicando ?: "el proveedor"}"
                            is ModoMapa.Dibujando -> "Toca el mapa para trazar · ${modo.puntos.size} puntos"
                            ModoMapa.Navegar -> ""
                        }
                        Text(texto, style = CuerpoMedioStyle.copy(fontSize = 12.sp), color = AmbarTexto, modifier = Modifier.weight(1f, fill = false))
                        if (modo is ModoMapa.Dibujando) {
                            BotonMini(Icons.AutoMirrored.Filled.Undo, "Deshacer", AmbarTexto, onDeshacerPunto)
                            BotonMini(Icons.Filled.Save, "Guardar", VerdePrimario, onGuardarDibujo)
                        }
                        BotonMini(Icons.Filled.Close, "Cancelar", RojoTexto, onCancelarModo)
                    }
                }

                // Zoom
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp)).background(Blanco, RoundedCornerShape(14.dp)),
                ) {
                    BotonMini(Icons.Filled.Add, "Acercar", Negro) { camara.acercar() }
                    BotonMini(Icons.Filled.Remove, "Alejar", Negro) { camara.alejar() }
                    BotonMini(Icons.Filled.CenterFocusStrong, "Centrar", tema.acento) { camara.centrar() }
                }

                // Leyenda (acopiador)
                if (!state.editable) {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                            .background(Blanco.copy(alpha = 0.92f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PuntoLeyenda(VerdePrimario, "Recogido")
                        PuntoLeyenda(Rojo, "Pendiente")
                    }
                }
            }

            // Acciones
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.editable) {
                    BotonAccion(Icons.Filled.Route, "Trazar según paradas", tema.acento, onTrazarPorParadas)
                    BotonAccion(Icons.Filled.Draw, "Dibujar camino", tema.acento, onEmpezarDibujo)
                    BotonAccion(Icons.Filled.Delete, "Borrar camino", Rojo) { confirmarBorrado = true }
                } else {
                    val pendientes = state.paradas.filter { !it.recogido }.mapNotNull { it.punto }
                    if (pendientes.isNotEmpty()) {
                        BotonAccion(Icons.Filled.Directions, "Navegar pendientes (${pendientes.size})", tema.acento) {
                            uri.openUri(urlNavegacion(pendientes))
                        }
                    }
                    if (onIrARecojo != null) BotonAccion(Icons.Filled.CheckCircle, "Registrar recojo", VerdePrimario, onIrARecojo)
                }
            }

            if (state.editable) {
                PanelProveedores(
                    state = state,
                    onBuscar = onBuscar,
                    onEnfocar = { id -> onEnfocarParada(if (state.paradaEnfocadaId == id) null else id) },
                    onUbicar = onAlternarUbicar,
                    onMoverParada = onMoverParada,
                )
            } else {
                TituloSeccion("Paradas", modifier = Modifier.padding(horizontal = 16.dp))
                if (state.paradas.isEmpty()) {
                    Text("Esta ruta no tiene proveedores.", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.paradas.forEachIndexed { i, parada ->
                        StaggeredListItem(index = i) {
                            FilaParada(
                                parada = parada,
                                esUltima = i == state.paradas.lastIndex,
                                editable = false,
                                enfocada = parada.proveedor.id == state.paradaEnfocadaId,
                                ubicando = false,
                                onClick = { onEnfocarParada(parada.proveedor.id) },
                                onUbicar = {},
                                onSubir = null,
                                onBajar = null,
                                onNavegar = parada.punto?.let { p -> { uri.openUri(urlNavegacion(listOf(p))) } },
                            )
                        }
                    }
                }
            }
        }

    if (confirmarBorrado) {
        DialogoConfirmacion(
            titulo = "¿Borrar el camino?",
            mensaje = "Se borrará el camino trazado de ${state.ruta?.nombre ?: "esta ruta"}. Podrás volver a trazarlo cuando quieras.",
            icono = Icons.Filled.Route,
            tono = TonoDialogo.PELIGRO,
            textoConfirmar = "Borrar",
            onConfirmar = { confirmarBorrado = false; onLimpiarTrazo() },
            onCancelar = { confirmarBorrado = false },
        )
    }
}

/** Abre Google Maps (app o web) con indicaciones: la última parada es el destino, el resto son escalas. */
private fun urlNavegacion(puntos: List<PuntoGeo>): String {
    fun f(p: PuntoGeo) = "${p.lat},${p.lng}"
    val destino = f(puntos.last())
    val escalas = puntos.dropLast(1).joinToString("%7C") { f(it) }
    return buildString {
        append("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=").append(destino)
        if (escalas.isNotEmpty()) append("&waypoints=").append(escalas)
    }
}

@Composable
private fun FilaParada(
    parada: ParadaMapa,
    esUltima: Boolean,
    editable: Boolean,
    enfocada: Boolean,
    ubicando: Boolean,
    onClick: () -> Unit,
    onUbicar: () -> Unit,
    onSubir: (() -> Unit)?,
    onBajar: (() -> Unit)?,
    onNavegar: (() -> Unit)?,
) {
    val tema = LocalTemaRol.current
    val color = when {
        ubicando -> Ambar
        !editable && parada.recogido -> VerdePrimario
        !editable -> Rojo
        else -> tema.acento
    }
    Row(modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // Línea de tiempo
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
            Box(
                Modifier.size(32.dp).background(color, CircleShape).border(3.dp, Blanco, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!editable && parada.recogido) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Blanco, modifier = Modifier.size(18.dp))
                } else {
                    Text(parada.numero.toString(), color = Blanco, style = CuerpoMedioStyle)
                }
            }
            if (!esUltima) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(color.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
            }
        }
        SectionCard(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 10.dp)
                .then(if (enfocada) Modifier.border(2.dp, color, RoundedCornerShape(20.dp)) else Modifier)
                .clickable(onClick = onClick),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(parada.proveedor.nombreCompleto, style = CuerpoMedioStyle.copy(fontSize = 14.sp), color = Negro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        parada.proveedor.fincaUbicacion.ifBlank { "Sin ubicación registrada" },
                        style = CuerpoStyle.copy(fontSize = 12.sp),
                        color = GrisTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        parada.punto == null -> Text("Sin punto en el mapa", style = EtiquetaMayusculaStyle, color = RojoTexto)
                        !editable -> Text(if (parada.recogido) "RECOGIDO" else "PENDIENTE", style = EtiquetaMayusculaStyle, color = color)
                    }
                }
                if (editable) {
                    BotonMini(Icons.Filled.PinDrop, "Marcar ubicación en el mapa", if (ubicando) Ambar else tema.acento, onUbicar)
                    BotonMini(Icons.Filled.KeyboardArrowUp, "Subir", if (onSubir != null) Negro else Borde) { onSubir?.invoke() }
                    BotonMini(Icons.Filled.KeyboardArrowDown, "Bajar", if (onBajar != null) Negro else Borde) { onBajar?.invoke() }
                } else if (onNavegar != null) {
                    BotonMini(Icons.Filled.Directions, "Cómo llegar", tema.acento, onNavegar)
                }
            }
        }
    }
}

@Composable
private fun Insignia(icono: ImageVector, texto: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(icono, null, tint = color, modifier = Modifier.size(14.dp))
        Text(texto, style = CuerpoMedioStyle.copy(fontSize = 11.5.sp), color = color, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
    }
}

@Composable
private fun PuntoLeyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(texto, style = EtiquetaMayusculaStyle.copy(fontSize = 9.sp), color = Negro, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
internal fun BotonMini(icono: ImageVector, descripcion: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = color, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun BotonAccion(icono: ImageVector, texto: String, color: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icono, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = CuerpoMedioStyle, color = color)
    }
}

internal fun iniciales(nombre: String): String =
    nombre.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/**
 * Panel "Proveedores" junto al mapa (planta / administrador), como en Laravel: todos los proveedores,
 * primero las paradas de la ruta elegida en orden; se puede marcar su finca en el mapa, verlo y
 * cambiar el orden de las paradas.
 */
@Composable
private fun PanelProveedores(
    state: MapaRutaUiState,
    onBuscar: (String) -> Unit,
    onEnfocar: (String) -> Unit,
    onUbicar: (String) -> Unit,
    onMoverParada: (String, Int) -> Unit,
) {
    val tema = LocalTemaRol.current
    val ubicandoId = (state.modo as? ModoMapa.Ubicando)?.proveedorId
    val totalParadas = state.paradas.size

    TituloSeccion("Proveedores", modifier = Modifier.padding(horizontal = 16.dp)) {
        Insignia(Icons.Filled.PinDrop, state.proveedoresPanel.size.toString(), tema.acento)
    }
    AppTextField(
        valor = state.busqueda,
        onValorCambia = onBuscar,
        etiqueta = "Buscar proveedor...",
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (state.proveedoresPanel.isEmpty()) {
        Text(
            if (state.busqueda.isNotBlank()) "Ningún proveedor coincide con la búsqueda." else "Todavía no hay proveedores registrados.",
            style = CuerpoStyle,
            color = GrisTexto,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.proveedoresPanel.forEach { item ->
            val p = item.proveedor
            val ubicando = p.id == ubicandoId
            val parada = item.parada
            val color = when {
                ubicando -> Ambar
                parada != null -> tema.acento
                else -> GrisTexto
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (ubicando) AmbarFondo else if (parada != null) tema.acento.copy(alpha = 0.08f) else Blanco)
                    .border(
                        if (p.id == state.paradaEnfocadaId) 2.dp else 1.dp,
                        if (p.id == state.paradaEnfocadaId || ubicando) color else Borde,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Box(Modifier.size(32.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(parada?.toString() ?: iniciales(p.nombreCompleto), color = Blanco, style = CuerpoMedioStyle.copy(fontSize = if (parada != null) 14.sp else 11.sp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(p.nombreCompleto, style = CuerpoMedioStyle.copy(fontSize = 14.sp), color = Negro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.fincaUbicacion.ifBlank { "Sin ubicación" }, style = CuerpoStyle.copy(fontSize = 12.sp), color = GrisTexto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (parada == null) {
                        Text(item.rutas, style = CuerpoStyle.copy(fontSize = 12.sp), color = GrisTexto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.punto == null) Text("Sin punto en el mapa", style = EtiquetaMayusculaStyle, color = RojoTexto)
                }
                BotonMini(Icons.Filled.PinDrop, "Marcar ubicación en el mapa", if (ubicando) Ambar else tema.acento) { onUbicar(p.id) }
                if (item.punto != null) {
                    BotonMini(Icons.Filled.Visibility, "Ver dónde está respecto al camino", tema.acento) { onEnfocar(p.id) }
                }
                if (parada != null) {
                    BotonMini(Icons.Filled.KeyboardArrowUp, "Subir", if (parada > 1) Negro else Borde) { if (parada > 1) onMoverParada(p.id, -1) }
                    BotonMini(Icons.Filled.KeyboardArrowDown, "Bajar", if (parada < totalParadas) Negro else Borde) { if (parada < totalParadas) onMoverParada(p.id, 1) }
                }
            }
        }
    }
}
