package com.example.ecolacteoshuata.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import com.example.ecolacteoshuata.domain.usecase.GuardarUbicacionPropiaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveAcopiadoresUseCase
import com.example.ecolacteoshuata.domain.usecase.UbicacionPropiaInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UbicacionFincaState(
    val cargado: Boolean = false,
    val fincaUbicacion: String = "",
    val fincaReferencia: String = "",
    val ubicacion: PuntoGeo? = null,
    val acopiadorId: String? = null,
    val guardando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null,
)

/** "Ubicación de mi finca" del perfil del proveedor (sección de `settings/profile` en Laravel). */
class UbicacionFincaViewModel(
    private val usuarioId: String,
    private val proveedorRepository: ProveedorRepository,
    rutaRepository: RutaRepository,
    observeAcopiadoresUseCase: ObserveAcopiadoresUseCase,
    private val guardarUbicacionPropiaUseCase: GuardarUbicacionPropiaUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(UbicacionFincaState())
    val state: StateFlow<UbicacionFincaState> = _state.asStateFlow()

    /** Como en Laravel, ordenados por nombre. */
    val acopiadores: StateFlow<List<Acopiador>> = observeAcopiadoresUseCase()
        .map { lista -> lista.sortedBy { it.nombreCompleto.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Caminos de las rutas activas del acopiador elegido (`Acopiador::rutasParaMapa`), en azul. */
    val rutasDelAcopiador: StateFlow<List<List<PuntoGeo>>> =
        combine(_state.map { it.acopiadorId }.distinctUntilChanged(), rutaRepository.observeAll()) { acopiadorId, rutas ->
            rutas.filter { it.acopiadorId == acopiadorId && it.activa && it.trazo.size > 1 }
                .sortedBy { it.nombre }
                .map { it.trazo }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val p = proveedorRepository.getByUsuarioId(usuarioId)
            val lat = p?.latitud
            val lng = p?.longitud
            _state.value = UbicacionFincaState(
                cargado = true,
                fincaUbicacion = p?.fincaUbicacion.orEmpty(),
                fincaReferencia = p?.fincaReferencia.orEmpty(),
                ubicacion = if (lat != null && lng != null) PuntoGeo(lat, lng) else null,
                acopiadorId = p?.acopiadorId,
            )
        }
    }

    private fun editar(cambio: UbicacionFincaState.() -> UbicacionFincaState) {
        _state.value = _state.value.cambio().copy(error = null)
    }

    fun onFincaUbicacion(v: String) = editar { copy(fincaUbicacion = v) }
    fun onFincaReferencia(v: String) = editar { copy(fincaReferencia = v) }
    fun onAcopiador(a: Acopiador) = editar { copy(acopiadorId = a.id) }
    fun onUbicacion(p: PuntoGeo) = editar { copy(ubicacion = p) }

    fun guardar() {
        val s = _state.value
        if (s.guardando) return
        _state.value = s.copy(guardando = true, error = null)
        viewModelScope.launch {
            val r = guardarUbicacionPropiaUseCase(
                usuarioId,
                UbicacionPropiaInput(s.fincaUbicacion, s.fincaReferencia, s.ubicacion, s.acopiadorId),
            )
            _state.value = when (r) {
                is AppResult.Success -> _state.value.copy(guardando = false, mensaje = r.data)
                is AppResult.Error -> _state.value.copy(guardando = false, error = r.message)
            }
        }
    }

    fun consumirMensaje() { _state.value = _state.value.copy(mensaje = null) }
}
