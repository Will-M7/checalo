package com.example.ecolacteoshuata.presentation.acopiador.inventario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.Recojo
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.presentation.acopiador.etiqueta
import com.example.ecolacteoshuata.core.util.litrosConDecimales

@Composable
fun AcopiadorInventarioScreen(pendientes: List<Recojo>, turnos: Map<String, Turno>) {
    if (pendientes.isEmpty()) {
        EmptyState(
            "Sin leche pendiente",
            "Todos los recojos ya fueron entregados a planta.",
            modifier = Modifier.padding(24.dp),
            icono = Icons.Filled.Inventory,
        )
        return
    }
    val porFecha = pendientes.groupBy { it.fecha }
    val totalLitros = pendientes.sumOf { it.litros }
    val totalSoles = pendientes.sumOf { it.total }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            HeroMetricCard(
                etiqueta = "Pendiente de entregar",
                valor = litrosConDecimales(totalLitros),
                detalle = "${pendientes.size} recojos · ${pendientes.map { it.proveedorId }.distinct().size} proveedores · ${formatMoney(totalSoles)}",
                icono = Icons.Filled.Inventory,
            )
        }

        porFecha.forEach { (fecha, recojos) ->
            item(key = "fecha-$fecha") {
                TituloSeccion(fecha.formatDdMmmYyyy()) {
                    Text(litrosConDecimales(recojos.sumOf { it.litros }), style = CuerpoMedioStyle, color = VerdeOscuro)
                }
            }
            itemsIndexed(recojos, key = { _, r -> r.id }) { index, recojo ->
                StaggeredListItem(index = index) {
                    SectionCard {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(recojo.proveedorNombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    turnos[recojo.turnoId]?.let { EstadoChip("Turno ${it.nombre}", EstiloChip.EXITO) }
                                    EstadoChip(
                                        recojo.estadoLeche.etiqueta(),
                                        if (recojo.estadoLeche == EstadoLeche.NORMAL) EstiloChip.INFO else EstiloChip.ERROR,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(litrosConDecimales(recojo.litros), style = TituloPantallaStyle, color = VerdeOscuro)
                                Text(formatMoney(recojo.total), style = CuerpoStyle, color = GrisTexto)
                            }
                        }
                    }
                }
            }
        }
    }
}
