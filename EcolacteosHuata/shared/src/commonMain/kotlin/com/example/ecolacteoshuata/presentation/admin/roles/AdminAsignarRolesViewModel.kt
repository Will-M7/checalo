package com.example.ecolacteoshuata.presentation.admin.roles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.usecase.AsignarRolesUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveUsuariosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Formulario "Asignar roles" abierto para un usuario (el modal de Laravel). */
data class AsignarRolesForm(
    val usuario: Usuario,
    val roles: Set<String>,
    val catalogo: List<com.example.ecolacteoshuata.domain.model.RolAsignado> = emptyList(),
    /** "Tomar asistencia": acceso al módulo de asistencia y justificaciones. */
    val tomaAsistencia: Boolean = false,
    val error: String? = null,
    val guardando: Boolean = false,
)

class AdminAsignarRolesViewModel(
    observeUsuariosUseCase: ObserveUsuariosUseCase,
    private val asignarRolesUseCase: AsignarRolesUseCase,
    private val sesion: com.example.ecolacteoshuata.domain.repository.SessionRepository,
    private val db: com.example.ecolacteoshuata.db.AppDatabase,
    private val central: com.example.ecolacteoshuata.domain.usecase.AdministrarRolesUseCase,
) : ViewModel() {
    val usuarios: StateFlow<List<Usuario>> = observeUsuariosUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _form = MutableStateFlow<AsignarRolesForm?>(null)
    val form: StateFlow<AsignarRolesForm?> = _form.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun abrir(usuario: Usuario) {
        val propio = sesion.usuarioActual.value?.id?.let { com.example.ecolacteoshuata.data.repository.AutorizacionLocal.leer(db, it) }
        _form.value = AsignarRolesForm(usuario = usuario, roles = usuario.autorizacion?.roles.orEmpty().map { it.id }.toSet(), catalogo = (propio?.catalogo_roles.orEmpty() + usuario.autorizacion?.roles.orEmpty()).distinctBy { it.id }.filterNot { it.auxiliar || it.id == "tomar_asistencia" }, tomaAsistencia = usuario.autorizacion?.roles.orEmpty().any { it.id == "tomar_asistencia" })
        viewModelScope.launch {
            when (val r = central.asignables()) {
                is AppResult.Success -> if (_form.value?.usuario?.id == usuario.id) _form.value = _form.value?.copy(catalogo = (r.data + usuario.autorizacion?.roles.orEmpty()).distinctBy { it.id }.filterNot { it.auxiliar || it.id == "tomar_asistencia" })
                is AppResult.Error -> if (_form.value?.usuario?.id == usuario.id) _form.value = _form.value?.copy(error = r.message)
            }
        }

    }


    fun cerrar() { _form.value = null }

    /** Marca o desmarca un rol (volver a tocar uno marcado lo quita, como en Laravel). */
    fun alternarRol(rol: String) = editar { copy(roles = if (rol in roles) roles - rol else roles + rol) }

    fun guardar() {
        val f = _form.value ?: return
        if (f.guardando) return
        _form.value = f.copy(guardando = true, error = null)
        viewModelScope.launch {
            when (val r = asignarRolesUseCase(f.usuario.id, f.roles, f.tomaAsistencia)) {
                is AppResult.Success -> {
                    _form.value = null
                    _mensaje.value = "Roles actualizados correctamente."
                }
                is AppResult.Error -> _form.value = f.copy(guardando = false, error = r.message)
            }
        }
    }

    fun consumirMensaje() { _mensaje.value = null }

    private fun editar(cambio: AsignarRolesForm.() -> AsignarRolesForm) {
        _form.value = _form.value?.cambio()?.copy(error = null)
    }
}
