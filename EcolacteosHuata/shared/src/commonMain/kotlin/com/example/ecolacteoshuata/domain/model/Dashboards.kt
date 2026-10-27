package com.example.ecolacteoshuata.domain.model

data class DashboardAdmin(
    val totalUsuarios: Long,
    val usuariosActivos: Long,
    val litrosHoy: Double,
    val ventasHoyCantidad: Long,
    val ventasHoyTotal: Double,
)

data class DashboardPlanta(
    val litrosRecibidosHoy: Double,
    val cantidadAcopiadores: Int,
    val cantidadProveedores: Int,
    val cantidadProductos: Long,
)

data class DashboardAcopiador(
    val litrosRecogidosHoy: Double,
    val proveedoresAsignados: Int,
    val proveedoresFaltantes: Int,
    val deudaTotal: Double,
)

data class DashboardProveedor(
    val acopiadorNombre: String,
    /** `ruta_nombre` del acopiador asignado. */
    val acopiadorRuta: String = "",
    val proximoTurno: String,
    val entregadoHoyLitros: Double,
    val entregadoSemanaLitros: Double,
    val entregadoMesLitros: Double,
    val porCobrar: Double,
)
