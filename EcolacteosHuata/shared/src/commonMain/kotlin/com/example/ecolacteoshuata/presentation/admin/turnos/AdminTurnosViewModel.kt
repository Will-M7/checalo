package com.example.ecolacteoshuata.presentation.admin.turnos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.model.TurnoProduccion
import com.example.ecolacteoshuata.domain.usecase.AgregarTurnoProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.AgregarTurnoUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarTurnoProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarTurnoUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTurnosProduccionUseCase
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Qué formulario de "Agregar turno" está abierto (uno por sección, como en Laravel). */
enum class FormTurno { RUTA, PRODUCCION }

class AdminTurnosViewModel(
    observeTurnosProduccionUseCase: ObserveTurnosProduccionUseCase,
    turnoRepository: TurnoRepository,
    private val agregarTurnoUseCase: AgregarTurnoUseCase,
    private val eliminarTurnoUseCase: EliminarTurnoUseCase,
    private val agregarTurnoProduccionUseCase: AgregarTurnoProduccionUseCase,
    private val eliminarTurnoProduccionUseCase: EliminarTurnoProduccionUseCase,
) : ViewModel() {
    // Como en Laravel, los turnos de ruta son una lista general: no pertenecen a un acopiador.
    val turnosRuta: StateFlow<List<Turno>> = turnoRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val turnosProduccion: StateFlow<List<TurnoProduccion>> = observeTurnosProduccionUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formVisible = MutableStateFlow<FormTurno?>(null)
    val formVisible: StateFlow<FormTurno?> = _formVisible

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun abrirForm(form: FormTurno) { _error.value = null; _formVisible.value = form }
    fun cerrarForm() { _formVisible.value = null; _error.value = null }

    fun agregarTurnoRuta(nombre: String, horaInicio: String, horaFin: String) {
        viewModelScope.launch {
            // acopiador_id vacío = turno general, visible para todos los acopiadores.
            cuandoTermina(agregarTurnoUseCase(Turno("", "", nombre.trim(), horaInicio.trim(), horaFin.trim())))
        }
    }

    fun agregarTurnoProduccion(nombre: String, horaInicio: String, horaFin: String) {
        viewModelScope.launch {
            cuandoTermina(agregarTurnoProduccionUseCase(TurnoProduccion("", nombre.trim(), horaInicio.trim(), horaFin.trim())))
        }
    }

    fun eliminarTurnoRuta(id: String) {
        viewModelScope.launch { eliminarTurnoUseCase(id) }
    }

    fun eliminarTurnoProduccion(id: String) {
        viewModelScope.launch { eliminarTurnoProduccionUseCase(id) }
    }

    private fun cuandoTermina(resultado: AppResult<Unit>) {
        when (resultado) {
            is AppResult.Success -> cerrarForm()
            is AppResult.Error -> _error.value = resultado.message
        }
    }
}
