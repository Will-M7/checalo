package com.example.ecolacteoshuata.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.PaletaModulos
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.domain.model.Rol

/**
 * Roles del usuario y cuál se está usando. Un usuario puede tener varios roles (como en
 * Laravel, donde el menú muestra una sección por rol): en el teléfono se trabaja con uno a la
 * vez y se cambia desde el chip del rol en el encabezado.
 */
data class SelectorRol(
    val roles: List<com.example.ecolacteoshuata.domain.model.RolAsignado>,
    val actual: com.example.ecolacteoshuata.domain.model.RolAsignado,
    val onCambiar: (com.example.ecolacteoshuata.domain.model.RolAsignado) -> Unit,
) {
    val tieneVarios: Boolean get() = roles.size > 1
}

val LocalSelectorRol = staticCompositionLocalOf<SelectorRol?> { null }

fun Rol.icono(): ImageVector = when (this) {
    Rol.ADMIN -> Icons.Filled.AdminPanelSettings
    Rol.PERSONAL_PLANTA -> Icons.Filled.Science
    Rol.ACOPIADOR -> Icons.Filled.LocalShipping
    Rol.PROVEEDOR -> Icons.Filled.Agriculture
    Rol.VENDEDOR -> Icons.Filled.PointOfSale
    Rol.PRODUCCION -> Icons.Filled.Factory
}

private fun Rol.descripcion(): String = when (this) {
    Rol.ADMIN -> "Usuarios, rutas, precios y configuración"
    Rol.PERSONAL_PLANTA -> "Laboratorio, entregas, pagos y rutas"
    Rol.ACOPIADOR -> "Recojo de ruta y entrega a planta"
    Rol.PROVEEDOR -> "Mis entregas, laboratorio y comunicados"
    Rol.VENDEDOR -> "Venta, historial, clientes e inventario"
    Rol.PRODUCCION -> "Producción, recetas, insumos y compras"
}

/** Hoja "Cambiar de rol": un tarjetón por rol; el actual va marcado. */
@Composable
fun CambiarRolSheet(selector: SelectorRol, onDismiss: () -> Unit) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario("Cambiar de rol", "Tienes ${selector.roles.size} roles: elige con cuál trabajar.")
            selector.roles.forEachIndexed { i, rol ->
                val actual = rol == selector.actual
                val color = PaletaModulos[i % PaletaModulos.size]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (actual) VerdeClaro else Color.Transparent)
                        .border(1.dp, if (actual) VerdePrimario.copy(alpha = 0.5f) else GrisTexto.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            if (!actual) selector.onCambiar(rol)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon((Rol.fromServerValue(rol.id) ?: Rol.VENDEDOR).icono(), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(rol.etiqueta, style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold)
                        Text("${rol.pantallas.size} funciones asignadas", style = CuerpoStyle, color = GrisTexto)
                    }
                    if (actual) Icon(Icons.Filled.CheckCircle, contentDescription = "Rol actual", tint = VerdePrimario)
                }
            }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }
}
