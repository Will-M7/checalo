package com.example.ecolacteoshuata.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val EcolacteosColorScheme = lightColorScheme(
    primary = VerdePrimario,
    onPrimary = Blanco,
    primaryContainer = VerdeClaro,
    onPrimaryContainer = VerdeOscuro,
    secondary = Celeste,
    onSecondary = Blanco,
    secondaryContainer = CelesteClaro,
    onSecondaryContainer = CelesteTexto,
    background = FondoApp,
    onBackground = Negro,
    surface = Blanco,
    onSurface = Negro,
    surfaceVariant = VerdeClaro,
    onSurfaceVariant = GrisTexto,
    outline = Borde,
    error = Rojo,
    onError = Blanco,
    errorContainer = RojoFondo,
    onErrorContainer = RojoTexto,
)

/** Escala de todo el texto de la app (1f = tamaño normal). Se reduce para que la letra sea más pequeña. */
private const val EscalaTexto = 0.88f

@Composable
fun EcolacteosHuataTheme(content: @Composable () -> Unit) {
    val densidad = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(densidad.density, densidad.fontScale * EscalaTexto),
    ) {
        MaterialTheme(
            colorScheme = EcolacteosColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
