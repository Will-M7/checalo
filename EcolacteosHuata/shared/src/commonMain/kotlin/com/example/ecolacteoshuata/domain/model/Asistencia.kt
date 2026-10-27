package com.example.ecolacteoshuata.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** `App\Enums\EstadoAsistencia` de Laravel. */
enum class EstadoAsistencia {
    PRESENTE, TARDANZA, FALTA;

    val etiqueta: String
        get() = when (this) {
            PRESENTE -> "Presente"
            TARDANZA -> "Tardanza"
            FALTA -> "Falta"
        }

    companion object {
        fun desde(valor: String?): EstadoAsistencia? = entries.firstOrNull { it.name.equals(valor, ignoreCase = true) }
    }
}

/** `App\Enums\EstadoJustificacion` de Laravel. */
enum class EstadoJustificacion {
    PENDIENTE, ACEPTADA, RECHAZADA;

    val etiqueta: String
        get() = when (this) {
            PENDIENTE -> "Pendiente"
            ACEPTADA -> "Aceptada"
            RECHAZADA -> "Rechazada"
        }

    companion object {
        fun desde(valor: String?): EstadoJustificacion = entries.firstOrNull { it.name.equals(valor, ignoreCase = true) } ?: PENDIENTE
    }
}

/**
 * Horario en que los acopiadores marcan su asistencia. Ej. "Mañana": de 05:00 a 05:10 es
 * presente, de 05:11 a 05:20 es tardanza y quien no marcó hasta entonces queda con falta.
 * Las horas van como "HH:MM".
 */
data class HorarioAsistencia(
    val id: String,
    val nombre: String,
    val horaInicio: String,
    val limitePresente: String,
    val limiteTardanza: String,
    val activo: Boolean,
) {
    /** Primer minuto que ya cuenta como tardanza. */
    val inicioTardanza: String
        get() {
            val (h, m) = limitePresente.split(":").map { it.toIntOrNull() ?: 0 }
            val total = (h * 60 + m + 1) % (24 * 60)
            return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
        }
}

/** Sin [horarioId] es la asistencia general; con horario, la marca de un acopiador. */
data class Asistencia(
    val id: String,
    val usuarioId: String,
    val fecha: LocalDate,
    val horarioId: String?,
    val estado: EstadoAsistencia,
    val horaMarcada: String?,
)

/** Justificación de una falta o tardanza, con los datos de la asistencia a la que pertenece. */
data class Justificacion(
    val id: String,
    val asistenciaId: String,
    val usuarioId: String,
    val fecha: LocalDate,
    val horarioId: String?,
    val estadoAsistencia: EstadoAsistencia,
    val motivo: String,
    val tieneArchivo: Boolean,
    val estado: EstadoJustificacion,
    val enviadaEn: LocalDate?,
)

/** Una asistencia del propio trabajador ("Asistencia" en Laravel), con su justificación. */
data class MiAsistencia(
    val asistencia: Asistencia,
    val justificacionId: String?,
    val justificacionEstado: EstadoJustificacion?,
) {
    /** Último día (inclusive) para justificar: `Asistencia::fechaLimiteJustificacion()`. */
    val fechaLimite: LocalDate get() = asistencia.fecha.plus(DatePeriod(days = DIAS_PARA_JUSTIFICAR))

    fun diasRestantes(hoy: LocalDate): Int = maxOf(0, (fechaLimite.toEpochDays() - hoy.toEpochDays()).toInt())

    /** `Asistencia::puedeJustificarse()`: falta o tardanza, dentro de la semana y sin justificación vigente. */
    fun puedeJustificarse(hoy: LocalDate): Boolean =
        asistencia.estado != EstadoAsistencia.PRESENTE &&
            hoy <= fechaLimite &&
            (justificacionId == null || justificacionEstado == EstadoJustificacion.RECHAZADA)

    companion object {
        /** `Asistencia::DIAS_PARA_JUSTIFICAR`. */
        const val DIAS_PARA_JUSTIFICAR = 7
    }
}

/** `HorarioAsistencia::estadoParaHora`: presente, tardanza o `null` si no está abierto a esa hora ("HH:MM"). */
fun HorarioAsistencia.estadoParaHora(hora: String): EstadoAsistencia? = when {
    hora < horaInicio -> null
    hora <= limitePresente -> EstadoAsistencia.PRESENTE
    hora <= limiteTardanza -> EstadoAsistencia.TARDANZA
    else -> null
}
