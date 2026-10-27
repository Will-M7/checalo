package com.example.ecolacteoshuata.core.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val MESES_ES = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
)

@OptIn(ExperimentalTime::class)
fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

@OptIn(ExperimentalTime::class)
fun nowLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDate.formatDdMmmYyyy(): String {
    val dia = day.toString().padStart(2, '0')
    val mes = MESES_ES[month.number - 1]
    return "$dia $mes $year"
}

fun LocalDateTime.formatDdMmmYyyy(): String = date.formatDdMmmYyyy()

fun LocalDateTime.formatDdMmmYyyyHm(): String {
    val hora = hour.toString().padStart(2, '0')
    val minuto = minute.toString().padStart(2, '0')
    return "${date.formatDdMmmYyyy()} $hora:$minuto"
}

fun LocalDate.plusDias(dias: Int): LocalDate = this.plus(DatePeriod(days = dias))

fun LocalDate.startOfWeek(): LocalDate {
    val diasDesdeLunes = dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
    return this.minus(DatePeriod(days = diasDesdeLunes))
}

fun LocalDate.endOfWeek(): LocalDate = startOfWeek().plusDias(6)

fun LocalDate.startOfMonth(): LocalDate = LocalDate(year, month.number, 1)

fun LocalDate.endOfMonth(): LocalDate {
    val nextMonth = if (month.number == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month.number + 1, 1)
    return nextMonth.plusDias(-1)
}

fun LocalDate.startOfYear(): LocalDate = LocalDate(year, 1, 1)

fun LocalDate.endOfYear(): LocalDate = LocalDate(year, 12, 31)

fun formatLitros(litros: Double): String {
    val redondeado = redondear2(litros)
    return if (redondeado == redondeado.toLong().toDouble()) {
        "${redondeado.toLong()} L"
    } else {
        "$redondeado L"
    }
}
