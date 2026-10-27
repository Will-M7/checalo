package com.example.ecolacteoshuata.presentation.admin.config

import com.example.ecolacteoshuata.core.ui.TituloSeccion
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.domain.model.Empresa

@Composable
fun AdminConfiguracionScreen(empresa: Empresa?, onGuardar: (String, String, String, String) -> Unit) {
    var razonSocial by remember(empresa?.id) { mutableStateOf(empresa?.razonSocial ?: "") }
    var ruc by remember(empresa?.id) { mutableStateOf(empresa?.ruc ?: "") }
    var telefono by remember(empresa?.id) { mutableStateOf(empresa?.telefono ?: "") }
    var direccion by remember(empresa?.id) { mutableStateOf(empresa?.direccion ?: "") }

    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        SectionCard {
            TituloSeccion("Datos de la boleta")
            AppTextField(razonSocial, { razonSocial = it }, "Razón social")
            AppTextField(ruc, { ruc = it }, "RUC", teclado = KeyboardType.Number, modifier = Modifier.padding(top = 10.dp))
            AppTextField(telefono, { telefono = it }, "Teléfono", teclado = KeyboardType.Phone, modifier = Modifier.padding(top = 10.dp))
            AppTextField(direccion, { direccion = it }, "Dirección", modifier = Modifier.padding(top = 10.dp))
            PrimaryButton(
                texto = "Guardar",
                onClick = { onGuardar(razonSocial, ruc, telefono, direccion) },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
