package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.Negro
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.ecolacteoshuata.core.theme.VerdePrimario

@Composable
fun AppTextField(
    valor: String,
    onValorCambia: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    lineasMax: Int = 1,
    esError: Boolean = false,
    soloLectura: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorCambia,
        label = { Text(etiqueta) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = lineasMax == 1,
        maxLines = lineasMax,
        isError = esError,
        readOnly = soloLectura,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = teclado),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VerdePrimario, cursorColor = VerdePrimario),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppDropdown(
    seleccionado: T?,
    opciones: List<T>,
    etiqueta: String,
    textoOpcion: (T) -> String,
    onSeleccionar: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { expandido = it }, modifier = modifier) {
        OutlinedTextField(
            value = seleccionado?.let(textoOpcion) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VerdePrimario, cursorColor = VerdePrimario),
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(textoOpcion(opcion)) },
                    onClick = {
                        onSeleccionar(opcion)
                        expandido = false
                    },
                )
            }
        }
    }
}

/** Casilla con texto que se marca tocando toda la fila. */
@Composable
fun CasillaTexto(texto: String, marcado: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = marcado,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(checkedColor = VerdePrimario),
        )
        Text(texto, style = CuerpoMedioStyle, color = Negro)
    }
}
