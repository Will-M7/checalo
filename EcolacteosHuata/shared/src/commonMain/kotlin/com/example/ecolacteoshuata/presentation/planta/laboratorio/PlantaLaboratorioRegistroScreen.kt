package com.example.ecolacteoshuata.presentation.planta.laboratorio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.ocr.LabCamaraPreview
import com.example.ecolacteoshuata.core.ocr.LabOcrCapture
import com.example.ecolacteoshuata.core.ocr.LabOcrDraft
import com.example.ecolacteoshuata.core.ocr.ResultadoLaboratorioOcrParser
import com.example.ecolacteoshuata.core.ocr.rememberLabOcrCapture
import com.example.ecolacteoshuata.core.theme.Ambar
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.theme.VerdeProfundo
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SecondaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.core.util.litrosConDecimales
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.ImageBitmap

private val Epoca = LocalDate(1970, 1, 1)
private const val MILIS_DIA = 86_400_000L

private fun etiquetaResultado(tipo: ResultadoTipo) = when (tipo) {
    ResultadoTipo.CONFORME -> "Conforme"
    ResultadoTipo.SOLEADO -> "Soleado"
    ResultadoTipo.ACIDIFICADO -> "Acidificado"
    ResultadoTipo.AGUADO -> "Aguado"
    ResultadoTipo.OTRO -> "Otro"
    ResultadoTipo.NO_CONFORME -> "No conforme"
}

/** Opción del selector de turno: [id] null = "Sin turno", como en Laravel. */
private data class OpcionTurno(val id: String?, val nombre: String)

@Composable
fun PlantaLaboratorioRegistroScreen(
    state: PlantaLaboratorioRegistroUiState,
    onFechaChange: (LocalDate) -> Unit,
    onTurnoChange: (String?) -> Unit,
    onLitrosChange: (String) -> Unit,
    onAnalisisChange: (CampoAnalisis, String) -> Unit,
    onObservacionesChange: (String) -> Unit,
    onConformeChange: (Boolean) -> Unit,
    onOcrLeido: (LabOcrDraft, ImageBitmap?) -> Unit,
    onRegistrar: () -> Unit,
    onDesbloquear: () -> Unit,
    onCerrarMensaje: () -> Unit,
) {
    val proveedor = state.proveedor ?: return

    var estadoOcr by remember { mutableStateOf<String?>(null) }
    // true = el visor muestra la cámara en vivo; false = muestra la última foto tomada.
    var enVivo by remember { mutableStateOf(true) }
    var procesando by remember { mutableStateOf(false) }
    val ocr = rememberLabOcrCapture(
        onTextoReconocido = { lineas, foto ->
            procesando = false
            enVivo = foto == null
            estadoOcr = if (lineas.isEmpty()) "No se reconoció texto. Intenta con más luz y el ticket centrado."
            else "Datos llenados desde la foto. Revísalos y corrige lo que haga falta antes de guardar."
            onOcrLeido(ResultadoLaboratorioOcrParser.parsear(lineas), foto)
        },
        onError = { procesando = false; estadoOcr = it },
    )
    // Tras guardar, la foto se limpia: el visor vuelve a la cámara en vivo.
    LaunchedEffect(state.foto) { if (state.foto == null) enVivo = true }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val tomarFoto: () -> Unit = {
        when {
            procesando -> Unit
            !enVivo -> {
                // "Otra foto": vuelve a la cámara en vivo, en el mismo visor.
                enVivo = true
                estadoOcr = null
                scope.launch { scroll.animateScrollTo(0) }
            }
            else -> {
                procesando = true
                estadoOcr = "Leyendo el resultado…"
                ocr.lanzar()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        EncabezadoProveedor(proveedor, onDesbloquear)

        // 1. TOMAR FOTO
        TituloSeccion("Tomar foto", modifier = Modifier.padding(top = 14.dp))
        VisorFoto(
            foto = state.foto.takeIf { !enVivo },
            ocr = ocr,
            procesando = procesando,
            onCapturar = tomarFoto,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            estadoOcr ?: if (ocr.disponible) "Enfoca el ticket del analizador y presiona el botón: los valores se llenan solos y puedes editarlos."
            else "La cámara solo está disponible en Android. Llena los datos a mano.",
            style = CuerpoStyle,
            color = GrisTexto,
            modifier = Modifier.padding(top = 6.dp),
        )

        // 2. FORMULARIO DE DATOS
        TituloSeccion("Formulario de datos", modifier = Modifier.padding(top = 14.dp))
        SectionCard(modifier = Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Science, contentDescription = null, tint = VerdePrimario)
                Text(if (state.editando) "Editar resultado de laboratorio" else "Resultado de laboratorio", style = TituloPantallaStyle, color = Negro, modifier = Modifier.padding(start = 8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoFecha(state.fecha, onFechaChange, Modifier.weight(1f))
                val opciones = listOf(OpcionTurno(null, "Sin turno")) + state.turnos.map { OpcionTurno(it.id, it.nombre) }
                AppDropdown(
                    seleccionado = opciones.firstOrNull { it.id == state.turnoId },
                    opciones = opciones,
                    etiqueta = "Turno (opcional)",
                    textoOpcion = { it.nombre },
                    onSeleccionar = { onTurnoChange(it.id) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(VerdeClaro, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) {
                    Text("Litraje que recogieron los acopiadores", style = CuerpoStyle.copy(fontSize = 11.sp), color = GrisTexto)
                    Text(
                        state.litrosRecogidos?.let { litrosConDecimales(it) } ?: "Sin recojo en esta fecha",
                        style = CuerpoMedioStyle.copy(fontSize = 15.sp),
                        color = VerdeOscuro,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                AppTextField(
                    state.litros,
                    onLitrosChange,
                    "Litraje que llegó a planta",
                    teclado = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = VerdeOscuro, modifier = Modifier.size(18.dp))
                Text("Análisis (SmartScan AI)", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(start = 6.dp))
            }
            CampoAnalisis.entries.chunked(3).forEach { fila ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { campo ->
                        CampoNumero(
                            campo = campo,
                            valor = state.analisis[campo].orEmpty(),
                            onValorCambia = { onAnalisisChange(campo, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            AppTextField(state.observaciones, onObservacionesChange, "Observaciones", lineasMax = 3, modifier = Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onConformeChange(!state.conforme) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Resultado conforme", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.conforme,
                    onCheckedChange = onConformeChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = VerdePrimario, checkedThumbColor = Blanco),
                )
            }
            if (!state.conforme && !state.editando) {
                Text(
                    "Esto sumará una alteración de calidad al proveedor. Al llegar a ${Proveedor.MAX_ALTERACIONES}, se bloquea automáticamente." +
                        (state.resultadoDetectado?.takeIf { it != ResultadoTipo.CONFORME }?.let { " Detectado: ${etiquetaResultado(it)}." } ?: ""),
                    style = CuerpoStyle,
                    color = RojoTexto,
                )
            }

            state.error?.let { Aviso(it, esError = true) }
            state.ultimoRegistro?.let { Aviso(it.mensaje, esError = false, onCerrar = onCerrarMensaje) }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ocr.disponible) {
                    SecondaryButton(texto = if (enVivo) "Tomar foto" else "Otra foto", onClick = tomarFoto, modifier = Modifier.weight(1f))
                }
                PrimaryButton(texto = "Guardar", onClick = onRegistrar, cargando = state.guardando, modifier = Modifier.weight(1f))
            }
        }

        // 3. HISTORIAL
        TituloSeccion("Historial", modifier = Modifier.padding(top = 14.dp))
        SectionCard(modifier = Modifier.padding(top = 10.dp)) {
            if (state.historial.isEmpty()) {
                Text("Este proveedor aún no tiene resultados de laboratorio.", style = CuerpoStyle, color = GrisTexto)
            }
            state.historial.forEachIndexed { index, resultado ->
                if (index > 0) DividerLine(modifier = Modifier.padding(vertical = 8.dp))
                HistorialItem(resultado)
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun EncabezadoProveedor(proveedor: Proveedor, onDesbloquear: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(proveedor.nombreCompleto, style = TituloPantallaStyle, color = Negro)
                Text(proveedor.fincaUbicacion, style = CuerpoStyle, color = GrisTexto)
            }
            when {
                proveedor.bloqueado -> EstadoChip("Bloqueado", EstiloChip.ERROR)
                proveedor.alteraciones > 0 -> EstadoChip("En observación ${proveedor.alteraciones}/${Proveedor.MAX_ALTERACIONES}", EstiloChip.ADVERTENCIA)
                else -> EstadoChip("Sin alteraciones", EstiloChip.EXITO)
            }
        }
        // Como en Laravel: se bloquea solo al llegar a  alteraciones; planta puede desbloquearlo.
        if (proveedor.bloqueado) {
            SecondaryButton(texto = "Desbloquear proveedor", onClick = onDesbloquear, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Visor oscuro tipo cámara: muestra la foto tomada o un marco para enfocar el ticket. */
@Composable
private fun VisorFoto(foto: ImageBitmap?, ocr: LabOcrCapture, procesando: Boolean, onCapturar: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(20.dp)
    val disponible = ocr.disponible && !procesando
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(440.dp)
            .clip(forma)
            .background(Brush.verticalGradient(listOf(VerdeProfundo, Color(0xFF26352D)))),
    ) {
        if (foto != null) {
            Image(foto, contentDescription = "Foto del resultado", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
        } else {
            if (ocr.disponible) LabCamaraPreview(ocr, Modifier.fillMaxSize())
            // Marco grande: el ticket entero (todas sus líneas) cabe adentro y el OCR lee mejor.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp)
                    .fillMaxSize()
                    .border(3.dp, Ambar, RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Enfoque el ticket del analizador",
                    style = CuerpoMedioStyle.copy(fontSize = 15.sp),
                    color = Ambar,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .border(3.dp, Blanco, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(if (disponible) Blanco else Blanco.copy(alpha = 0.4f))
                    .clickable(enabled = disponible, onClick = onCapturar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Capturar", tint = VerdeProfundo, modifier = Modifier.size(22.dp))
            }
            Text(
                when {
                    procesando -> "Leyendo…"
                    foto == null -> "Capturar"
                    else -> "Volver a capturar"
                },
                style = EtiquetaMayusculaStyle,
                color = Blanco,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoFecha(fecha: LocalDate, onFechaChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = fecha.formatDdMmmYyyy(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Fecha") },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VerdePrimario),
        )
        // Capa transparente encima: el campo es de solo lectura y al tocarlo abre el calendario.
        Box(Modifier.matchParentSize().clickable { abierto = true })
    }
    if (abierto) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fecha.toEpochDays() * MILIS_DIA)
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onFechaChange(Epoca.plus(DatePeriod(days = (it / MILIS_DIA).toInt()))) }
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }
}

/**
 * Campo compacto con la etiqueta arriba, para la cuadrícula de 3 columnas. El teclado depende
 * del campo: decimal para las medidas, numérico para SN/Mode y con ":" o "/" para hora y fecha.
 */
@Composable
private fun CampoNumero(campo: CampoAnalisis, valor: String, onValorCambia: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(campo.etiqueta, style = CuerpoStyle.copy(fontSize = 11.sp), color = GrisTexto, maxLines = 1)
        OutlinedTextField(
            value = valor,
            onValueChange = onValorCambia,
            singleLine = true,
            textStyle = CuerpoMedioStyle.copy(fontSize = 14.sp, color = Negro),
            placeholder = campo.ejemplo?.let { { Text(it, style = CuerpoStyle.copy(fontSize = 13.sp), color = GrisTexto.copy(alpha = 0.6f)) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = when (campo.tipo) {
                    TipoCampo.DECIMAL -> KeyboardType.Decimal
                    TipoCampo.ENTERO -> KeyboardType.Number
                    // Hora "09:42" y fecha "06/06/26" necesitan ":" y "/": teclado de texto.
                    TipoCampo.HORA, TipoCampo.FECHA -> KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VerdePrimario, cursorColor = VerdePrimario),
        )
    }
}

@Composable
private fun Aviso(texto: String, esError: Boolean, onCerrar: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(if (esError) RojoFondo else VerdeClaro, RoundedCornerShape(10.dp))
            .then(if (onCerrar != null) Modifier.clickable(onClick = onCerrar) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (esError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (esError) RojoTexto else VerdeOscuro,
            modifier = Modifier.size(18.dp),
        )
        Text(texto, style = CuerpoMedioStyle, color = if (esError) RojoTexto else VerdeOscuro, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun HistorialItem(resultado: ResultadoLaboratorio) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(resultado.fecha.formatDdMmmYyyy(), style = CuerpoMedioStyle, color = Negro)
            val detalle = listOfNotNull(
                resultado.litros?.let { litrosConDecimales(it) },
                resultado.grasa?.let { "Grasa $it%" },
                resultado.densidad?.let { "Dens. $it" },
            ).joinToString(" · ")
            if (detalle.isNotBlank()) Text(detalle, style = CuerpoStyle, color = GrisTexto)
            // Datos del ticket del analizador: SN y la hora/fecha que imprimió.
            val ticket = listOfNotNull(
                resultado.sn?.let { "SN $it" },
                listOfNotNull(resultado.horaAnalisis, resultado.fechaAnalisis).joinToString(" ").ifBlank { null },
            ).joinToString(" · ")
            if (ticket.isNotBlank()) Text(ticket, style = CuerpoStyle.copy(fontSize = 12.sp), color = GrisTexto)
        }
        EstadoChip(etiquetaResultado(resultado.resultado), if (resultado.conforme) EstiloChip.EXITO else EstiloChip.ERROR)
    }
}
