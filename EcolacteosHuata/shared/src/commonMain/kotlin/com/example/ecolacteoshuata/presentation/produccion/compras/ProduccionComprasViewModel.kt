package com.example.ecolacteoshuata.presentation.produccion.compras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.endOfMonth
import com.example.ecolacteoshuata.core.util.endOfWeek
import com.example.ecolacteoshuata.core.util.plusDias
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.startOfMonth
import com.example.ecolacteoshuata.core.util.startOfWeek
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Compra
import com.example.ecolacteoshuata.domain.model.CompraDetalle
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.usecase.ObserveComprasUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarCompraUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Atajos de fecha, iguales a los botones de Laravel. */
enum class PeriodoCompras(val etiqueta: String) {
    ULTIMOS_30("Últimos 30 días"),
    HOY("Hoy"),
    AYER("Ayer"),
    ESTA_SEMANA("Esta semana"),
    SEMANA_PASADA("Semana pasada"),
    ESTE_MES("Este mes"),
    MES_PASADO("Mes pasado");

    fun rango(hoy: LocalDate = todayLocalDate()): Pair<LocalDate, LocalDate> = when (this) {
        ULTIMOS_30 -> hoy.plusDias(-30) to hoy
        HOY -> hoy to hoy
        AYER -> hoy.plusDias(-1) to hoy.plusDias(-1)
        ESTA_SEMANA -> hoy.startOfWeek() to hoy
        SEMANA_PASADA -> hoy.plusDias(-7).startOfWeek() to hoy.plusDias(-7).endOfWeek()
        ESTE_MES -> hoy.startOfMonth() to hoy
        MES_PASADO -> hoy.startOfMonth().plusDias(-1).startOfMonth() to hoy.startOfMonth().plusDias(-1).endOfMonth()
    }
}

/** Una fila del historial: un insumo que entró en una compra. */
data class IngresoInsumo(
    val detalle: CompraDetalle,
    val fecha: LocalDate,
    val simbolo: String,
    /** Precio de la compra anterior del mismo insumo; null = es la primera compra registrada. */
    val precioAnterior: Double?,
) {
    val diferencia: Double? get() = precioAnterior?.let { detalle.costoUnitario - it }
    val porcentaje: Double? get() = precioAnterior?.takeIf { it > 0 }?.let { (detalle.costoUnitario - it) / it * 100 }
}

data class HistorialComprasState(
    val periodo: PeriodoCompras = PeriodoCompras.ULTIMOS_30,
    val desde: LocalDate = PeriodoCompras.ULTIMOS_30.rango().first,
    val hasta: LocalDate = PeriodoCompras.ULTIMOS_30.rango().second,
    val insumoId: String? = null,
    val ingresos: List<IngresoInsumo> = emptyList(),
) {
    val totalPeriodo: Double get() = ingresos.sumOf { it.detalle.subtotal }
}

class ProduccionComprasViewModel(
    observeComprasUseCase: ObserveComprasUseCase,
    observeInsumosUseCase: ObserveInsumosUseCase,
    private val registrarCompraUseCase: RegistrarCompraUseCase,
) : ViewModel() {
    val compras: StateFlow<List<Compra>> = observeComprasUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val insumos: StateFlow<List<Insumo>> = observeInsumosUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val filtros = MutableStateFlow(HistorialComprasState())

    /**
     * Historial de ingresos de stock filtrado por fechas e insumo. La variación de precio se
     * compara siempre contra la compra anterior del mismo insumo, sin importar el filtro
     * (igual que Laravel).
     */
    val historial: StateFlow<HistorialComprasState> = combine(compras, insumos, filtros) { compras, insumos, f ->
        val simbolos = insumos.associate { it.id to it.unidadMedidaSimbolo }
        // De la más antigua a la más reciente, para saber cuál fue la compra anterior de cada insumo.
        val cronologico = compras.reversed().flatMap { compra -> compra.detalle.map { compra.fecha to it } }
        val ultimoPrecio = mutableMapOf<String, Double>()
        val todos = cronologico.map { (fecha, detalle) ->
            IngresoInsumo(detalle, fecha, simbolos[detalle.insumoId].orEmpty(), ultimoPrecio[detalle.insumoId])
                .also { ultimoPrecio[detalle.insumoId] = detalle.costoUnitario }
        }
        f.copy(
            ingresos = todos
                .filter { it.fecha >= f.desde && it.fecha <= f.hasta }
                .filter { f.insumoId == null || it.detalle.insumoId == f.insumoId }
                .reversed(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistorialComprasState())

    fun seleccionarPeriodo(periodo: PeriodoCompras) {
        val (desde, hasta) = periodo.rango()
        filtros.value = filtros.value.copy(periodo = periodo, desde = desde, hasta = hasta)
    }

    fun seleccionarInsumo(insumoId: String?) { filtros.value = filtros.value.copy(insumoId = insumoId) }

    private val _formVisible = MutableStateFlow(false)
    val formVisible: StateFlow<Boolean> = _formVisible

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun abrirNuevo() { _formVisible.value = true; _error.value = null }
    fun cerrarForm() { _formVisible.value = false; _error.value = null }

    /**
     * "Registrar compra de insumo": suma la compra al stock y recalcula el costo unitario como
     * promedio ponderado, igual que desde Insumos (`Insumo::registrarCompra()` en Laravel).
     */
    fun registrar(insumoId: String?, cantidad: Double?, costoUnitario: Double?) {
        val insumo = insumos.value.find { it.id == insumoId }
        if (insumo == null) { _error.value = "Elige el insumo que compraste."; return }
        if (cantidad == null || cantidad <= 0) { _error.value = "Ingresa una cantidad mayor a 0."; return }
        if (costoUnitario == null || costoUnitario < 0) { _error.value = "Ingresa el costo unitario pagado."; return }
        viewModelScope.launch {
            val subtotal = redondear2(cantidad * costoUnitario)
            val compra = Compra(
                id = "",
                fecha = todayLocalDate(),
                proveedorInsumo = "",
                descripcion = "Compra de ${insumo.nombre}",
                total = subtotal,
                detalle = listOf(
                    CompraDetalle(
                        id = IdGenerator.newId(), compraId = "", insumoId = insumo.id, insumoNombre = insumo.nombre,
                        cantidad = cantidad, costoUnitario = costoUnitario, subtotal = subtotal,
                    ),
                ),
            )
            when (val resultado = registrarCompraUseCase(compra)) {
                is AppResult.Success -> cerrarForm()
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }
}
