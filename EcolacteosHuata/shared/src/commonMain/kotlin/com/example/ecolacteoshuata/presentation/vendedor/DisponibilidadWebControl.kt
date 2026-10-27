package com.example.ecolacteoshuata.presentation.vendedor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.ui.*
import com.example.ecolacteoshuata.data.remote.datasource.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class DisponibilidadState(val vendedores: List<DisponibilidadWeb> = emptyList(), val propio: DisponibilidadWeb? = null, val ocupado: Boolean = false, val error: String? = null)
class DisponibilidadWebViewModel(private val api: FinanzasRemota) : ViewModel() {
    private val _state = MutableStateFlow(DisponibilidadState())
    val state = _state.asStateFlow()
    private fun ejecutar(a: suspend () -> Unit) {
        if (_state.value.ocupado) return
        _state.value = _state.value.copy(ocupado = true, error = null)
        viewModelScope.launch {
            try { a() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(error = e.message ?: "No se recibió confirmación del servidor.") }
            finally { _state.value = _state.value.copy(ocupado = false) }
        }
    }
    fun cargar(admin: Boolean) = ejecutar { if (admin) _state.value = _state.value.copy(vendedores = api.vendedores()) else _state.value = _state.value.copy(propio = api.disponibilidad()) }
    fun pausar(v: Boolean) = ejecutar { _state.value = _state.value.copy(propio = api.pausar(v)) }
    fun autorizar(id: Long, v: Boolean) = ejecutar { api.autorizar(id, v); _state.value = _state.value.copy(vendedores = api.vendedores()) }
}

@Composable
fun DisponibilidadWebControl(admin: Boolean = false) {
    val vm = koinViewModel<DisponibilidadWebViewModel>(key = "disponibilidad-$admin")
    val s by vm.state.collectAsState()
    var abierto by remember { mutableStateOf(false) }
    if (admin) {
        TextButton(onClick = { abierto = true; vm.cargar(true) }) { Text("Disponibilidad de vendedores web") }
        if (abierto) AppBottomSheet(onDismiss = { abierto = false }) {
            var busqueda by remember { mutableStateOf("") }
            var estado by remember { mutableStateOf("") }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Vendedores web", style = MaterialTheme.typography.titleLarge)
                    Text("La autorización y la pausa son independientes del receptor financiero y de Bridge.")
                    FiltrosListado(busqueda, { busqueda = it }, listOf(CampoFiltro("estado", "Estado", estado, listOf("disponible" to "Disponible", "pausado" to "Pausado", "inhabilitado" to "Inhabilitado"))), { _,v -> estado = v }, { busqueda = ""; estado = "" }, "Vendedor")
                    if (s.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
                    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { vm.cargar(true) }, enabled = !s.ocupado) { Text("Actualizar") }
                }
                items(s.vendedores.filter { (it.capacidad || !it.autorizado) && it.nombre.contains(busqueda, ignoreCase = true) && (estado.isBlank() || it.estado == estado) }, key = { it.id }) { v ->
                    Card { Column(Modifier.padding(12.dp)) {
                        Text(v.nombre)
                        Text("${v.estado} · ${if (v.activo) "Usuario activo" else "Usuario inactivo"} · ${if (v.pausado) "Pausa del vendedor" else "Sin pausa"}")
                        TextButton(onClick = { vm.autorizar(v.id, !v.autorizado) }, enabled = !s.ocupado) { Text(if (v.autorizado) "Desautorizar recepción" else "Autorizar recepción") }
                    } }
                }
            }
        }
    } else {
        LaunchedEffect(Unit) { vm.cargar(false) }
        Card { Column(Modifier.padding(12.dp)) {
            Text("Recepción de pedidos web: ${s.propio?.estado ?: "consultando servidor"}")
            Text("Pausar conserva tus pedidos e historial.")
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            s.propio?.let { p ->
                if (p.capacidad) TextButton(onClick = { vm.pausar(!p.pausado) }, enabled = !s.ocupado && p.autorizado) { Text(if (p.pausado) "Activar recepción web" else "Pausar recepción web") }
            }
            TextButton(onClick = { vm.cargar(false) }, enabled = !s.ocupado) { Text("Consultar estado actual") }
        } }
    }
}
