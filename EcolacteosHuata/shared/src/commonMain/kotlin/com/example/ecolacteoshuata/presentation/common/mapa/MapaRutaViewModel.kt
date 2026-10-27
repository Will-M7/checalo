package com.example.ecolacteoshuata.presentation.common.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.domain.usecase.GetProveedoresRecogidosHoyUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarTrazoRutaUseCase
import com.example.ecolacteoshuata.domain.usecase.MoverParadaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveAcopiadoresUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProveedoresUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTodasLasRutasUseCase
import com.example.ecolacteoshuata.domain.usecase.UbicarProveedorEnMapaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ParadaMapa(val proveedor: Proveedor, val numero: Int, val recogido: Boolean) {
    val punto: PuntoGeo? get() = proveedor.latitud?.let { lat -> proveedor.longitud?.let { lng -> PuntoGeo(lat, lng) } }
}

/** Fila del panel de proveedores junto al mapa (planta / administrador), como en Laravel. */
data class ProveedorMapa(val proveedor: Proveedor, val parada: Int?, val rutas: String) {
    val punto: PuntoGeo? get() = proveedor.latitud?.let { lat -> proveedor.longitud?.let { lng -> PuntoGeo(lat, lng) } }
}

/** Qué hace un toque sobre el mapa en modo edición. */
sealed interface ModoMapa {
    data object Navegar : ModoMapa
    data class Ubicando(val proveedorId: String) : ModoMapa
    data class Dibujando(val puntos: List<PuntoGeo>) : ModoMapa
}

data class MapaRutaUiState(
    val editable: Boolean = false,
    val rutas: List<Ruta> = emptyList(),
    val ruta: Ruta? = null,
    val acopiadorNombre: String = "",
    val nombresAcopiador: Map<String, String> = emptyMap(),
    val paradas: List<ParadaMapa> = emptyList(),
    val modo: ModoMapa = ModoMapa.Navegar,
    val paradaEnfocadaId: String? = null,
    val mensaje: String? = null,
    /** Todos los proveedores: primero las paradas de la ruta elegida en orden, después el resto por nombre. */
    val proveedoresPanel: List<ProveedorMapa> = emptyList(),
    /** Proveedores con punto en el mapa que no son parada de la ruta elegida. */
    val otrosProveedores: List<ProveedorMapa> = emptyList(),
    /** Caminos de las demás rutas, de referencia debajo de la ruta elegida. */
    val otrosTrazos: List<List<PuntoGeo>> = emptyList(),
    val busqueda: String = "",
    val nombreUbicando: String? = null,
) {
    val trazoVisible: List<PuntoGeo>
        get() = (modo as? ModoMapa.Dibujando)?.puntos ?: ruta?.trazo.orEmpty()
    val recogidos: Int get() = paradas.count { it.recogido }
    val sinUbicacion: Int get() = paradas.count { it.punto == null }
}

/**
 * [editable] = true para planta y administrador (ubicar fincas, ordenar paradas, trazar el
 * camino); false para el acopiador, que solo ve sus rutas activas y qué paradas ya recogió.
 */
class MapaRutaViewModel(
    private val editable: Boolean,
    private val acopiadorId: String?,
    observeRutas: ObserveTodasLasRutasUseCase,
    observeProveedores: ObserveProveedoresUseCase,
    observeAcopiadores: ObserveAcopiadoresUseCase,
    private val getRecogidosHoy: GetProveedoresRecogidosHoyUseCase,
    private val moverParadaUseCase: MoverParadaUseCase,
    private val guardarTrazoUseCase: GuardarTrazoRutaUseCase,
    private val ubicarProveedorUseCase: UbicarProveedorEnMapaUseCase,
) : ViewModel() {
    private val rutaSeleccionadaId = MutableStateFlow<String?>(null)
    private val modo = MutableStateFlow<ModoMapa>(ModoMapa.Navegar)
    private val enfocada = MutableStateFlow<String?>(null)
    private val mensaje = MutableStateFlow<String?>(null)
    private val recogidos = MutableStateFlow<Set<String>>(emptySet())
    private val busqueda = MutableStateFlow("")

    private val base = combine(observeRutas(), observeProveedores(), observeAcopiadores()) { rutas, proveedores, acopiadores ->
        Triple(rutas, proveedores, acopiadores)
    }

    private val ui = combine(modo, enfocada, mensaje, recogidos, busqueda) { m, e, msg, rec, b -> Extras(m, e, msg, rec, b) }

    val state: StateFlow<MapaRutaUiState> = combine(base, rutaSeleccionadaId, ui) { (rutas, proveedores, acopiadores), seleccion, extras ->
        val visibles = if (acopiadorId != null) rutas.filter { it.acopiadorId == acopiadorId && it.activa } else rutas
        val ruta = visibles.firstOrNull { it.id == seleccion } ?: visibles.firstOrNull()
        val porId = proveedores.associateBy { it.id }
        val nombres = acopiadores.associate { it.id to it.nombreCompleto }
        val orden = ruta?.proveedorIds.orEmpty().withIndex().associate { (i, id) -> id to i + 1 }
        val rutasDe = rutas.flatMap { r -> r.proveedorIds.map { it to r.nombre } }.groupBy({ it.first }, { it.second })
        val panel = if (!editable) emptyList() else proveedores
            .map { ProveedorMapa(it, orden[it.id], rutasDe[it.id].orEmpty().joinToString(", ").ifBlank { "Sin ruta" }) }
            .sortedWith(compareBy<ProveedorMapa> { it.parada ?: Int.MAX_VALUE }.thenBy { it.proveedor.nombreCompleto })
        val ubicandoId = (extras.modo as? ModoMapa.Ubicando)?.proveedorId
        MapaRutaUiState(
            editable = editable,
            rutas = visibles,
            ruta = ruta,
            acopiadorNombre = ruta?.let { nombres[it.acopiadorId] }.orEmpty(),
            nombresAcopiador = nombres,
            paradas = ruta?.proveedorIds.orEmpty().mapNotNull { porId[it] }.mapIndexed { i, p ->
                ParadaMapa(p, i + 1, p.id in extras.recogidos)
            },
            modo = extras.modo,
            paradaEnfocadaId = extras.enfocada,
            mensaje = extras.mensaje,
            proveedoresPanel = panel.filter { extras.busqueda.isBlank() || it.proveedor.nombreCompleto.contains(extras.busqueda.trim(), ignoreCase = true) },
            otrosProveedores = panel.filter { it.parada == null && it.punto != null },
            otrosTrazos = if (editable) visibles.filter { it.id != ruta?.id }.map { it.trazo } else emptyList(),
            busqueda = extras.busqueda,
            nombreUbicando = ubicandoId?.let { id -> proveedores.firstOrNull { it.id == id }?.nombreCompleto },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapaRutaUiState(editable = editable))

    private data class Extras(val modo: ModoMapa, val enfocada: String?, val mensaje: String?, val recogidos: Set<String>, val busqueda: String)

    /** Recalcula qué paradas ya se recogieron (se llama cada vez que se abre el mapa). */
    fun refrescarRecogidos() {
        val id = acopiadorId ?: return
        viewModelScope.launch { recogidos.value = getRecogidosHoy(id) }
    }

    fun seleccionarRuta(id: String) {
        rutaSeleccionadaId.value = id
        modo.value = ModoMapa.Navegar
        enfocada.value = null
    }

    fun enfocarParada(proveedorId: String?) {
        enfocada.value = proveedorId
    }

    fun buscar(texto: String) {
        busqueda.value = texto
    }

    fun consumirMensaje() {
        mensaje.value = null
    }

    // --- Edición (planta / administrador) ---

    fun alternarUbicar(proveedorId: String) {
        if (!editable) return
        modo.value = if ((modo.value as? ModoMapa.Ubicando)?.proveedorId == proveedorId) ModoMapa.Navegar else ModoMapa.Ubicando(proveedorId)
    }

    fun empezarDibujo() {
        if (!editable) return
        modo.value = ModoMapa.Dibujando(emptyList())
    }

    fun deshacerPunto() {
        val actual = modo.value as? ModoMapa.Dibujando ?: return
        modo.value = actual.copy(puntos = actual.puntos.dropLast(1))
    }

    fun cancelarModo() {
        modo.value = ModoMapa.Navegar
    }

    /** Un toque en el mapa: agrega un punto al camino o ubica la finca, según el modo. */
    fun tocarMapa(punto: PuntoGeo) {
        when (val m = modo.value) {
            is ModoMapa.Dibujando -> modo.value = m.copy(puntos = m.puntos + punto)
            is ModoMapa.Ubicando -> viewModelScope.launch {
                when (val r = ubicarProveedorUseCase(m.proveedorId, punto)) {
                    is AppResult.Success -> mensaje.value = "Ubicación del proveedor guardada."
                    is AppResult.Error -> mensaje.value = r.message
                }
                modo.value = ModoMapa.Navegar
            }
            ModoMapa.Navegar -> Unit
        }
    }

    fun guardarDibujo() {
        val ruta = state.value.ruta ?: return
        val puntos = (modo.value as? ModoMapa.Dibujando)?.puntos ?: return
        viewModelScope.launch {
            guardarTrazoUseCase(ruta.id, puntos)
            modo.value = ModoMapa.Navegar
            mensaje.value = "Camino de la ruta guardado."
        }
    }

    fun trazarPorParadas() {
        val s = state.value
        val ruta = s.ruta ?: return
        val puntos = s.paradas.mapNotNull { it.punto }
        viewModelScope.launch {
            guardarTrazoUseCase(ruta.id, puntos)
            mensaje.value = if (puntos.isEmpty()) "Ninguna parada tiene ubicación en el mapa." else "Camino generado según el orden de las paradas."
        }
    }

    fun limpiarTrazo() {
        val ruta = state.value.ruta ?: return
        viewModelScope.launch {
            guardarTrazoUseCase(ruta.id, emptyList())
            mensaje.value = "Camino borrado."
        }
    }

    fun moverParada(proveedorId: String, direccion: Int) {
        val ruta = state.value.ruta ?: return
        viewModelScope.launch { moverParadaUseCase(ruta, proveedorId, direccion) }
    }
}
