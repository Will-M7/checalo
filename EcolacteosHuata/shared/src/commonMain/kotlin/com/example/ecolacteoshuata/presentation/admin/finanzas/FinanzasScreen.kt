package com.example.ecolacteoshuata.presentation.admin.finanzas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.ui.*

@Composable
fun FinanzasScreen(vm: FinanzasViewModel) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.cargar() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Finanzas", style = MaterialTheme.typography.titleLarge)
            Text("Posición actual sin filtros. El registro no certifica saldos bancarios; la custodia sigue pendiente de entrega.")
            if (s.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            s.mensaje?.let { Text(it) }
            TextButton(onClick = { vm.cargar() }, enabled = !s.ocupado) { Text("Actualizar desde el servidor") }
        }
        s.datos?.let { d ->
            item {
                Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("saldos_clientes" to "Obligaciones por saldos", "custodia" to "Fondos en custodia", "empresa" to "Fondos netos entregados", "disponible" to "Disponible tras obligaciones", "proveedores_pendientes" to "Obligaciones con proveedores", "faltante_cobertura" to "Cobertura pendiente").forEach { (k,l) -> Text("$l: ${dineroFinanzas(d.resumen[k] ?: 0)}") }
                    Text("Saldos heredados sin conciliación: ${dineroFinanzas(d.resumen["saldo_no_conciliado"] ?: 0)}. Abonos sin ingreso conciliado: ${dineroFinanzas(d.resumen["abonos_no_conciliados"] ?: 0)}.")
                    Text("Fuentes sin asiento: ventas ${d.resumen["ventas_sin_registro"] ?: 0}, proveedores ${d.resumen["pagos_proveedor_sin_registro"] ?: 0}, Core ${d.resumen["pagos_core_sin_registro"] ?: 0}.")
                } }
            }
            item {
                FiltrosListado(s.filtros["busqueda"].orEmpty(), { vm.filtro("busqueda", it) }, listOf(
                    CampoFiltro("desde", "Desde", s.filtros["desde"].orEmpty()), CampoFiltro("hasta", "Hasta", s.filtros["hasta"].orEmpty()),
                    CampoFiltro("vendedor", "Vendedor", s.filtros["vendedor"].orEmpty(), d.usuarios.map { it.id.toString() to it.nombre }),
                    CampoFiltro("receptor", "Receptor", s.filtros["receptor"].orEmpty(), d.receptores.map { it.id.toString() to it.nombre }),
                    CampoFiltro("tipo", "Tipo", s.filtros["tipo"].orEmpty(), d.tipos.map { it to it.replace('_', ' ') }),
                    CampoFiltro("estado", "Conciliación", s.filtros["estado"].orEmpty(), listOf("confirmado" to "Confirmado", "no_conciliado" to "No conciliado")),
                ), { k,v -> vm.filtro(k,v) }, { vm.limpiar(false) }, "Origen o referencia")
                TextButton(onClick = { vm.cargar() }, enabled = !s.ocupado) { Text("Aplicar filtros de movimientos") }
                listOf("recaudado" to "Recaudación", "ventas" to "Ingresos por ventas", "recargas" to "Recargas confirmadas", "rendido" to "Rendiciones", "egresos" to "Egresos").forEach { (k,l) -> Text("$l: ${dineroFinanzas(d.actividad[k] ?: 0)}") }
                Text("Movimientos", style = MaterialTheme.typography.titleMedium)
            }
            items(d.movimientos.data, key = { "mov-${it.id}" }) { m ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text("#${m.id} · ${m.tipo.replace('_', ' ')} · ${dineroFinanzas(m.amount_minor)}")
                    Text("${m.created_at.take(19)} · ${m.origen} · ${m.estado}")
                    Text("Vendedor: ${d.usuarios.find { it.id == m.vendedor_id }?.nombre ?: "—"}")
                    Text("Custodio: ${d.usuarios.find { it.id == m.custodio_id }?.nombre ?: "—"} · Receptor: ${d.receptores.find { it.id == m.receptor_pago_id }?.nombre ?: "—"}")
                    if (m.pendiente_rendir_minor > 0) TextButton(onClick = { vm.preparar(m) }, enabled = !s.ocupado) { Text("Registrar entrega · ${dineroFinanzas(m.pendiente_rendir_minor)}") }
                } }
            }
            item { PaginasFinanzas(d.movimientos.current_page, d.movimientos.last_page, !s.ocupado) { vm.cargar(it) } }
            item {
                Text("Clientes y saldos", style = MaterialTheme.typography.titleLarge)
                Text("Saldo del comprador. El vendedor se consulta por cada pedido.")
                FiltrosListado(s.filtrosClientes["busquedaCliente"].orEmpty(), { vm.filtro("busquedaCliente", it, true) }, listOf(
                    CampoFiltro("saldoCliente", "Saldo", s.filtrosClientes["saldoCliente"].orEmpty(), listOf("positivo" to "Con saldo", "cero" to "Sin saldo")),
                    CampoFiltro("vendedorCliente", "Atendido por", s.filtrosClientes["vendedorCliente"].orEmpty(), d.usuarios.map { it.id.toString() to it.nombre }),
                ), { k,v -> vm.filtro(k,v,true) }, { vm.limpiar(true) }, "Cliente o contacto")
                TextButton(onClick = { vm.clientes() }, enabled = !s.ocupado) { Text("Consultar clientes") }
            }
        }
        items(s.clientes?.data.orEmpty(), key = { "cli-${it.id}" }) { c ->
            Card { Column(Modifier.padding(12.dp)) {
                Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${c.contacto.orEmpty()} · ${c.email.orEmpty()}")
                Text("Saldo: ${dineroFinanzas(c.saldo_minor)} · Recargado: ${dineroFinanzas(c.recargado_minor)} · Consumido: ${dineroFinanzas(c.consumido_minor)}")
                Text("Pedidos: ${c.pedidos_total} · Pendientes: ${c.pedidos_pendientes} · Pagados: ${c.pedidos_pagados}")
                TextButton(onClick = { vm.pedidos(c) }, enabled = !s.ocupado) { Text("Ver pedidos y vendedores") }
            } }
        }
        s.clientes?.let { c -> item { PaginasFinanzas(c.current_page, c.last_page, !s.ocupado) { vm.clientes(it) } } }
        s.cliente?.let { c ->
            item { Text("Historial de ${c.nombre}", style = MaterialTheme.typography.titleMedium) }
            items(s.pedidos?.data.orEmpty(), key = { "pedido-${it.id}" }) { p -> Text("Pedido #${p.id} · ${p.fecha.take(10)} · ${p.vendedor} · S/ ${p.total} · ${p.estado} · ${p.pago_estado}") }
            s.pedidos?.let { p -> item { PaginasFinanzas(p.current_page, p.last_page, !s.ocupado) { vm.pedidos(c, it) } } }
        }
    }
    s.ingreso?.let { m ->
        AppBottomSheet(onDismiss = vm::cerrar) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Entrega de fondos · ingreso #${m.id}", style = MaterialTheme.typography.titleLarge)
                Text("Registra únicamente fondos realmente recibidos por Huata. Se requiere confirmación del servidor.")
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
