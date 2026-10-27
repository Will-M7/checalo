package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Dimens
import com.example.ecolacteoshuata.core.theme.VerdePrimario

@Composable
fun PrimaryButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        modifier = modifier.fillMaxWidth().height(Dimens.buttonHeight),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.buttonRadius),
        colors = ButtonDefaults.buttonColors(containerColor = VerdePrimario, contentColor = Blanco),
    ) {
        if (cargando) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Blanco, strokeWidth = 2.dp)
        } else {
            Text(texto)
        }
    }
}

@Composable
fun SecondaryButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().height(Dimens.buttonHeight),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.buttonRadius),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = VerdePrimario),
    ) {
        Text(texto)
    }
}
