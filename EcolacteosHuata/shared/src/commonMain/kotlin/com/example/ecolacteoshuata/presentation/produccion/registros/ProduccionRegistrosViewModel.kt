package com.example.ecolacteoshuata.presentation.produccion.registros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.InventarioInsumoItem
import com.example.ecolacteoshuata.domain.model.ProduccionRegistro
import com.example.ecolacteoshuata.domain.model.Receta
import com.example.ecolacteoshuata.domain.model.TurnoProduccion
import com.example.ecolacteoshuata.domain.usecase.ActualizarProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.GetInventarioInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.PublicarProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveRecetasUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTurnosProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarProduccionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una receta a producir en "Nueva producción". */
data class LineaProduccion(
    val recetaId: String? = null,
    val cantidad: String = "",
    /** Insumos de la receta que ya están en 0: la línea no se puede producir hasta reponer. */
    val agotados: List<String> = emptyList(),
)

data class RegistroFormState(
    /** Producción que se está editando; null = "Nueva producción". */
    val editandoId: String? = null,
    val lineas: List<LineaProduccion> = listOf(LineaProduccion()),
    val turnoId: String? = null,
    /** "Cuajo — faltan 1.50 L": stock insuficiente para lo que se está por guardar. */
    val faltantes: List<String> = emptyList(),
    val guardando: Boolean = false,
)

/** "Actualizar producción": suma una cantidad a una producción existente. */
data class ActualizarFormState(
    val registro: ProduccionRegistro,
    val cantidad: String = "",
    val turnoId: String? = registro.turnoId,
    val faltantes: List<String> = emptyList(),
)

class ProduccionRegistrosViewModel(
    observeProduccionUseCase: ObserveProduccionUseCase,
    observeRecetasUseCase: ObserveRecetasUseCase,
    private val registrarProduccionUseCase: RegistrarProduccionUseCase,
    observeTurnosProduccionUseCase: ObserveTurnosProduccionUseCase,
    private val getInventarioInsumosUseCase: GetInventarioInsumosUseCase,
    private val actualizarProduccionUseCase: ActualizarProduccionUseCase,
    private val publicarProduccionUseCase: PublicarProduccionUseCase,
    private val eliminarProduccionUseCase: EliminarProduccionUseCase,
) : ViewModel() {
    val registros: StateFlow<List<ProduccionRegistro>> = observeProduccionUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recetas: StateFlow<List<Receta>> = observeRecetasUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val turnos: StateFlow<List<TurnoProduccion>> = observeTurnosProduccionUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formVisible = MutableStateFlow<RegistroFormState?>(null)
    val formVisible: StateFlow<RegistroFormState?> = _formVisible

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Stock de cada insumo al abrir el formulario, para avisar faltantes mientras se llena. */
    private var inventario: Map<String, InventarioInsumoItem> = emptyMap()

    fun abrirNuevo() {
        _formVisible.value = RegistroFormState()
        viewModelScope.launch {
            inventario = getInventarioInsumosUseCase().associateBy { it.insumo.id }
            recalcular()
        }
    }

    /** Editar: una sola línea con su receta y cantidad; lo que ya consumía vuelve al stock disponible. */
    fun abrirEditar(registro: ProduccionRegistro) {
        _formVisible.value = RegistroFormState(
            editandoId = registro.id,
            lineas = listOf(LineaProduccion(registro.recetaId, numero(registro.cantidadLotes))),
            turnoId = registro.turnoId,
        )
        viewModelScope.launch {
            inventario = inventarioSin(registro)
            recalcular()
        }
    }

    fun cerrarForm() { _formVisible.value = null; _error.value = null }

    fun eliminar(id: String) {
        viewModelScope.launch {
            val resultado = eliminarProduccionUseCase(id)
            if (resultado is AppResult.Error) _error.value = resultado.message
        }
    }

    fun publicar(id: String) { viewModelScope.launch { publicarProduccionUseCase(id) } }

    // ----- Actualizar producción -----
    private val _actualizar = MutableStateFlow<ActualizarFormState?>(null)
    val actualizar: StateFlow<ActualizarFormState?> = _actualizar

    fun abrirActualizar(registro: ProduccionRegistro) {
        _error.value = null
        _actualizar.value = ActualizarFormState(registro)
        viewModelScope.launch { inventario = getInventarioInsumosUseCase().associateBy { it.insumo.id } }
    }

    fun cerrarActualizar() { _actualizar.value = null; _error.value = null }

    fun onActualizarCantidadChange(v: String) {
        val a = _actualizar.value ?: return
        val agregada = v.replace(',', '.').toDoubleOrNull() ?: 0.0
        val receta = recetas.value.firstOrNull { it.id == a.registro.recetaId }
        val necesario = receta?.detalle?.associate { it.insumoId to it.cantidad * agregada }.orEmpty()
        _actualizar.value = a.copy(cantidad = v, faltantes = faltantesDe(necesario))
        _error.value = null
    }

    fun onActualizarTurnoChange(turnoId: String?) { _actualizar.value = _actualizar.value?.copy(turnoId = turnoId) }

    fun guardarActualizacion() {
        val a = _actualizar.value ?: return
        val agregada = a.cantidad.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (agregada <= 0) { _error.value = "Ingresa la cantidad a agregar."; return }
        if (a.faltantes.isNotEmpty()) { _error.value = "No hay stock suficiente para agregar esta cantidad."; return }
        viewModelScope.launch {
            val r = a.registro
            val resultado = actualizarProduccionUseCase(
                r.copy(cantidadLotes = r.cantidadLotes + agregada, cantidadProducida = r.cantidadProducida + agregada, turnoId = a.turnoId),
            )
            when (resultado) {
                is AppResult.Success -> cerrarActualizar()
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }

    /** Inventario como si [registro] no existiera (para editarlo sin contar dos veces su consumo). */
    private suspend fun inventarioSin(registro: ProduccionRegistro): Map<String, InventarioInsumoItem> {
        val receta = recetas.value.firstOrNull { it.id == registro.recetaId }
        val devuelto = receta?.detalle?.associate { it.insumoId to it.cantidad * registro.cantidadProducida }.orEmpty()
        return getInventarioInsumosUseCase().associate { item ->
            item.insumo.id to item.copy(stock = item.stock + (devuelto[item.insumo.id] ?: 0.0))
        }
    }

    private fun faltantesDe(necesarioPorInsumo: Map<String, Double>): List<String> =
        necesarioPorInsumo.mapNotNull { (insumoId, necesario) ->
            val item = inventario[insumoId] ?: return@mapNotNull null
            val falta = necesario - item.stock
            if (falta > 0) "${item.insumo.nombre} — faltan ${formato(falta)} ${item.insumo.unidadMedidaSimbolo}".trim() else null
        }

    private fun numero(v: Double) = v.toString().removeSuffix(".0")

    fun agregarLinea() = actualizarLineas { it + LineaProduccion() }

    fun quitarLinea(index: Int) = actualizarLineas { lineas ->
        if (lineas.size <= 1) lineas else lineas.filterIndexed { i, _ -> i != index }
    }

    fun onRecetaChange(index: Int, recetaId: String) = actualizarLineas { lineas ->
        lineas.mapIndexed { i, l -> if (i == index) l.copy(recetaId = recetaId) else l }
    }

    fun onCantidadChange(index: Int, cantidad: String) = actualizarLineas { lineas ->
        lineas.mapIndexed { i, l -> if (i == index) l.copy(cantidad = cantidad) else l }
    }

    fun onTurnoChange(turnoId: String?) { _formVisible.value = _formVisible.value?.copy(turnoId = turnoId) }

    private fun actualizarLineas(cambio: (List<LineaProduccion>) -> List<LineaProduccion>) {
        val f = _formVisible.value ?: return
        _formVisible.value = f.copy(lineas = cambio(f.lineas))
        _error.value = null
        recalcular()
    }

    private fun cantidad(linea: LineaProduccion) = linea.cantidad.replace(',', '.').toDoubleOrNull() ?: 0.0

    /**
     * Igual que Laravel: suma lo que consumiría cada insumo entre todas las líneas (dos líneas
     * pueden compartir un insumo) y lo compara con su stock.
     */
    private fun recalcular() {
        val f = _formVisible.value ?: return
        val recetasPorId = recetas.value.associateBy { it.id }
        val pendiente = mutableMapOf<String, Double>()
        f.lineas.forEach { linea ->
            val receta = recetasPorId[linea.recetaId] ?: return@forEach
            val cant = cantidad(linea).takeIf { it > 0 } ?: return@forEach
            receta.detalle.forEach { pendiente[it.insumoId] = (pendiente[it.insumoId] ?: 0.0) + it.cantidad * cant }
        }
        val faltantes = faltantesDe(pendiente)
        val lineas = f.lineas.map { linea ->
            val receta = recetasPorId[linea.recetaId]
            linea.copy(
                agotados = receta?.detalle
                    ?.mapNotNull { d -> inventario[d.insumoId]?.takeIf { it.stock <= 0 }?.insumo?.nombre }
                    ?.distinct()
                    .orEmpty(),
            )
        }
        _formVisible.value = f.copy(lineas = lineas, faltantes = faltantes)
    }

    private fun formato(v: Double): String {
        val centesimos = kotlin.math.round(v * 100).toLong()
        return "${centesimos / 100}.${(centesimos % 100).toString().padStart(2, '0')}"
    }

    fun guardar() {
        val f = _formVisible.value ?: return
        if (f.guardando) return
        if (f.lineas.any { it.recetaId == null }) { _error.value = "Selecciona la receta de cada línea."; return }
        if (f.lineas.any { cantidad(it) <= 0 }) { _error.value = "La cantidad de cada receta debe ser mayor a 0."; return }
        if (f.faltantes.isNotEmpty()) { _error.value = "No hay stock suficiente de insumos para producir esa cantidad."; return }

        val recetasPorId = recetas.value.associateBy { it.id }
        val original = f.editandoId?.let { id -> registros.value.firstOrNull { it.id == id } }
        if (original != null) {
            val linea = f.lineas.first()
            val cant = cantidad(linea)
            viewModelScope.launch {
                _formVisible.value = f.copy(guardando = true)
                val resultado = actualizarProduccionUseCase(
                    original.copy(
                        recetaId = linea.recetaId.orEmpty(),
                        productoId = recetasPorId[linea.recetaId]?.productoId,
                        cantidadLotes = cant,
                        cantidadProducida = cant,
                        turnoId = f.turnoId,
                    ),
                )
                when (resultado) {
                    is AppResult.Success -> cerrarForm()
                    is AppResult.Error -> {
                        _error.value = resultado.message
                        _formVisible.value = _formVisible.value?.copy(guardando = false)
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            _formVisible.value = f.copy(guardando = true)
            for (linea in f.lineas) {
                val receta = recetasPorId[linea.recetaId]
                val cant = cantidad(linea)
                val registro = ProduccionRegistro(
                    id = "",
                    fecha = todayLocalDate(),
                    recetaId = linea.recetaId.orEmpty(),
                    recetaNombre = receta?.nombre.orEmpty(),
                    productoId = receta?.productoId,
                    cantidadLotes = cant,
                    cantidadProducida = cant,
                    observaciones = "",
                    turnoId = f.turnoId,
                )
                val resultado = registrarProduccionUseCase(registro)
                if (resultado is AppResult.Error) {
                    _error.value = resultado.message
                    _formVisible.value = _formVisible.value?.copy(guardando = false)
                    return@launch
                }
            }
            cerrarForm()
        }
    }
}
