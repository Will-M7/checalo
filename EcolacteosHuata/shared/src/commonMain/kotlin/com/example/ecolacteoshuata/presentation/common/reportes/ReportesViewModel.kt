package com.example.ecolacteoshuata.presentation.common.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.endOfMonth
import com.example.ecolacteoshuata.core.util.endOfWeek
import com.example.ecolacteoshuata.core.util.endOfYear
import com.example.ecolacteoshuata.core.util.startOfMonth
import com.example.ecolacteoshuata.core.util.startOfWeek
import com.example.ecolacteoshuata.core.util.startOfYear
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Periodo
import com.example.ecolacteoshuata.domain.model.ReporteTabla
import com.example.ecolacteoshuata.domain.model.TipoReporte
import com.example.ecolacteoshuata.domain.usecase.ExportarReportePdfUseCase
import com.example.ecolacteoshuata.domain.usecase.GetReporteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportesUiState(
    val periodo: Periodo = Periodo.DIA,
    val tipoSeleccionado: TipoReporte? = null,
    val reporte: ReporteTabla? = null,
    val cargando: Boolean = false,
    val rutaPdfGenerado: String? = null,
)

class ReportesViewModel(
    private val tiposDisponibles: List<TipoReporte>,
    private val acopiadorId: String?,
    private val proveedorId: String?,
    private val getReporteUseCase: GetReporteUseCase,
    private val exportarReportePdfUseCase: ExportarReportePdfUseCase,
) : ViewModel() {
    val tipos: List<TipoReporte> get() = tiposDisponibles

    private val _state = MutableStateFlow(ReportesUiState())
    val state: StateFlow<ReportesUiState> = _state.asStateFlow()

    fun seleccionarPeriodo(periodo: Periodo) {
        _state.value = _state.value.copy(periodo = periodo)
        _state.value.tipoSeleccionado?.let { verReporte(it) }
    }

    fun verReporte(tipo: TipoReporte) {
        viewModelScope.launch {
            _state.value = _state.value.copy(tipoSeleccionado = tipo, cargando = true)
            val hoy = todayLocalDate()
            val (desde, hasta) = when (_state.value.periodo) {
                Periodo.TURNO, Periodo.DIA -> hoy to hoy
                Periodo.SEMANA -> hoy.startOfWeek() to hoy.endOfWeek()
                Periodo.MES -> hoy.startOfMonth() to hoy.endOfMonth()
                Periodo.ANIO -> hoy.startOfYear() to hoy.endOfYear()
            }
            val reporte = getReporteUseCase(tipo, desde, hasta, acopiadorId, proveedorId)
            _state.value = _state.value.copy(reporte = reporte, cargando = false)
        }
    }

    fun exportarPdf() {
        val reporte = _state.value.reporte ?: return
        viewModelScope.launch {
            val ruta = exportarReportePdfUseCase(reporte)
            _state.value = _state.value.copy(rutaPdfGenerado = ruta)
        }
    }

    fun limpiarRutaPdf() {
        _state.value = _state.value.copy(rutaPdfGenerado = null)
    }
}
