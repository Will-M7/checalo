package com.example.ecolacteoshuata.presentation.common.reportes

import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.domain.model.Periodo
import com.example.ecolacteoshuata.domain.model.ReporteTabla
import com.example.ecolacteoshuata.domain.model.TipoReporte

fun etiquetaTipoReporte(tipo: TipoReporte): String = when (tipo) {
    TipoReporte.LECHE_POR_ACOPIADOR -> "Leche por acopiador"
    TipoReporte.USUARIOS -> "Usuarios"
    TipoReporte.CLIENTES_FRECUENTES -> "Clientes y compra frecuente"
    TipoReporte.PRODUCTOS -> "Productos"
    TipoReporte.PRECIOS -> "Precios"
    TipoReporte.VENTAS -> "Ventas"
    TipoReporte.RECEPCION_PLANTA -> "Recepción en planta"
    TipoReporte.DESCUENTOS_APLICADOS -> "Descuentos aplicados"
    TipoReporte.PROVEEDORES_PAGADOS_POR_PAGAR -> "Proveedores pagados / por pagar"
    TipoReporte.RESULTADOS_LABORATORIO -> "Resultados de laboratorio"
    TipoReporte.LECHE_POR_PROVEEDOR -> "Leche por proveedor"
    TipoReporte.LITROS_POR_TURNO -> "Litros por turno"
    TipoReporte.ENTREGAS_PROVEEDOR -> "Mis entregas"
}

private fun etiquetaPeriodo(periodo: Periodo) = when (periodo) {
    Periodo.TURNO -> "Turno"
    Periodo.DIA -> "Día"
    Periodo.SEMANA -> "Semana"
    Periodo.MES -> "Mes"
    Periodo.ANIO -> "Año"
}

@Composable
fun ReportesScreen(
    tipos: List<TipoReporte>,
    state: ReportesUiState,
    onPeriodoChange: (Periodo) -> Unit,
    onVerReporte: (TipoReporte) -> Unit,
    onExportarPdf: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Periodo.entries.forEach { periodo ->
                    val activo = periodo == state.periodo
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(fondoOpcion(activo), RoundedCornerShape(999.dp))
                            .clickable { onPeriodoChange(periodo) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(etiquetaPeriodo(periodo), style = EtiquetaMayusculaStyle, color = textoOpcion(activo))
                    }
                }
            }
        }
        item { TituloSeccion("Reportes disponibles") }
        items(tipos) { tipo ->
            SectionCard(modifier = Modifier.clickable { onVerReporte(tipo) }) {
                Text(etiquetaTipoReporte(tipo), style = CuerpoMedioStyle, color = Negro)
            }
        }
        if (state.reporte != null) {
            item { ReportePreview(state.reporte, onExportarPdf) }
        }
    }
}

@Composable
private fun ReportePreview(reporte: ReporteTabla, onExportarPdf: () -> Unit) {
    SectionCard {
        Text(reporte.titulo, style = TituloPantallaStyle, color = Negro)
        Text(reporte.subtitulo, style = CuerpoMedioStyle, color = GrisTexto, modifier = Modifier.padding(bottom = 8.dp))
        if (reporte.filas.isEmpty()) {
            EmptyState("Sin datos", "No hay información para el periodo seleccionado.")
        } else {
            reporte.filas.take(20).forEach { fila ->
                Text(fila.joinToString("   ·   "), style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (reporte.totales.isNotEmpty()) {
                Text(reporte.totales.filter { it.isNotBlank() }.joinToString("   ·   "), style = CuerpoMedioStyle, color = VerdeOscuro, modifier = Modifier.padding(top = 8.dp))
            }
        }
        PrimaryButton(texto = "Exportar a PDF", onClick = onExportarPdf, modifier = Modifier.padding(top = 14.dp))
    }
}
