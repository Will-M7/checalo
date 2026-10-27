package com.example.ecolacteoshuata.presentation.admin.rutas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.usecase.AgregarProveedorARutaUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarRutaUseCase
import com.example.ecolacteoshuata.domain.usecase.GetProveedoresRecogidosEnElDiaUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarRutaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveAcopiadoresUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProveedoresUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTodasLasRutasUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTodosLosTurnosUseCase
import com.example.ecolacteoshuata.domain.usecase.QuitarProveedorDeRutaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fila de la tabla de rutas (como en la página Livewire planta.rutas). */
data class RutaFila(val ruta: Ruta, val acopiador: String, val turnos: List<Turno>)

data class CandidatoRuta(val proveedor: Proveedor, val asignado: Boolean, val deOtroAcopiador: Boolean, val acopiadorActual: String?)

data class RutaForm(
    val id: String = "",
    val nombre: String = "",
    val acopiadorId: String = "",
    val activa: Boolean = true,
    val error: String? = null,
)

data class AdminRutasUiState(
    val rutas: List<RutaFila> = emptyList(),
    val acopiadores: List<Acopiador> = emptyList(),
    val detalle: RutaFila? = null,
    val proveedoresDetalle: List<Proveedor> = emptyList(),
    val recogidosHoy: Set<String> = emptySet(),
    val asignar: RutaFila? = null,
    val candidatos: List<CandidatoRuta> = emptyList(),
    val busquedaAsignar: String = "",
    val form: RutaForm? = null,
    val eliminar: RutaFila? = null,
    val mensaje: String? = null,
)

class AdminRutasViewModel(
    observeRutas: ObserveTodasLasRutasUseCase,
    observeAcopiadores: ObserveAcopiadoresUseCase,
    observeProveedores: ObserveProveedoresUseCase,
    observeTurnos: ObserveTodosLosTurnosUseCase,
    private val getRecogidosHoy: GetProveedoresRecogidosEnElDiaUseCase,
    private val guardarRutaUseCase: GuardarRutaUseCase,
    private val eliminarRutaUseCase: EliminarRutaUseCase,
    private val agregarProveedorUseCase: AgregarProveedorARutaUseCase,
    private val quitarProveedorUseCase: QuitarProveedorDeRutaUseCase,
) : ViewModel() {
    private data class Ui(
        val detalleId: String? = null,
        val asignarId: String? = null,
        val busquedaAsignar: String = "",
        val form: RutaForm? = null,
        val eliminarId: String? = null,
        val mensaje: String? = null,
        val recogidosHoy: Set<String> = emptySet(),
    )

    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<AdminRutasUiState> = combine(
        observeRutas(), observeAcopiadores(), observeProveedores(), observeTurnos(), ui,
    ) { rutas, acopiadores, proveedores, turnos, u ->
        val nombres = acopiadores.associate { it.id to it.nombreCompleto }
        val porId = proveedores.associateBy { it.id }
        // En la app los turnos de ruta se guardan por acopiador: la ruta muestra los de su acopiador.
        val filas = rutas.map { r -> RutaFila(r, nombres[r.acopiadorId] ?: "Sin acopiador", turnos.filter { it.acopiadorId == r.acopiadorId }) }
        val detalle = filas.firstOrNull { it.ruta.id == u.detalleId }
        val asignar = filas.firstOrNull { it.ruta.id == u.asignarId }
        val enAlgunaRuta = rutas.flatMap { it.proveedorIds }.toSet()

        AdminRutasUiState(
            rutas = filas,
            acopiadores = acopiadores,
            detalle = detalle,
            proveedoresDetalle = detalle?.ruta?.proveedorIds.orEmpty().mapNotNull { porId[it] },
            recogidosHoy = u.recogidosHoy,
            asignar = asignar,
            // Candidatos: los proveedores del acopiador de la ruta, los que no están en ninguna ruta
            // y los que ya están en esta (aunque sean de otro acopiador), igual que en Laravel.
            candidatos = asignar?.let { fila ->
                proveedores
                    .filter { p ->
                        p.acopiadorId == fila.ruta.acopiadorId || p.id !in enAlgunaRuta || p.id in fila.ruta.proveedorIds
                    }
                    .filter { u.busquedaAsignar.isBlank() || it.nombreCompleto.contains(u.busquedaAsignar.trim(), ignoreCase = true) }
                    .sortedBy { it.nombreCompleto }
                    .map { p ->
                        CandidatoRuta(
                            proveedor = p,
                            asignado = p.id in fila.ruta.proveedorIds,
                            deOtroAcopiador = p.acopiadorId != fila.ruta.acopiadorId,
                            acopiadorActual = p.acopiadorId?.let { nombres[it] },
                        )
                    }
            }.orEmpty(),
            busquedaAsignar = u.busquedaAsignar,
            form = u.form,
            eliminar = filas.firstOrNull { it.ruta.id == u.eliminarId },
            mensaje = u.mensaje,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminRutasUiState())

    private fun actualizar(cambio: (Ui) -> Ui) {
        ui.value = cambio(ui.value)
    }

    fun verDetalle(rutaId: String?) {
        actualizar { it.copy(detalleId = rutaId) }
        if (rutaId != null) viewModelScope.launch {
            val recogidos = getRecogidosHoy()
            actualizar { it.copy(recogidosHoy = recogidos) }
        }
    }

    fun abrirAsignar(rutaId: String?) = actualizar { it.copy(asignarId = rutaId, busquedaAsignar = "") }

    fun buscarAsignar(texto: String) = actualizar { it.copy(busquedaAsignar = texto) }

    fun alternarProveedor(candidato: CandidatoRuta) {
        val rutaId = ui.value.asignarId ?: return
        viewModelScope.launch {
            if (candidato.asignado) {
                quitarProveedorUseCase(rutaId, candidato.proveedor.id)
                actualizar { it.copy(mensaje = "Proveedor quitado de la ruta.") }
            } else {
                agregarProveedorUseCase(rutaId, candidato.proveedor.id)
                actualizar { it.copy(mensaje = "Proveedor asignado a la ruta.") }
            }
        }
    }

    fun nuevaRuta() = actualizar { it.copy(form = RutaForm()) }

    fun editarRuta(ruta: Ruta) = actualizar {
        it.copy(form = RutaForm(id = ruta.id, nombre = ruta.nombre, acopiadorId = ruta.acopiadorId, activa = ruta.activa))
    }

    fun cerrarForm() = actualizar { it.copy(form = null) }

    fun onFormCambia(form: RutaForm) = actualizar { it.copy(form = form.copy(error = null)) }

    fun guardarRuta() {
        val form = ui.value.form ?: return
        viewModelScope.launch {
            val ruta = Ruta(id = form.id, acopiadorId = form.acopiadorId, nombre = form.nombre.trim(), activa = form.activa)
            when (val r = guardarRutaUseCase(ruta)) {
                is AppResult.Success -> actualizar {
                    it.copy(form = null, mensaje = if (form.id.isBlank()) "Ruta creada." else "Ruta actualizada.")
                }
                is AppResult.Error -> actualizar { it.copy(form = form.copy(error = r.message)) }
            }
        }
    }

    fun pedirEliminar(rutaId: String?) = actualizar { it.copy(eliminarId = rutaId) }

    fun eliminarRuta() {
        val id = ui.value.eliminarId ?: return
        viewModelScope.launch {
            eliminarRutaUseCase(id)
            actualizar { it.copy(eliminarId = null, detalleId = null, mensaje = "Ruta eliminada.") }
        }
    }

    fun consumirMensaje() = actualizar { it.copy(mensaje = null) }
}
