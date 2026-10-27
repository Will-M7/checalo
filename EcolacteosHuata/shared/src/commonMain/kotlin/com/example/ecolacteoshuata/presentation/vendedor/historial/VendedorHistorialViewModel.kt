package com.example.ecolacteoshuata.presentation.vendedor.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.plusDias
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Venta
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.domain.repository.VentaRepository
import com.example.ecolacteoshuata.domain.usecase.GenerarBoletaPdfUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveClientesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Una boleta del historial con los nombres ya resueltos. */
data class FilaVenta(val venta: Venta, val cliente: String, val vendedor: String?)

data class VendedorHistorialUiState(
    val desde: LocalDate,
    val hasta: LocalDate,
    val metodoPago: MetodoPago? = null,
    val busqueda: String = "",
    val ventas: List<FilaVenta> = emptyList(),
    val confirmarLimpiar: Boolean = false,
    val mensaje: String? = null,
) {
    val totalPeriodo: Double get() = redondear2(ventas.sumOf { it.venta.total })
    val boletasPeriodo: Int get() = ventas.size
}

private data class FiltroHistorial(
    val desde: LocalDate,
    val hasta: LocalDate,
    val metodoPago: MetodoPago? = null,
    val busqueda: String = "",
    val confirmarLimpiar: Boolean = false,
    val mensaje: String? = null,
)

/**
 * "Historial de ventas" de Laravel: las boletas de un rango de fechas (por defecto los últimos
 * 30 días), filtradas por método de pago y por boleta o cliente; su PDF y "Limpiar historial".
 */
class VendedorHistorialViewModel(
    private val ventaRepository: VentaRepository,
    observeClientesUseCase: ObserveClientesUseCase,
    usuarioRepository: UsuarioRepository,
    private val generarBoletaPdfUseCase: GenerarBoletaPdfUseCase,
) : ViewModel() {
    private val hoy = todayLocalDate()
    private val filtro = MutableStateFlow(FiltroHistorial(desde = hoy.plusDias(-30), hasta = hoy))

    val state: StateFlow<VendedorHistorialUiState> = combine(
        filtro,
        ventaRepository.observeRecientes(),
        observeClientesUseCase(),
        usuarioRepository.observeAll(),
    ) { f, ventas, clientes, usuarios ->
        val clientesPorId = clientes.associateBy { it.id }
        val usuariosPorId = usuarios.associateBy { it.id }
        val texto = f.busqueda.trim()
        val filas = ventas
            .filter { it.fecha.date >= f.desde && it.fecha.date <= f.hasta }
            .filter { f.metodoPago == null || it.metodoPago == f.metodoPago }
            .map { venta ->
                val cliente = venta.clienteId?.let { clientesPorId[it] }?.nombreCompleto
                    ?: venta.clienteNombreLibre?.takeIf { it.isNotBlank() }
                    ?: "Venta anónima"
                FilaVenta(venta, cliente, venta.vendedorId?.let { usuariosPorId[it]?.nombreCompleto })
            }
            .filter { texto.isEmpty() || it.venta.numeroBoleta.contains(texto, true) || it.cliente.contains(texto, true) }
        VendedorHistorialUiState(f.desde, f.hasta, f.metodoPago, f.busqueda, filas, f.confirmarLimpiar, f.mensaje)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VendedorHistorialUiState(hoy.plusDias(-30), hoy))

    fun onDesde(fecha: LocalDate) = filtro.update { it.copy(desde = fecha) }

    fun onHasta(fecha: LocalDate) = filtro.update { it.copy(hasta = fecha) }

    fun onMetodo(metodo: MetodoPago?) = filtro.update { it.copy(metodoPago = metodo) }

    fun onBusqueda(texto: String) = filtro.update { it.copy(busqueda = texto) }

    fun descargarBoleta(fila: FilaVenta) {
        viewModelScope.launch {
            val ruta = generarBoletaPdfUseCase(fila.venta)
            filtro.update { it.copy(mensaje = "Boleta guardada en $ruta") }
        }
    }

    fun pedirLimpiar() = filtro.update { it.copy(confirmarLimpiar = true) }

    fun cancelarLimpiar() = filtro.update { it.copy(confirmarLimpiar = false) }

    /** Elimina las boletas que coinciden con los filtros actuales (no repone el stock). */
    fun limpiarHistorial() {
        val ids = state.value.ventas.map { it.venta.id }
        if (ids.isEmpty()) {
            filtro.update { it.copy(confirmarLimpiar = false, mensaje = "No hay ventas para eliminar con los filtros actuales.") }
            return
        }
        viewModelScope.launch {
            ventaRepository.eliminar(ids)
            filtro.update { it.copy(confirmarLimpiar = false, mensaje = "Se eliminaron ${ids.size} boleta(s) del historial.") }
        }
    }

    fun consumirMensaje() = filtro.update { it.copy(mensaje = null) }
}
