package com.example.ecolacteoshuata.presentation.admin.roles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.usecase.AdministrarRolesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RolCentralForm(val id: Long? = null, val codigo: String = "", val nombre: String = "", val activo: Boolean = true, val pantallas: Set<String> = emptySet())
data class AdminRolesState(val catalogo: CatalogoAdministrativo? = null, val form: RolCentralForm? = null, val cargando: Boolean = false, val guardando: Boolean = false, val error: String? = null, val mensaje: String? = null)

class AdminRolesViewModel(private val central: AdministrarRolesUseCase) : ViewModel() {
    private val _state = MutableStateFlow(AdminRolesState())
    val state = _state.asStateFlow()
    init { cargar() }
    fun cargar() {
        if (_state.value.cargando || _state.value.guardando) return
        _state.value = _state.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            _state.value = when (val r = central.cargar()) {
                is AppResult.Success -> _state.value.copy(catalogo = r.data, cargando = false)
                is AppResult.Error -> _state.value.copy(cargando = false, error = r.message)
            }
        }
    }
    fun nuevo() { if (!_state.value.guardando) _state.value = _state.value.copy(form = RolCentralForm(), error = null) }
    fun editar(r: RolAdministrable) { if (!_state.value.guardando) _state.value = _state.value.copy(form = RolCentralForm(r.registro_id, r.codigo, r.nombre, r.activo, r.pantallas.toSet()), error = null) }
    private fun cambiar(accion: RolCentralForm.() -> RolCentralForm) { if (!_state.value.guardando) _state.value = _state.value.copy(form = _state.value.form?.accion(), error = null) }
    fun codigo(v: String) = cambiar { if (id == null) copy(codigo = v) else this }
    fun nombre(v: String) = cambiar { copy(nombre = v) }
    fun activo(v: Boolean) = cambiar { copy(activo = v) }
    fun pantalla(id: String) {
        if (_state.value.catalogo?.pantallas?.any { it.id == id && it.delegable } == true) cambiar { copy(pantallas = if (id in pantallas) pantallas - id else pantallas + id) }
    }
    fun cerrar() { if (!_state.value.guardando) _state.value = _state.value.copy(form = null, error = null) }
    fun consumirMensaje() { _state.value = _state.value.copy(mensaje = null) }
    fun guardar() {
        val f = _state.value.form ?: return
        if (_state.value.guardando) return
        _state.value = _state.value.copy(guardando = true, error = null)
        viewModelScope.launch {
            _state.value = when (val r = central.guardar(f.id, GuardarRolCentral(f.nombre.trim(), f.codigo.trim(), f.activo, f.pantallas.toList()))) {
                is AppResult.Success -> _state.value.copy(catalogo = r.data, form = null, guardando = false, mensaje = "Rol guardado en Laravel.")
                is AppResult.Error -> _state.value.copy(guardando = false, error = r.message)
            }
        }
    }
}
