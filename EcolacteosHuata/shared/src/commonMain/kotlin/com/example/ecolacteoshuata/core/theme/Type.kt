package com.example.ecolacteoshuata.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Sustituir por las fuentes Sora y Manrope reales colocándolas en composeResources/font
// y registrándolas aquí con FontFamily(Font(...)). Mientras tanto se usa la fuente del sistema.
val SoraFontFamily = FontFamily.Default
val ManropeFontFamily = FontFamily.Default

val TituloPantallaStyle = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp)
val KpiStyle = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp)
val CifraGrandeStyle = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 42.sp)
val UbicacionFincaStyle = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp)
val CuerpoStyle = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp)
val CuerpoMedioStyle = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp)
val EtiquetaMayusculaStyle = TextStyle(
    fontFamily = ManropeFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 10.5.sp,
    letterSpacing = 0.08.em,
)

val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TituloPantallaStyle,
    titleSmall = TextStyle(fontFamily = SoraFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = CuerpoStyle,
    bodySmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = CuerpoMedioStyle,
    labelSmall = EtiquetaMayusculaStyle,
)
