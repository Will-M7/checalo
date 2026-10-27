package com.example.ecolacteoshuata.presentation.planta.laboratorio

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.ocr.LabOcrDraft
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import com.example.ecolacteoshuata.domain.model.TurnoProduccion
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import com.example.ecolacteoshuata.domain.repository.TurnoProduccionRepository
import com.example.ecolacteoshuata.domain.usecase.BloquearProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.NuevoResultadoLabInput
import com.example.ecolacteoshuata.domain.usecase.RegistrarResultadoLaboratorioUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistroLabResultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Qué teclado y qué valor espera cada campo del análisis. */
enum class TipoCampo { DECIMAL, ENTERO, HORA, FECHA }

/**
 * Campos del análisis (SmartScan AI), en el mismo orden en que los imprime el ticket del
 * analizador: SN y Mode arriba; hora y fecha del análisis al final, junto a pH.
 */
enum class CampoAnalisis(val etiqueta: String, val tipo: TipoCampo = TipoCampo.DECIMAL, val ejemplo: String? = null) {
    SN("SN", TipoCampo.ENTERO, "48359"),
    MODO("Mode", TipoCampo.ENTERO, "1"),
    TEMPERATURA("Temp. (°C)"),
    GRASA("Grasa (%)"),
    SNG("SNG (%)"),
    DENSIDAD("Densidad"),
    PROTEINA("Proteína (%)"),
    LACTOSA("Lactosa (%)"),
    SALES("Sales (%)"),
    TOTAL_SOLIDOS("Total sólidos (%)"),
    AGUA_ANADIDA("Agua añadida (%)"),
    PUNTO_CONGELACION("Punto cong. (°C)"),
    PH("pH"),
    HORA("Hora", TipoCampo.HORA, "09:42"),
    FECHA("Fecha", TipoCampo.FECHA, "06/06/26"),
}

data class PlantaLaboratorioRegistroUiState(
    val proveedor: Proveedor? = null,
    val historial: List<ResultadoLaboratorio> = emptyList(),
    val turnos: List<TurnoProduccion> = emptyList(),
    val fecha: LocalDate = todayLocalDate(),
    val turnoId: String? = null,
    /** Suma de los recojos ya entregados a planta de este proveedor en [fecha]; null = sin recojo. */
    val litrosRecogidos: Double? = null,
    val litros: String = "",
    val analisis: Map<CampoAnalisis, String> = emptyMap(),
    val observaciones: String = "",
    val conforme: Boolean = true,
    /** Tipo de alteración que leyó el OCR (acidificado, aguado…), si la hubo. */
    val resultadoDetectado: ResultadoTipo? = null,
    val foto: ImageBitmap? = null,
    val guardando: Boolean = false,
    val error: String? = null,
    val ultimoRegistro: RegistroLabResultado? = null,
    /** Editando un resultado ya registrado (el lápiz de Laravel). */
    val editando: Boolean = false,
    /** Se guardó la edición: la pantalla vuelve a la lista de resultados. */
    val edicionGuardada: Boolean = false,
)

class PlantaLaboratorioRegistroViewModel(
    private val proveedorId: String,
    private val resultadoId: String?,
    private val proveedorRepository: ProveedorRepository,
    private val resultadoLaboratorioRepository: ResultadoLaboratorioRepository,
    private val registrarResultadoLaboratorioUseCase: RegistrarResultadoLaboratorioUseCase,
    private val bloquearProveedorUseCase: BloquearProveedorUseCase,
    private val recojoRepository: RecojoRepository,
    private val turnoProduccionRepository: TurnoProduccionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlantaLaboratorioRegistroUiState())
    val state: StateFlow<PlantaLaboratorioRegistroUiState> = _state.asStateFlow()

    init {
        if (resultadoId != null) precargar(resultadoId)
        cargar()
        viewModelScope.launch {
            _state.value = _state.value.copy(turnos = turnoProduccionRepository.observeAll().first())
        }
    }

    private fun cargar() {
        viewModelScope.launch {
            val proveedor = proveedorRepository.getById(proveedorId)
            val historial = resultadoLaboratorioRepository.getByProveedor(proveedorId)
            _state.value = _state.value.copy(proveedor = proveedor, historial = historial)
            cargarLitrosRecogidos()
        }
    }

    /** Llena el formulario con el resultado que se va a editar, como `editar()` de Laravel. */
    private fun precargar(id: String) {
        viewModelScope.launch {
            val r = resultadoLaboratorioRepository.getById(id) ?: return@launch
            fun t(v: Double?) = v?.toString().orEmpty()
            _state.value = _state.value.copy(
                editando = true,
                fecha = r.fecha,
                turnoId = r.turnoId,
                litros = t(r.litros),
                analisis = mapOf(
                    CampoAnalisis.SN to r.sn.orEmpty(),
                    CampoAnalisis.MODO to r.modo.orEmpty(),
                    CampoAnalisis.TEMPERATURA to t(r.temperatura),
                    CampoAnalisis.GRASA to t(r.grasa),
                    CampoAnalisis.SNG to t(r.sng),
                    CampoAnalisis.DENSIDAD to t(r.densidad),
                    CampoAnalisis.PROTEINA to t(r.proteina),
                    CampoAnalisis.LACTOSA to t(r.lactosa),
                    CampoAnalisis.SALES to t(r.sales),
                    CampoAnalisis.TOTAL_SOLIDOS to t(r.totalSolidos),
                    CampoAnalisis.AGUA_ANADIDA to t(r.aguaAnadida),
                    CampoAnalisis.PUNTO_CONGELACION to t(r.puntoCongelacion),
                    CampoAnalisis.PH to t(r.ph),
                    CampoAnalisis.HORA to r.horaAnalisis.orEmpty(),
                    CampoAnalisis.FECHA to r.fechaAnalisis.orEmpty(),
                ).filterValues { it.isNotBlank() },
                observaciones = r.observaciones,
                conforme = r.conforme,
                resultadoDetectado = r.resultado.takeIf { it != ResultadoTipo.CONFORME },
            )
            cargarLitrosRecogidos()
        }
    }

    private suspend fun cargarLitrosRecogidos() {
        val fecha = _state.value.fecha
        val recojos = recojoRepository.getByProveedor(proveedorId).filter { it.fecha == fecha && it.entregado }
        _state.value = _state.value.copy(litrosRecogidos = recojos.takeIf { it.isNotEmpty() }?.sumOf { it.litros })
    }

    fun onFechaChange(v: LocalDate) {
        _state.value = _state.value.copy(fecha = v, error = null)
        viewModelScope.launch { cargarLitrosRecogidos() }
    }

    fun onTurnoChange(v: String?) { _state.value = _state.value.copy(turnoId = v) }
    fun onLitrosChange(v: String) { _state.value = _state.value.copy(litros = v, error = null) }
    fun onAnalisisChange(campo: CampoAnalisis, v: String) { _state.value = _state.value.copy(analisis = _state.value.analisis + (campo to v)) }
    fun onObservacionesChange(v: String) { _state.value = _state.value.copy(observaciones = v) }
    fun onConformeChange(v: Boolean) { _state.value = _state.value.copy(conforme = v) }

    /** Vuelca en el formulario lo que leyó el OCR; lo que no reconoció queda como estaba. */
    fun aplicarOcr(borrador: LabOcrDraft, foto: ImageBitmap?) {
        val leidos = mapOf(
            CampoAnalisis.SN to borrador.sn,
            CampoAnalisis.MODO to borrador.modo,
            CampoAnalisis.TEMPERATURA to borrador.temperatura?.toString(),
            CampoAnalisis.GRASA to borrador.grasa?.toString(),
            CampoAnalisis.SNG to borrador.sng?.toString(),
            CampoAnalisis.DENSIDAD to borrador.densidad?.toString(),
            CampoAnalisis.PROTEINA to borrador.proteina?.toString(),
            CampoAnalisis.LACTOSA to borrador.lactosa?.toString(),
            CampoAnalisis.SALES to borrador.sales?.toString(),
            CampoAnalisis.TOTAL_SOLIDOS to borrador.totalSolidos?.toString(),
            CampoAnalisis.AGUA_ANADIDA to borrador.aguaAnadida?.toString(),
            CampoAnalisis.PUNTO_CONGELACION to borrador.puntoCongelacion?.toString(),
            CampoAnalisis.PH to borrador.ph?.toString(),
            CampoAnalisis.HORA to borrador.hora,
            CampoAnalisis.FECHA to borrador.fecha,
        ).mapNotNull { (campo, valor) -> valor?.let { campo to it } }.toMap()
        val s = _state.value
        _state.value = s.copy(
            analisis = s.analisis + leidos,
            foto = foto ?: s.foto,
            resultadoDetectado = borrador.resultado,
            conforme = borrador.resultado?.let { it == ResultadoTipo.CONFORME } ?: s.conforme,
        )
    }

    fun registrar() {
        val s = _state.value
        if (s.guardando) return
        fun num(campo: CampoAnalisis) = s.analisis[campo]?.replace(',', '.')?.toDoubleOrNull()
        fun texto(campo: CampoAnalisis) = s.analisis[campo]?.trim()?.ifBlank { null }
        val resultado = when {
            s.conforme -> ResultadoTipo.CONFORME
            s.resultadoDetectado != null && s.resultadoDetectado != ResultadoTipo.CONFORME -> s.resultadoDetectado
            else -> ResultadoTipo.NO_CONFORME
        }
        viewModelScope.launch {
            _state.value = s.copy(guardando = true, error = null)
            val respuesta = registrarResultadoLaboratorioUseCase(
                NuevoResultadoLabInput(
                    resultadoId = resultadoId,
                    proveedorId = proveedorId,
                    fecha = s.fecha,
                    turnoId = s.turnoId,
                    resultado = resultado,
                    densidad = num(CampoAnalisis.DENSIDAD),
                    acidez = null,
                    temperatura = num(CampoAnalisis.TEMPERATURA),
                    observaciones = s.observaciones,
                    litros = s.litros.replace(',', '.').toDoubleOrNull(),
                    grasa = num(CampoAnalisis.GRASA),
                    sng = num(CampoAnalisis.SNG),
                    proteina = num(CampoAnalisis.PROTEINA),
                    lactosa = num(CampoAnalisis.LACTOSA),
                    sales = num(CampoAnalisis.SALES),
                    totalSolidos = num(CampoAnalisis.TOTAL_SOLIDOS),
                    aguaAnadida = num(CampoAnalisis.AGUA_ANADIDA),
                    puntoCongelacion = num(CampoAnalisis.PUNTO_CONGELACION),
                    ph = num(CampoAnalisis.PH),
                    sn = texto(CampoAnalisis.SN),
                    modo = texto(CampoAnalisis.MODO),
                    horaAnalisis = texto(CampoAnalisis.HORA),
                    fechaAnalisis = texto(CampoAnalisis.FECHA),
                )
            )
            when (respuesta) {
                is AppResult.Success -> {
                    if (resultadoId != null) {
                        _state.value = _state.value.copy(guardando = false, ultimoRegistro = respuesta.data, edicionGuardada = true)
                        return@launch
                    }
                    _state.value = _state.value.copy(
                        guardando = false,
                        ultimoRegistro = respuesta.data,
                        litros = "",
                        analisis = emptyMap(),
                        observaciones = "",
                        conforme = true,
                        resultadoDetectado = null,
                        foto = null,
                    )
                    cargar()
                }
                is AppResult.Error -> _state.value = _state.value.copy(guardando = false, error = respuesta.message)
            }
        }
    }

    /** "Desbloquear" de Laravel: vuelve a recoger su leche y reinicia sus alteraciones. */
    fun desbloquearProveedor() {
        viewModelScope.launch {
            bloquearProveedorUseCase(proveedorId, false)
            cargar()
        }
    }

    /** La pantalla ya volvió a la lista: si se vuelve a editar este resultado, se recarga desde la base. */
    fun edicionCerrada() {
        _state.value = _state.value.copy(edicionGuardada = false, ultimoRegistro = null)
        resultadoId?.let { precargar(it) }
        cargar()
    }

    fun confirmacionConsumida() {
        _state.value = _state.value.copy(ultimoRegistro = null)
    }
}
