package com.example.ecolacteoshuata.presentation.admin.clientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.Cliente
import com.example.ecolacteoshuata.domain.repository.VentaRepository
import com.example.ecolacteoshuata.domain.usecase.EliminarClienteUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarClienteUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveClientesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.round

/** Un cliente con el % de descuento que realmente recibió en sus compras (como en Laravel). */
data class FilaCliente(val cliente: Cliente, val descuentoPct: Double)

data class ClientesUiState(
    val filas: List<FilaCliente> = emptyList(),
    /** El que más compró (si ya compró algo): el aviso "Cliente más frecuente". */
    val masFrecuente: Cliente? = null,
    val busqueda: String = "",
)

/** "Clientes" del vendedor en Laravel: búsqueda, cliente más frecuente y % de descuento. */
class AdminClientesViewModel(
    observeClientesUseCase: ObserveClientesUseCase,
    private val guardarClienteUseCase: GuardarClienteUseCase,
    private val eliminarClienteUseCase: EliminarClienteUseCase,
    ventaRepository: VentaRepository,
) : ViewModel() {
    private val _busqueda = MutableStateFlow("")

    val state: StateFlow<ClientesUiState> = combine(observeClientesUseCase(), ventaRepository.observeRecientes(), _busqueda) { clientes, ventas, busqueda ->
        val porCliente = ventas.filter { it.clienteId != null }.groupBy { it.clienteId }
        val texto = busqueda.trim()
        val filas = clientes
            .filter { texto.isEmpty() || it.nombres.contains(texto, true) || it.apellidos.contains(texto, true) || it.documento.contains(texto, true) }
            .sortedByDescending { it.comprasCount }
            .map { cliente ->
                val propias = porCliente[cliente.id].orEmpty()
                val subtotal = propias.sumOf { it.subtotal }
                val descuento = propias.sumOf { it.descuento }
                FilaCliente(cliente, if (subtotal > 0) round(descuento / subtotal * 10_000) / 100 else 0.0)
            }
        ClientesUiState(filas, clientes.maxByOrNull { it.comprasCount }?.takeIf { it.comprasCount > 0 }, busqueda)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClientesUiState())

    private val _formVisible = MutableStateFlow<Cliente?>(null)
    val formVisible: StateFlow<Cliente?> = _formVisible

    private val _porEliminar = MutableStateFlow<Cliente?>(null)
    val porEliminar: StateFlow<Cliente?> = _porEliminar

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje

    fun onBusqueda(texto: String) { _busqueda.value = texto }

    fun abrirNuevo() { _formVisible.value = Cliente("", "", "", "", 0, false, 0.0, false, 0.0) }
    fun abrirEditar(cliente: Cliente) { _formVisible.value = cliente }
    fun cerrarForm() { _formVisible.value = null; _error.value = null }

    fun guardar(cliente: Cliente) {
        viewModelScope.launch {
            when (val resultado = guardarClienteUseCase(cliente)) {
                is AppResult.Success -> {
                    cerrarForm()
                    _mensaje.value = "Cliente guardado correctamente."
                }
                is AppResult.Error -> _error.value = resultado.message
            }
        }
    }

    fun pedirEliminar(cliente: Cliente) { _porEliminar.value = cliente }
    fun cancelarEliminar() { _porEliminar.value = null }

    fun eliminar() {
        val cliente = _porEliminar.value ?: return
        viewModelScope.launch {
            eliminarClienteUseCase(cliente.id)
            _porEliminar.value = null
            _mensaje.value = "Cliente eliminado."
        }
    }

    fun consumirMensaje() { _mensaje.value = null }
}
