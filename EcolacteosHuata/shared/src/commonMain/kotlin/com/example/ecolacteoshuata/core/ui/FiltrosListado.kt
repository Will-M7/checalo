package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Controles de lectura. Filtrar nunca solicita sincronización ni cambia registros. */
data class CampoFiltro(val clave: String, val etiqueta: String, val valor: String, val opciones: List<Pair<String, String>>? = null)

@Composable
fun FiltrosListado(
    busqueda: String,
    onBusqueda: (String) -> Unit,
    campos: List<CampoFiltro>,
    onFiltro: (String, String) -> Unit,
    onLimpiar: () -> Unit,
    etiqueta: String = "Buscar...",
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(busqueda, onBusqueda, etiqueta, modifier = Modifier.weight(1f), placeholder = placeholder)
            Box {
                val activos = campos.count { it.valor.isNotEmpty() }
                TextButton(onClick = { abierto = !abierto }) { Text("Filtrar" + if (activos > 0) " ($activos)" else "") }
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    Column(Modifier.width(260.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        campos.forEach { campo ->
                            if (campo.opciones != null) {
                                val opciones = listOf("" to "Todos") + campo.opciones
                                AppDropdown(opciones.firstOrNull { it.first == campo.valor }, opciones, campo.etiqueta, { it.second }, { onFiltro(campo.clave, it.first) })
                            } else {
                                AppTextField(campo.valor, { onFiltro(campo.clave, it) }, campo.etiqueta, placeholder = "AAAA-MM-DD", esError = campo.valor.isNotBlank() && runCatching { kotlinx.datetime.LocalDate.parse(campo.valor) }.isFailure)
                            }
                        }
                        TextButton(onClick = onLimpiar) { Text("Limpiar filtros") }
                    }
                }
            }
        }
        val activos = campos.filter { it.valor.isNotEmpty() }
        if (activos.isNotEmpty()) Text(activos.joinToString(" · ") { campo -> campo.etiqueta + ": " + (campo.opciones?.firstOrNull { it.first == campo.valor }?.second ?: campo.valor) }, style = MaterialTheme.typography.bodySmall)
    }
}
