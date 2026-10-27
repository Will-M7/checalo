package com.example.ecolacteoshuata.presentation.admin.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.usecase.DatosProveedorInput
import com.example.ecolacteoshuata.domain.usecase.GetProveedorPorUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.GetUsuarioByIdUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarUsuarioInput
import com.example.ecolacteoshuata.domain.usecase.GuardarUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveAcopiadoresUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminUsuarioFormState(
    val editandoId: String? = null,
    val nombres: String = "",
    val apellidos: String = "",
    val documento: String = "",
    val celular: String = "",
    /** Como en Laravel, el rol empieza sin elegir ("Selecciona un rol..."). */
    val rol: Rol? = null,
    val roles: Set<String> = emptySet(),
    val catalogo: List<com.example.ecolacteoshuata.domain.model.RolAsignado> = emptyList(),
    val usuario: String = "",
    val password: String = "",
    val activo: Boolean = true,
    val acopiadorId: String? = null,
    val fincaUbicacion: String = "",
    val fincaReferencia: String = "",
    val ubicacion: PuntoGeo? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val guardadoOk: Boolean = false,
)

class AdminUsuarioFormViewModel(
    private val usuarioId: String?,
    private val getUsuarioByIdUseCase: GetUsuarioByIdUseCase,
    private val getProveedorPorUsuarioUseCase: GetProveedorPorUsuarioUseCase,
    observeAcopiadoresUseCase: ObserveAcopiadoresUseCase,
    private val guardarUsuarioUseCase: GuardarUsuarioUseCase,
    private val central: com.example.ecolacteoshuata.domain.usecase.AdministrarRolesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminUsuarioFormState(editandoId = usuarioId))
    val state: StateFlow<AdminUsuarioFormState> = _state.asStateFlow()

    val acopiadores: StateFlow<List<Acopiador>> = observeAcopiadoresUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            when (val r = central.asignables()) {
                is AppResult.Success -> _state.value = _state.value.copy(catalogo = (r.data + _state.value.catalogo).distinctBy { it.id })
                is AppResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
        if (usuarioId != null) {
            viewModelScope.launch {
                val usuario = getUsuarioByIdUseCase(usuarioId) ?: return@launch
                val proveedor = if (usuario.tieneRol(Rol.PROVEEDOR)) getProveedorPorUsuarioUseCase(usuarioId) else null
                _state.value = _state.value.copy(
                    nombres = usuario.nombres,
                    apellidos = usuario.apellidos,
                    documento = usuario.documento,
                    celular = usuario.celular,
                    rol = usuario.rol,
                    roles = usuario.autorizacion?.roles.orEmpty().map { it.id }.toSet(),
                    catalogo = (_state.value.catalogo + usuario.autorizacion?.roles.orEmpty()).distinctBy { it.id },
                    usuario = usuario.usuario,
                    activo = usuario.activo,
                    acopiadorId = proveedor?.acopiadorId,
                    fincaUbicacion = proveedor?.fincaUbicacion.orEmpty(),
                    fincaReferencia = proveedor?.fincaReferencia.orEmpty(),
                    ubicacion = proveedor?.let { p ->
                        val lat = p.latitud
                        val lng = p.longitud
                        if (lat != null && lng != null) PuntoGeo(lat, lng) else null
                    },
                )
            }
        }
    }

    private fun editar(cambio: AdminUsuarioFormState.() -> AdminUsuarioFormState) {
        _state.value = _state.value.cambio().copy(error = null)
    }

    fun onNombresChange(v: String) = editar { copy(nombres = v) }
    fun onApellidosChange(v: String) = editar { copy(apellidos = v) }
    fun onDocumentoChange(v: String) = editar { copy(documento = v) }
    fun onCelularChange(v: String) = editar { copy(celular = v) }
    fun onRolChange(v: String) = editar { copy(roles = if (v in roles) roles - v else roles + v) }
    fun onUsuarioChange(v: String) = editar { copy(usuario = v) }
    fun onPasswordChange(v: String) = editar { copy(password = v) }
    fun onActivoChange(v: Boolean) = editar { copy(activo = v) }
    fun onAcopiadorChange(v: Acopiador) = editar { copy(acopiadorId = v.id) }
    fun onFincaUbicacionChange(v: String) = editar { copy(fincaUbicacion = v) }
    fun onFincaReferenciaChange(v: String) = editar { copy(fincaReferencia = v) }
    fun onUbicacionChange(v: PuntoGeo) = editar { copy(ubicacion = v) }

    fun guardar() {
        val s = _state.value
        if (s.cargando) return
        viewModelScope.launch {
            _state.value = s.copy(cargando = true, error = null)
            val resultado = guardarUsuarioUseCase(
                GuardarUsuarioInput(
                    id = s.editandoId,
                    nombres = s.nombres,
                    apellidos = s.apellidos,
                    documento = s.documento,
                    celular = s.celular,
                    rol = null,
                    rolesDinamicos = s.roles,
                    usuario = s.usuario,
                    password = s.password,
                    activo = s.activo,
                    proveedor = DatosProveedorInput(
                        acopiadorId = s.acopiadorId,
                        fincaUbicacion = s.fincaUbicacion,
                        fincaReferencia = s.fincaReferencia,
                        latitud = s.ubicacion?.lat,
                        longitud = s.ubicacion?.lng,
                    ),
                )
            )
            _state.value = when (resultado) {
                is AppResult.Success -> _state.value.copy(cargando = false, guardadoOk = true)
                is AppResult.Error -> _state.value.copy(cargando = false, error = resultado.message)
            }
        }
    }
}
