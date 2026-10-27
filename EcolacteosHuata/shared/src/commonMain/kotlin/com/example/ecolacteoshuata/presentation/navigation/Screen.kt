package com.example.ecolacteoshuata.presentation.navigation

sealed class Screen {
    data object Login : Screen()

    /** "Tomar asistencia": del administrador o de quien él se lo asigne, desde cualquier rol. */
    data object TomarAsistencia : Screen()

    /** "Asistencia" (mis asistencias): marcar y justificar faltas o tardanzas. */
    data object MiAsistencia : Screen()

    data object AdminDashboard : Screen()
    data object AdminUsuarios : Screen()
    data object AdminAsignarRoles : Screen()
    data object AdminFinanzas : Screen()
    data object AdminRoles : Screen()
    data object AdminDescuentos : Screen()
    data object AdminPrecios : Screen()
    data object AdminRutas : Screen()
    data class AdminRutaDetalle(val acopiadorId: String) : Screen()
    data object AdminTurnos : Screen()
    data object AdminEmpresa : Screen()
    data object AdminComunicados : Screen()

    data object VendedorDashboard : Screen()
    data object VendedorVenta : Screen()
    data object VendedorProductos : Screen()
    data object VendedorHistorialVentas : Screen()
    data object VendedorClientes : Screen()
    data object VendedorInventario : Screen()

    data object ProduccionDashboard : Screen()
    data object ProduccionRegistros : Screen()
    data object ProduccionRecetas : Screen()
    data object ProduccionInsumos : Screen()
    data object ProduccionUnidades : Screen()
    data object ProduccionProductos : Screen()
    data object ProduccionCompras : Screen()
    data object ProduccionInventario : Screen()

    data object PlantaDashboard : Screen()
    data object PlantaRecepcion : Screen()
    data object PlantaLaboratorio : Screen()
    data class PlantaLaboratorioRegistro(val proveedorId: String) : Screen()
    data object PlantaRutas : Screen()
    data class PlantaRutaDetalle(val acopiadorId: String) : Screen()
    data object PlantaPagos : Screen()
    data object PlantaComunicados : Screen()

    data object AcopiadorDashboard : Screen()
    data object AcopiadorRecojo : Screen()
    data object AcopiadorMapaRecojo : Screen()
    data class AcopiadorRecojoForm(val proveedorId: String, val turnoId: String) : Screen()
    data object AcopiadorEntrega : Screen()
    data object AcopiadorInventario : Screen()

    data object ProveedorDashboard : Screen()
    data object ProveedorEntregas : Screen()
    data object ProveedorLaboratorio : Screen()
    data object ProveedorComunicados : Screen()
}
