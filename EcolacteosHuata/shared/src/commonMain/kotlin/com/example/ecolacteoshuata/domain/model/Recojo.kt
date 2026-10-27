package com.example.ecolacteoshuata.domain.model

import kotlinx.datetime.LocalDate

data class Recojo(
    val id: String,
    val proveedorId: String,
    val proveedorNombre: String = "",
    val acopiadorId: String,
    val turnoId: String,
    val fecha: LocalDate,
    val litros: Double,
    /** Lo que entregó el proveedor; [litros] es el litraje que llegó a planta (lo que se paga). */
    val litrosEntregados: Double? = null,
    val estadoLeche: EstadoLeche,
    val estadoOtro: String,
    val precioLitro: Double,
    val total: Double,
    val pagado: Boolean,
    val metodoPago: MetodoPago?,
    val entregado: Boolean,
    val sincronizado: Boolean,
)

data class EntregaPlanta(
    val id: String,
    val acopiadorId: String,
    val turnoId: String,
    val fecha: LocalDate,
    val litrosTotal: Double,
    val totalPagado: Double,
    val totalDeuda: Double,
    val proveedoresNoRecogidos: Int,
    val guia: String,
)

data class ResultadoLaboratorio(
    val id: String,
    val proveedorId: String,
    val fecha: LocalDate,
    val turnoId: String?,
    val resultado: ResultadoTipo,
    val densidad: Double?,
    val acidez: Double?,
    val temperatura: Double?,
    val observaciones: String,
    val conforme: Boolean,
    /** Litraje que llegó a planta (el definitivo). */
    val litros: Double? = null,
    val grasa: Double? = null,
    val sng: Double? = null,
    val proteina: Double? = null,
    val lactosa: Double? = null,
    val sales: Double? = null,
    val totalSolidos: Double? = null,
    val aguaAnadida: Double? = null,
    val puntoCongelacion: Double? = null,
    val ph: Double? = null,
    /** Número de serie del analizador ("SN: 48359" en el ticket). */
    val sn: String? = null,
    /** Modo del analizador ("Mode: 1"). */
    val modo: String? = null,
    /** Hora y fecha que imprime el ticket ("09:42  06/06/26"). */
    val horaAnalisis: String? = null,
    val fechaAnalisis: String? = null,
    /** Planta ya envió esta leche a producción (se sumó al stock del insumo leche). */
    val enviadoProduccion: Boolean = false,
)
