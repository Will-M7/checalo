package com.example.ecolacteoshuata.presentation.admin.finanzas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.data.remote.datasource.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

fun dineroFinanzas(minor: Long): String = (if (minor < 0) "-" else "") + "S/ " + (kotlin.math.abs(minor) / 100) + "." + (kotlin.math.abs(minor) % 100).toString().padStart(2, '0')
fun centimosFinanzas(valor: String): Long? {
    if (!Regex("[0-9]{1,7}(\\.[0-9]{1,2})?").matches(valor)) return null
    val p = valor.split('.')
    return p[0].toLong() * 100 + p.getOrElse(1) { "" }.padEnd(2, '0').toLong()
}

data class FinanzasState(
    val datos: RespuestaFinanzas? = null, val clientes: PaginaFinanzas<ClienteFinanzas>? = null,
    val cliente: ClienteFinanzas? = null, val pedidos: PaginaFinanzas<PedidoClienteFinanzas>? = null,
    val filtros: Map<String, String> = emptyMap(), val filtrosClientes: Map<String, String> = emptyMap(),
    val ocupado: Boolean = false, val error: String? = null, val mensaje: String? = null,
    val ingreso: MovimientoFinanzas? = null, val importe: String = "", val referencia: String = "", val recibido: Boolean = false,
    val clave: String = "", val enviada: RendicionFinanzas? = null,
)
class FinanzasViewModel(private val api: FinanzasRemota) : ViewModel() {
    private val _state = MutableStateFlow(FinanzasState())
    val state = _state.asStateFlow()
    private fun ejecutar(accion: suspend () -> Unit) {
        if (_state.value.ocupado) return
        _state.value = _state.value.copy(ocupado = true, error = null, mensaje = null)
        viewModelScope.launch {
            try { accion() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(error = e.message ?: "No se recibió confirmación del servidor. Reintenta la misma operación.") }
            finally { _state.value = _state.value.copy(ocupado = false) }
        }
    }
    fun cargar(pagina: Int = 1) = ejecutar {
        val datos = api.resumen(_state.value.filtros, pagina)
        val clientes = api.clientes(_state.value.filtrosClientes, _state.value.clientes?.current_page ?: 1)
        _state.value = _state.value.copy(datos = datos, clientes = clientes)
    }
    fun filtro(k: String, v: String, clientes: Boolean = false) {
        if (_state.value.ocupado) return
        _state.value = if (clientes) _state.value.copy(filtrosClientes = _state.value.filtrosClientes + (k to v)) else _state.value.copy(filtros = _state.value.filtros + (k to v))
    }
    fun limpiar(clientes: Boolean) { if (!_state.value.ocupado) _state.value = if (clientes) _state.value.copy(filtrosClientes = emptyMap()) else _state.value.copy(filtros = emptyMap()) }
    fun clientes(pagina: Int = 1) = ejecutar { _state.value = _state.value.copy(clientes = api.clientes(_state.value.filtrosClientes, pagina)) }
    fun pedidos(c: ClienteFinanzas, pagina: Int = 1) = ejecutar { _state.value = _state.value.copy(cliente = c, pedidos = api.pedidos(c.id, pagina)) }
    @OptIn(ExperimentalUuidApi::class)
    fun preparar(m: MovimientoFinanzas) { if (!_state.value.ocupado) _state.value = _state.value.copy(ingreso = m, importe = "${m.pendiente_rendir_minor / 100}.${(m.pendiente_rendir_minor % 100).toString().padStart(2, '0')}", referencia = "", recibido = false, enviada = null, clave = Uuid.random().toString(), error = null) }
    fun cerrar() { if (!_state.value.ocupado) _state.value = _state.value.copy(ingreso = null) }
    fun importe(v: String) { if (_state.value.enviada == null && !_state.value.ocupado) _state.value = _state.value.copy(importe = v) }
    fun referencia(v: String) { if (_state.value.enviada == null && !_state.value.ocupado) _state.value = _state.value.copy(referencia = v) }
    fun recibido(v: Boolean) { if (_state.value.enviada == null && !_state.value.ocupado) _state.value = _state.value.copy(recibido = v) }
    fun rendir() {
        val s = _state.value; val m = s.ingreso ?: return
        val minor = centimosFinanzas(s.importe)
        if (minor == null || minor <= 0 || !s.recibido || s.referencia.trim().length < 10) {
            _state.value = s.copy(error = "Indica importe, constancia y confirma la recepción real."); return
        }
        val d = s.enviada ?: RendicionFinanzas(m.id, minor, s.clave, s.referencia.trim(), true)
        _state.value = _state.value.copy(enviada = d)
        ejecutar {
            val respuesta = api.rendir(d)
            check(respuesta.confirmado) { "El servidor no confirmó la rendición. Reintenta la misma referencia." }
            _state.value = _state.value.copy(ingreso = null, enviada = null, mensaje = "Entrega #${respuesta.id} confirmada por Laravel.")
            // Un fallo posterior de consulta no transforma la confirmación recibida en un fallo de pago.
            val datos = api.resumen(_state.value.filtros, 1)
            _state.value = _state.value.copy(datos = datos)
        }
    }
}
