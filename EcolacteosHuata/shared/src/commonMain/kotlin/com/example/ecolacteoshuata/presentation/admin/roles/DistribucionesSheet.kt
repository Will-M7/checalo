package com.example.ecolacteoshuata.presentation.admin.roles

import androidx.compose.runtime.*
import com.example.ecolacteoshuata.core.ui.FiltrosListado
import com.example.ecolacteoshuata.core.ui.CampoFiltro
import com.example.ecolacteoshuata.presentation.common.filtros.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.*
import com.example.ecolacteoshuata.core.ui.*
import kotlinx.serialization.json.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DistribucionesSheet(onDismiss: () -> Unit, vm: DistribucionesViewModel = koinViewModel()) {
    var busquedaFiltro by remember { mutableStateOf("") }
    var filtrosLista by remember { mutableStateOf(mapOf<String, String>()) }

    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.cargar() }
    AppBottomSheet(onDismiss = { if (!s.ocupado) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EncabezadoFormulario("Distribuciones", "Configuración central · Requiere conexión")
            Text("Guardar o seleccionar no cambia accesos. Aplicar y recuperar requiere revisar y confirmar las diferencias.", style = CuerpoStyle, color = GrisTexto)
            if (s.ocupado) Text("Consultando Laravel…", style = CuerpoStyle, color = GrisTexto)
            s.error?.let { MensajeError(it) }
            s.mensaje?.let { Text(it, style = CuerpoMedioStyle, color = VerdeOscuro) }
            BotonAccion("Actualizar", Icons.Filled.Shield, VerdeOscuro, vm::cargar)
            s.catalogo?.let { c ->
                AppTextField(s.nombreCopia, vm::nombreCopia, "Nombre para guardar una copia")
                BotonAgregar("Guardar asignaciones actuales como copia", onClick = { vm.copiar(true) })
                BotonAgregar("Nueva distribución", vm::nueva)
                FiltrosListado(busquedaFiltro, { busquedaFiltro = it }, listOf(CampoFiltro("tipo", "Tipo", filtrosLista["tipo"].orEmpty(), listOf("base" to "Base protegida", "personalizada" to "Personalizada")), CampoFiltro("rol", "Contiene rol", filtrosLista["rol"].orEmpty(), c.roles.map { it.codigo to it.nombre })), { k, v -> filtrosLista = filtrosLista + (k to v) }, { busquedaFiltro = ""; filtrosLista = emptyMap() }, "Buscar distribución...")
                val visibles = c.distribuciones.filter { it.nombre.contains(busquedaFiltro, true) && (filtrosLista["tipo"].isNullOrEmpty() || it.tipo == filtrosLista["tipo"]) && (filtrosLista["rol"].isNullOrEmpty() || it.asignaciones.any { a -> a.codigo == filtrosLista["rol"] }) }
                if (visibles.isEmpty()) EmptyState("Sin coincidencias", "Prueba otra búsqueda o limpia los filtros.")
                visibles.forEach { d ->
                    TarjetaLista(titulo = d.nombre, subtitulo = "v${d.version} · ${if (d.tipo == "base") "Base protegida" else "Personalizada"}", icono = Icons.Filled.Shield,
                        acciones = { BotonAccion("Consultar / editar", Icons.Filled.Edit, VerdeOscuro, { vm.ver(d) }) }) {
                        if (d.predeterminada) Text("Inicial · Predeterminada para instalaciones nuevas", style = CuerpoMedioStyle, color = VerdeOscuro)
                        Text(if (d.coincide) "Sus roles coinciden con las asignaciones actuales" else "Contiene diferencias con las asignaciones actuales", style = CuerpoStyle, color = GrisTexto)
                    }
                }
                s.form?.let { f ->
                    EncabezadoFormulario(if (f.base) "Plantilla protegida" else "Distribución personalizada", "No crea roles ni asigna usuarios")
                    if (f.base) Text(f.nombre, style = CuerpoMedioStyle, color = Negro)
                    else AppTextField(f.nombre, vm::nombre, "Nombre")
                    if (f.id != null) {
                        Text("La vista previa utiliza la versión guardada. Guarda primero los cambios del formulario.", style = CuerpoStyle, color = GrisTexto)
                        BotonAccion("Duplicar como copia editable", Icons.Filled.Edit, VerdeOscuro, { vm.copiar(false) })
                        BotonAccion("Comparar distribución guardada", Icons.Filled.Shield, VerdeOscuro, { vm.preparar("aplicar") })
                        val origen = c.distribuciones.find { it.id == f.id }?.origen
                        origen?.get("revision")?.jsonPrimitive?.content?.let { Text("Revisión histórica: $it", style = CuerpoStyle, color = GrisTexto) }
                        origen?.get("adaptaciones")?.jsonArray?.forEach { Text(it.jsonPrimitive.content, style = CuerpoStyle, color = GrisTexto) }
                    }
                    // Se editan las mismas 41 funciones; las dos exclusivas web se identifican sin imitarlas.
                    f.asignaciones.keys.filter { codigo -> c.roles.none { it.codigo == codigo } }.forEach { codigo ->
                        MensajeError("Rol ausente: $codigo. Retíralo de la copia antes de guardarla.")
                        if (!f.base) BotonAccion("Retirar referencia $codigo", Icons.Filled.Edit, VerdeOscuro, { vm.rol(codigo) })
                    }
                    c.roles.forEach { r ->
                        CasillaTexto("${r.nombre} · ${f.asignaciones[r.codigo]?.size ?: 0} pantallas", r.codigo in f.asignaciones) { vm.rol(r.codigo) }
                        if (r.codigo in f.asignaciones) {
                            // El panel se expande por rol para conservar una lista manejable en teléfonos.
                            var expandido by remember(f.id, r.codigo) { mutableStateOf(false) }
                            BotonAccion(if (expandido) "Ocultar funciones" else "Consultar funciones", Icons.Filled.Shield, VerdeOscuro, { expandido = !expandido })
                            if (expandido) c.pantallas.forEach { p ->
                                CasillaTexto("${p.area}: ${p.nombre}" + if (p.android == null) " · Solo web" else "", p.id in f.asignaciones[r.codigo].orEmpty()) { vm.pantalla(r.codigo, p.id) }
                            }
                        }
                    }
                    if (!f.base) PrimaryButton("Guardar plantilla sin aplicar", onClick = vm::guardar)
                }
                if (c.rol_prueba_presente) {
                    EncabezadoFormulario("Retirar ventas (001)", "Se conserva cada cuenta y sus registros")
                    c.usuarios_prueba.forEach { u ->
                        val opciones = c.roles.filter { it.activo && it.codigo !in listOf("001", "tomar_asistencia") }
                        AppDropdown(seleccionado = opciones.find { it.codigo == s.reemplazos[u.id] }, opciones = opciones, etiqueta = "${u.nombre} · ID ${u.id}", textoOpcion = { it.nombre }, onSeleccionar = { vm.reemplazo(u.id, it.codigo) }, placeholder = "Seleccionar explícitamente un rol")
                    }
                    BotonAccion("Revisar reasignación y eliminación", Icons.Filled.Shield, VerdeOscuro, { vm.preparar("retirar-001") })
                }
                if (c.auxiliar_pendiente) MensajeError("El auxiliar de asistencia tiene dependencias pendientes. La pantalla asistencia.tomar se conserva.")
                s.vista?.let { v ->
                    EncabezadoFormulario("Vista previa", v.nombre)
                    v.advertencias.forEach { Text(it, style = CuerpoStyle, color = GrisTexto) }
                    v.errores.forEach { MensajeError(it) }
                    v.roles.forEach { r ->
                        TarjetaLista(titulo = r.nombre, subtitulo = r.codigo + if (!r.activo) " · Inactivo (se conserva)" else "", icono = Icons.Filled.Shield) {
                            Text("Actuales: ${r.actuales.joinToString().ifEmpty { "Ninguna" }}\nDestino: ${r.destino.joinToString().ifEmpty { "Ninguna" }}\nAñade: ${r.agrega.joinToString().ifEmpty { "Ninguna" }}\nRetira: ${r.retira.joinToString().ifEmpty { "Ninguna" }}\nUsuarios afectados: ${r.usuarios.joinToString { "${it.nombre} (ID ${it.id})" }.ifEmpty { "Ninguno" }}", style = CuerpoStyle, color = GrisTexto)
                        }
                    }
                    Text("Roles no afectados: ${v.no_afectados.joinToString().ifEmpty { "Ninguno" }}", style = CuerpoStyle, color = GrisTexto)
                    if (v.token != null) {
                        CasillaTexto("Confirmo exclusivamente las diferencias de esta vista previa", s.confirmado, vm::confirmarSeleccion)
                        PrimaryButton("Confirmar operación", onClick = vm::aplicar)
                    }
                }
                EncabezadoFormulario("Aplicaciones y recuperación", "Se bloquea si hay cambios posteriores de autorización o usuarios")
                if (c.historial.isEmpty()) Text("Todavía no se ha aplicado ninguna distribución.", style = CuerpoStyle, color = GrisTexto)
                c.historial.forEach { h ->
                    Text("${h.fecha} · ${h.nombre} · ${h.tipo} · Actor ${h.actor_id}", style = CuerpoStyle, color = GrisTexto)
                    if (h.tipo == "aplicacion") BotonAccion(if (h.recuperable) "Preparar recuperación" else "Consultar conflicto de recuperación", Icons.Filled.Shield, VerdeOscuro, { vm.preparar("recuperar", h.id) })
                }
            }
            SecondaryButton("Cerrar", onClick = { if (!s.ocupado) onDismiss() })
        }
    }
}
