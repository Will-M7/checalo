package com.example.ecolacteoshuata.presentation.vendedor.venta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.CarritoItem
import com.example.ecolacteoshuata.domain.model.Cliente
import com.example.ecolacteoshuata.domain.model.Descuento
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Producto
import com.example.ecolacteoshuata.domain.model.TotalesCarrito
import com.example.ecolacteoshuata.domain.model.Venta
import com.example.ecolacteoshuata.domain.repository.DescuentoRepository
import com.example.ecolacteoshuata.domain.usecase.CalcularTotalesCarritoUseCase
import com.example.ecolacteoshuata.domain.usecase.GenerarBoletaPdfUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveClientesUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProductosUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarVentaInput
import com.example.ecolacteoshuata.domain.usecase.RegistrarVentaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VendedorVentaUiState(
    val carrito: List<CarritoItem> = emptyList(),
    val clienteSeleccionado: Cliente? = null,
    val clienteNombreLibre: String = "",
    val clienteDocLibre: String = "",
    /** Descuentos del catálogo marcados para esta venta (como en Laravel). */
    val descuentos: List<Descuento> = emptyList(),
    val metodoPago: MetodoPago = MetodoPago.EFECTIVO,
    val efectivoRecibido: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val ventaCompletada: Venta? = null,
    val rutaPdf: String? = null,
) {
    val totales: TotalesCarrito
        get() = CalcularTotalesCarritoUseCase()(carrito, descuentos)
}

/** Venta al público, igual que `vendedor/ventas` de Laravel: se vende lo producido y publicado. */
class VendedorVentaViewModel(
    private val vendedorId: String?,
    observeProductosUseCase: ObserveProductosUseCase,
    observeClientesUseCase: ObserveClientesUseCase,
    descuentoRepository: DescuentoRepository,
    private val registrarVentaUseCase: RegistrarVentaUseCase,
    private val generarBoletaPdfUseCase: GenerarBoletaPdfUseCase,
    private val borradores: com.example.ecolacteoshuata.domain.repository.BorradorVentaYapeRepository,
) : ViewModel() {
    val productos: StateFlow<List<Producto>> = observeProductosUseCase(soloActivos = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clientes: StateFlow<List<Cliente>> = observeClientesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val descuentosDisponibles: StateFlow<List<Descuento>> = descuentoRepository.observeDisponibles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(VendedorVentaUiState())
    val state: StateFlow<VendedorVentaUiState> = _state.asStateFlow()

    init {
        if (!vendedorId.isNullOrBlank()) viewModelScope.launch {
            runCatching { borradores.cargar(vendedorId) }.onSuccess { draft ->
                if(draft!=null && _state.value.carrito.isEmpty()) {
                    _state.value=VendedorVentaUiState(carrito=draft.items,clienteSeleccionado=draft.cliente,
                        clienteNombreLibre=draft.nombreLibre.orEmpty(),clienteDocLibre=draft.documentoLibre.orEmpty(),
                        descuentos=draft.descuentos,metodoPago=MetodoPago.YAPE,
                        error="Borrador Yape recuperado. Pendiente de confirmación en el punto de venta web; no es una venta pagada.")
                }
            }.onFailure { _state.value=_state.value.copy(error="No se pudo recuperar el borrador local; requiere revisión. No hay un cobro confirmado.") }
        }
    }

    /** Lo que queda de la producción ahora (puede haber bajado si se vendió en otra venta). */
    private fun disponible(producto: Producto) = productos.value.firstOrNull { it.id == producto.id }?.disponible ?: producto.disponible

    fun agregarProducto(producto: Producto) {
        val carrito = _state.value.carrito.toMutableList()
        val idx = carrito.indexOfFirst { it.producto.id == producto.id }
        val enCarrito = if (idx >= 0) carrito[idx].cantidad else 0
        if (enCarrito + 1 > disponible(producto)) {
            _state.value = _state.value.copy(error = "No hay suficiente cantidad disponible de ${producto.nombre}.")
            return
        }
        if (idx >= 0) {
            carrito[idx] = carrito[idx].copy(cantidad = enCarrito + 1)
        } else {
            carrito.add(CarritoItem(producto, 1))
        }
        _state.value = _state.value.copy(carrito = carrito, error = null)
    }

    fun quitarUnidad(producto: Producto) {
        val carrito = _state.value.carrito.toMutableList()
        val idx = carrito.indexOfFirst { it.producto.id == producto.id }
        if (idx >= 0) {
            val nuevaCantidad = carrito[idx].cantidad - 1
            if (nuevaCantidad <= 0) carrito.removeAt(idx) else carrito[idx] = carrito[idx].copy(cantidad = nuevaCantidad)
        }
        _state.value = _state.value.copy(carrito = carrito, error = null)
    }

    fun alternarDescuento(descuento: Descuento) {
        val actuales = _state.value.descuentos
        _state.value = _state.value.copy(
            descuentos = if (actuales.any { it.id == descuento.id }) actuales.filter { it.id != descuento.id } else actuales + descuento,
        )
    }

    fun seleccionarCliente(cliente: Cliente?) {
        _state.value = _state.value.copy(clienteSeleccionado = cliente)
    }

    fun onNombreLibreChange(v: String) { _state.value = _state.value.copy(clienteNombreLibre = v) }
    fun onDocLibreChange(v: String) { _state.value = _state.value.copy(clienteDocLibre = v) }
    fun onMetodoPagoChange(v: MetodoPago) { _state.value = _state.value.copy(metodoPago = v, error = null) }
    fun onEfectivoChange(v: String) { _state.value = _state.value.copy(efectivoRecibido = v, error = null) }

    fun cobrar() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(cargando = true, error = null)
            val resultado = registrarVentaUseCase(
                RegistrarVentaInput(
                    items = s.carrito,
                    totales = s.totales,
                    cliente = s.clienteSeleccionado,
                    clienteNombreLibre = s.clienteNombreLibre,
                    clienteDocLibre = s.clienteDocLibre,
                    metodoPago = s.metodoPago,
                    efectivoRecibido = s.efectivoRecibido.replace(',', '.').toDoubleOrNull(),
                    vendedorId = vendedorId,
                    descuentosBorrador = s.descuentos,
                )
            )
            when (resultado) {
                is AppResult.Success -> {
                    val ruta = generarBoletaPdfUseCase(resultado.data)
                    _state.value = VendedorVentaUiState(ventaCompletada = resultado.data, rutaPdf = ruta)
                }
                is AppResult.Error -> _state.value = s.copy(cargando = false, error = resultado.message)
            }
        }
    }

    fun confirmacionConsumida() {
        _state.value = _state.value.copy(ventaCompletada = null, rutaPdf = null)
    }
}
