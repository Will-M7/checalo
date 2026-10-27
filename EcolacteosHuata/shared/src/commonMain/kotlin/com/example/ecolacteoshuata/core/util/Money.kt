package com.example.ecolacteoshuata.core.util

import kotlin.math.abs
import kotlin.math.round

expect fun formatMoney(amount: Double): String

internal fun formatMoneyCommon(amount: Double): String {
    val rounded = round(amount * 100) / 100.0
    val negative = rounded < 0
    val totalCents = round(abs(rounded) * 100).toLong()
    val soles = totalCents / 100
    val centavos = totalCents % 100
    return (if (negative) "-S/ " else "S/ ") + groupThousands(soles) + "." + centavos.toString().padStart(2, '0')
}

private fun groupThousands(value: Long): String {
    val digits = value.toString()
    val sb = StringBuilder()
    for ((index, c) in digits.reversed().withIndex()) {
        if (index != 0 && index % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.reverse().toString()
}

/** Litros siempre con dos decimales ("12.50 L"), igual que en Laravel. */
fun litrosConDecimales(litros: Double): String = "${formatMoneyCommon(litros).removePrefix("S/ ")} L"

fun redondear2(value: Double): Double = round(value * 100) / 100.0

fun calcularTotalRecojo(litros: Double, precioLitro: Double): Double = redondear2(litros * precioLitro)

fun calcularVuelto(total: Double, efectivoRecibido: Double): Double? =
    redondear2(efectivoRecibido - total).takeIf { it > 0.0 }
