package com.example.ecolacteoshuata.presentation.admin.roles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.*
import com.example.ecolacteoshuata.domain.usecase.AdministrarRolesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DistribucionForm(val id: Long? = null, val version: Int? = null, val nombre: String = "", val base: Boolean = false, val asignaciones: Map<String, Set<String>> = emptyMap())
data class DistribucionesState(
    val catalogo: CatalogoDistribuciones? = null, val form: DistribucionForm? = null,
    val vista: VistaDistribucion? = null, val confirmado: Boolean = false,
    val reemplazos: Map<String, String> = emptyMap(), val nombreCopia: String = "",
    val ocupado: Boolean = false, val error: String? = null, val mensaje: String? = null,
)

/** Solo estado de formulario y transporte: Laravel calcula diferencias, valida y aplica. */
class DistribucionesViewModel(private val central: AdministrarRolesUseCase) : ViewModel() {
    private val _state = MutableStateFlow(DistribucionesState())
    val state = _state.asStateFlow()

    private fun catalogo(accion: suspend () -> AppResult<CatalogoDistribuciones>, mensaje: String? = null, seleccionarUltima: Boolean = false) {
        if (_state.value.ocupado) return
        _state.value = _state.value.copy(ocupado = true, error = null)
        viewModelScope.launch {
            _state.value = when (val r = accion()) {
                is AppResult.Success -> _state.value.copy(catalogo = r.data, ocupado = false, vista = null, confirmado = false, mensaje = mensaje, form = null)
                is AppResult.Error -> _state.value.copy(ocupado = false, error = r.message)
            }
            if (seleccionarUltima && _state.value.error == null) {
                val c = _state.value.catalogo
                (c?.distribuciones?.find { it.id == c.guardada_id } ?: c?.distribuciones?.maxByOrNull { it.id })?.let(::ver)
            }
        }
    }
    fun cargar() = catalogo({ central.distribuciones() })
    fun nueva() { if (!_state.value.ocupado) _state.value = _state.value.copy(form = DistribucionForm(), vista = null, confirmado = false, error = null) }
    fun ver(d: DistribucionRoles) {
        if (!_state.value.ocupado) _state.value = _state.value.copy(form = DistribucionForm(d.id, d.version, d.nombre, d.tipo == "base", d.asignaciones.associate { it.codigo to it.pantallas.toSet() }), vista = null, confirmado = false, error = null)
    }
    fun nombre(v: String) { val f = _state.value.form ?: return; if (!f.base && !_state.value.ocupado) _state.value = _state.value.copy(form = f.copy(nombre = v), vista = null, confirmado = false) }
    fun nombreCopia(v: String) { if (!_state.value.ocupado) _state.value = _state.value.copy(nombreCopia = v) }
    fun rol(codigo: String) {
        val f = _state.value.form ?: return
        if (f.base || _state.value.ocupado) return
        _state.value = _state.value.copy(form = f.copy(asignaciones = if (codigo in f.asignaciones) f.asignaciones - codigo else f.asignaciones + (codigo to emptySet())), vista = null, confirmado = false)
    }
    fun pantalla(codigo: String, pantalla: String) {
        val f = _state.value.form ?: return
        if (f.base || _state.value.ocupado || codigo !in f.asignaciones) return
        val actuales = f.asignaciones.getValue(codigo)
        _state.value = _state.value.copy(form = f.copy(asignaciones = f.asignaciones + (codigo to if (pantalla in actuales) actuales - pantalla else actuales + pantalla)), vista = null, confirmado = false)
    }
    fun guardar() {
        val f = _state.value.form ?: return
        if (f.base) return
        catalogo({ central.guardarDistribucion(GuardarDistribucion(modo = "guardar", id = f.id, version = f.version, nombre = f.nombre.trim(), asignaciones = f.asignaciones.map { AsignacionDistribucion(it.key, pantallas = it.value.toList()) })) }, "Plantilla guardada. No se ha aplicado.", seleccionarUltima = true)
    }
    fun copiar(actual: Boolean) {
        val s = _state.value
        if (!actual && s.form?.id == null) return
        val nombre = s.nombreCopia.trim().ifBlank { if (actual) "Copia de asignaciones actuales" else "${s.form?.nombre} (copia)" }
        catalogo({ central.guardarDistribucion(GuardarDistribucion(modo = "copiar", nombre = nombre, origen = if (actual) null else s.form?.id)) }, "Copia editable guardada. No se ha aplicado.", seleccionarUltima = true)
    }
    fun reemplazo(usuarioId: String, codigo: String) { if (!_state.value.ocupado) _state.value = _state.value.copy(reemplazos = _state.value.reemplazos + (usuarioId to codigo), vista = null, confirmado = false) }
    fun preparar(operacion: String, aplicacion: String? = null) {
        val s = _state.value
        if (s.ocupado || (operacion == "aplicar" && s.form?.id == null)) return
        _state.value = s.copy(ocupado = true, error = null, vista = null, confirmado = false)
        viewModelScope.launch {
            _state.value = when (val r = central.prepararDistribucion(PrepararDistribucion(operacion, s.form?.id, aplicacion, s.reemplazos.map { ReemplazoRolPrueba(it.key, it.value) }))) {
                is AppResult.Success -> _state.value.copy(ocupado = false, vista = r.data)
                is AppResult.Error -> _state.value.copy(ocupado = false, error = r.message)
            }
        }
    }
    fun confirmarSeleccion() { if (!_state.value.ocupado) _state.value = _state.value.copy(confirmado = !_state.value.confirmado) }
    fun aplicar() {
        val s = _state.value; val token = s.vista?.token ?: return
        if (!s.confirmado) { _state.value = s.copy(error = "Confirma las diferencias de la vista previa."); return }
        catalogo({ central.confirmarDistribucion(ConfirmarDistribucion(token, true)) }, "Operación confirmada en Laravel.")
    }
}
