package com.example.ecolacteoshuata.core.theme

import androidx.compose.ui.graphics.Color

// Verde general de Laravel (--color-accent = huata-verde-700).
val VerdePrimario = Color(0xFF17663D)
val VerdeOscuro = Color(0xFF155134)
val VerdeProfundo = Color(0xFF0F2E1E)
val VerdeClaro = Color(0xFFEEF8F2)
val VerdeMenta = Color(0xFF8FE3B4)

val Celeste = Color(0xFF4FBEE6)
val CelesteClaro = Color(0xFFEAF7FC)
val CelesteTexto = Color(0xFF1E7E9E)

val Negro = Color(0xFF14211B)
val GrisTexto = Color(0xFF7C9086)
val Blanco = Color(0xFFFFFFFF)
val FondoApp = Color(0xFFF5F8F6)
val Borde = Color(0xFFE4EDE7)

val Ambar = Color(0xFFF0B429)
val AmbarFondo = Color(0xFFFFF6E3)
val AmbarTexto = Color(0xFF8A6410)

val Rojo = Color(0xFFD14343)
val RojoFondo = Color(0xFFFDF0F0)
val RojoTexto = Color(0xFFB93A3A)

val PillActivoFondo = Color(0xFFD8F0E3)

// Paleta de acentos para las tarjetas de módulos (se reparten en orden para dar variedad).
val AcentoVerde = Color(0xFF17663D)
val AcentoCeleste = Color(0xFF1E9BC8)
val AcentoAmbar = Color(0xFFE09A12)
val AcentoIndigo = Color(0xFF5B5BD6)
val AcentoCoral = Color(0xFFE0674B)
val AcentoLima = Color(0xFF6B9E1F)
val AcentoMagenta = Color(0xFFB2458F)
val AcentoPizarra = Color(0xFF3F6475)

val PaletaModulos = listOf(AcentoVerde, AcentoCeleste, AcentoAmbar, AcentoIndigo, AcentoCoral, AcentoLima, AcentoMagenta, AcentoPizarra)

/** Identidad visual de cada rol: degradado de la cabecera y color de acento. */
data class TemaRol(val degradadoInicio: Color, val degradadoFin: Color, val acento: Color)

// Todos los roles usan el verde general de Laravel (#17663D), como en la web.
private val TemaHuata = TemaRol(Color(0xFF0B3B24), VerdePrimario, VerdePrimario)

val TemaAdmin = TemaHuata
val TemaVendedor = TemaHuata
val TemaProduccion = TemaHuata
val TemaPlanta = TemaHuata
val TemaAcopiador = TemaHuata
val TemaProveedor = TemaHuata
