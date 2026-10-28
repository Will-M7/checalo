package com.example.ecolacteoshuata.presentation.admin.finanzas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Person
import com.example.ecolacteoshuata.core.theme.*
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.ui.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinanzasScreen(vm: FinanzasViewModel) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.cargar() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TituloSeccion("Resumen financiero")
            if (s.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            s.error?.let { AvisoFinanzas(it, error = true) }
            s.mensaje?.let { AvisoFinanzas(it) }
            TextButton(onClick = { vm.cargar() }, enabled = !s.ocupado) { Text("Actualizar desde el servidor") }
        }
        s.datos?.let { d ->
            item {
                HeroMetricCard("Disponible tras obligaciones", dineroFinanzas(d.resumen["disponible"] ?: 0),
                    "Posición actual · sin filtros", Icons.Filled.AccountBalanceWallet)
            }
            item {
                MetricasFinanzas(listOf(
                    "Obligaciones por saldos" to dineroFinanzas(d.resumen["saldos_clientes"] ?: 0),
                    "Fondos en custodia" to dineroFinanzas(d.resumen["custodia"] ?: 0),
                    "Fondos netos entregados" to dineroFinanzas(d.resumen["empresa"] ?: 0),
                    "Obligaciones con proveedores" to dineroFinanzas(d.resumen["proveedores_pendientes"] ?: 0),
                    "Cobertura pendiente" to dineroFinanzas(d.resumen["faltante_cobertura"] ?: 0),
                ))
            }
            item {
                SectionCard {
                    EstadoChip("Conciliación", EstiloChip.ADVERTENCIA)
                    Text("El registro no certifica saldos bancarios; la custodia sigue pendiente de entrega.", style = CuerpoStyle, modifier = Modifier.padding(top = 8.dp))
                    InfoRow("Saldos heredados sin conciliación", dineroFinanzas(d.resumen["saldo_no_conciliado"] ?: 0))
                    InfoRow("Abonos sin ingreso conciliado", dineroFinanzas(d.resumen["abonos_no_conciliados"] ?: 0))
                    Text("Fuentes sin asiento: ventas ${d.resumen["ventas_sin_registro"] ?: 0}, proveedores ${d.resumen["pagos_proveedor_sin_registro"] ?: 0}, Core ${d.resumen["pagos_core_sin_registro"] ?: 0}.", style = CuerpoMedioStyle, color = GrisTexto)
                }
            }
            item {
                TituloSeccion("Actividad y movimientos")
                SectionCard {
                FiltrosListado(s.filtros["busqueda"].orEmpty(), { vm.filtro("busqueda", it) }, listOf(
                    CampoFiltro("desde", "Desde", s.filtros["desde"].orEmpty()), CampoFiltro("hasta", "Hasta", s.filtros["hasta"].orEmpty()),
                    CampoFiltro("vendedor", "Vendedor", s.filtros["vendedor"].orEmpty(), d.usuarios.map { it.id.toString() to it.nombre }),
                    CampoFiltro("receptor", "Receptor", s.filtros["receptor"].orEmpty(), d.receptores.map { it.id.toString() to it.nombre }),
                    CampoFiltro("tipo", "Tipo", s.filtros["tipo"].orEmpty(), d.tipos.map { it to it.replace('_', ' ') }),
                    CampoFiltro("estado", "Conciliación", s.filtros["estado"].orEmpty(), listOf("confirmado" to "Confirmado", "no_conciliado" to "No conciliado")),
                ), { k,v -> vm.filtro(k,v) }, { vm.limpiar(false) }, "Origen o referencia")
                TextButton(onClick = { vm.cargar() }, enabled = !s.ocupado) { Text("Aplicar filtros de movimientos") }
                }
                Spacer(Modifier.height(12.dp))
                MetricasFinanzas(listOf("recaudado" to "Recaudación", "ventas" to "Ingresos por ventas", "recargas" to "Recargas confirmadas", "rendido" to "Rendiciones", "egresos" to "Egresos").map { (k,l) -> l to dineroFinanzas(d.actividad[k] ?: 0) })
            }
            if (d.movimientos.data.isEmpty() && !s.ocupado) item { EmptyState("Sin movimientos", "No hay registros para los filtros seleccionados.") }
            items(d.movimientos.data, key = { "mov-${it.id}" }) { m ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BurbujaIcono(Icons.Filled.ReceiptLong, LocalTemaRol.current.acento)
                        Column(Modifier.weight(1f)) {
                            Text(m.tipo.replace('_', ' '), style = TituloPantallaStyle)
                            Text("#${m.id} · ${m.created_at.take(19).replace('T', ' ')}", style = CuerpoMedioStyle, color = GrisTexto)
                        }
                    }
                    Text(dineroFinanzas(m.amount_minor), style = KpiStyle, modifier = Modifier.padding(vertical = 10.dp))
                    EstadoChip(m.estado.replace('_', ' '), estadoFinanzas(m.estado))
                    InfoRow("Origen", m.origen)
                    InfoRow("Vendedor", d.usuarios.find { it.id == m.vendedor_id }?.nombre ?: "—")
                    InfoRow("Custodio", d.usuarios.find { it.id == m.custodio_id }?.nombre ?: "—")
                    InfoRow("Receptor", d.receptores.find { it.id == m.receptor_pago_id }?.nombre ?: "—")
                    if (m.pendiente_rendir_minor > 0) {
                        DividerLine(Modifier.padding(vertical = 8.dp))
                        SecondaryButton("Registrar entrega · ${dineroFinanzas(m.pendiente_rendir_minor)}", onClick = { vm.preparar(m) }, habilitado = !s.ocupado)
                    }
                }
            }
            item { PaginasFinanzas(d.movimientos.current_page, d.movimientos.last_page, !s.ocupado) { vm.cargar(it) } }
            item {
                TituloSeccion("Clientes y saldos")
                SectionCard {
                Text("Saldo del comprador. El vendedor se consulta por cada pedido.", style = CuerpoMedioStyle, color = GrisTexto)
                FiltrosListado(s.filtrosClientes["busquedaCliente"].orEmpty(), { vm.filtro("busquedaCliente", it, true) }, listOf(
                    CampoFiltro("saldoCliente", "Saldo", s.filtrosClientes["saldoCliente"].orEmpty(), listOf("positivo" to "Con saldo", "cero" to "Sin saldo")),
                    CampoFiltro("vendedorCliente", "Atendido por", s.filtrosClientes["vendedorCliente"].orEmpty(), d.usuarios.map { it.id.toString() to it.nombre }),
                ), { k,v -> vm.filtro(k,v,true) }, { vm.limpiar(true) }, "Cliente o contacto")
                TextButton(onClick = { vm.clientes() }, enabled = !s.ocupado) { Text("Consultar clientes") }
                }
            }
        }
        items(s.clientes?.data.orEmpty(), key = { "cli-${it.id}" }) { c ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BurbujaIcono(Icons.Filled.Person, LocalTemaRol.current.acento)
                    Column(Modifier.weight(1f)) {
                        Text(c.nombre, style = TituloPantallaStyle)
                        Text(listOfNotNull(c.contacto?.takeIf { it.isNotBlank() }, c.email?.takeIf { it.isNotBlank() }).joinToString(" · "), style = CuerpoMedioStyle, color = GrisTexto)
                    }
                }
                Text("Saldo actual", style = CuerpoMedioStyle, color = GrisTexto, modifier = Modifier.padding(top = 12.dp))
                Text(dineroFinanzas(c.saldo_minor), style = KpiStyle)
                InfoRow("Total recargado", dineroFinanzas(c.recargado_minor))
                InfoRow("Total consumido", dineroFinanzas(c.consumido_minor))
                DividerLine(Modifier.padding(vertical = 8.dp))
                InfoRow("Pedidos realizados", c.pedidos_total.toString())
                InfoRow("Pendientes", c.pedidos_pendientes.toString())
                InfoRow("Pagados", c.pedidos_pagados.toString())
                TextButton(onClick = { vm.pedidos(c) }, enabled = !s.ocupado) { Text("Ver pedidos y vendedores") }
            }
        }
        if (s.clientes?.data?.isEmpty() == true && !s.ocupado) item { EmptyState("Sin clientes", "No hay clientes para los filtros seleccionados.") }
        s.clientes?.let { c -> item { PaginasFinanzas(c.current_page, c.last_page, !s.ocupado) { vm.clientes(it) } } }
        s.cliente?.let { c ->
            item { TituloSeccion("Historial de ${c.nombre}") }
            items(s.pedidos?.data.orEmpty(), key = { "pedido-${it.id}" }) { p ->
                SectionCard {
                    Text("Pedido #${p.id}", style = TituloPantallaStyle)
                    Text(p.fecha.take(10), style = CuerpoMedioStyle, color = GrisTexto)
                    Text("S/ ${p.total}", style = KpiStyle, modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow("Atendido por", p.vendedor)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EstadoChip(p.estado.replace('_', ' '), estadoFinanzas(p.estado))
                        EstadoChip(p.pago_estado.replace('_', ' '), estadoFinanzas(p.pago_estado))
                    }
                }
            }
            s.pedidos?.let { p -> item { PaginasFinanzas(p.current_page, p.last_page, !s.ocupado) { vm.pedidos(c, it) } } }
        }
    }
    s.ingreso?.let { m ->
        AppBottomSheet(onDismiss = vm::cerrar) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TituloSeccion("Entrega de fondos · ingreso #${m.id}")
                Text("Registra únicamente fondos realmente recibidos por Huata. Se requiere confirmación del servidor.", style = CuerpoStyle)
                AppTextField(s.importe, vm::importe, "Importe S/", soloLectura = s.enviada != null)
                AppTextField(s.referencia, vm::referencia, "Referencia / constancia", soloLectura = s.enviada != null)
                Row { Checkbox(s.recibido, vm::recibido, enabled = s.enviada == null && !s.ocupado); Text("Confirmo la recepción real") }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PrimaryButton(if (s.enviada == null) "Confirmar entrega" else "Reintentar misma operación", vm::rendir, habilitado = !s.ocupado, cargando = s.ocupado)
            }
        }
    }
}

@Composable
private fun PaginasFinanzas(actual: Int, ultima: Int, habilitado: Boolean, ir: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { ir(actual - 1) }, enabled = habilitado && actual > 1) { Text("Anterior") }
        Text("$actual / $ultima", modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = { ir(actual + 1) }, enabled = habilitado && actual < ultima) { Text("Siguiente") }
    }
}

@Composable
private fun MetricasFinanzas(valores: List<Pair<String, String>>) {
    BoxWithConstraints {
        // Una columna en teléfonos estrechos o con texto ampliado; dos cuando hay espacio.
        val columnas = if (maxWidth >= 480.dp && valores.all { it.second.length <= 14 } && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.2f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            valores.chunked(columnas).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fila.forEach { (etiqueta, valor) -> KpiCard(etiqueta, valor, LocalTemaRol.current.acento, Modifier.weight(1f), Icons.Filled.AccountBalanceWallet) }
                    if (fila.size < columnas) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AvisoFinanzas(texto: String, error: Boolean = false) {
    SectionCard(Modifier.padding(top = 8.dp)) {
        EstadoChip(if (error) "Requiere atención" else "Información", if (error) EstiloChip.ERROR else EstiloChip.INFO)
        Text(texto, style = CuerpoStyle, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun estadoFinanzas(estado: String): EstiloChip = when (estado) {
    "confirmado", "pagado", "entregado", "completado" -> EstiloChip.EXITO
    "rechazado", "cancelado", "fallido" -> EstiloChip.ERROR
    "pendiente", "no_conciliado", "sin_pago" -> EstiloChip.ADVERTENCIA
    else -> EstiloChip.INFO
}
