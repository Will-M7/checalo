package com.example.ecolacteoshuata.domain.model

enum class TipoReporte {
    LECHE_POR_ACOPIADOR,
    USUARIOS,
    CLIENTES_FRECUENTES,
    PRODUCTOS,
    PRECIOS,
    VENTAS,
    RECEPCION_PLANTA,
    DESCUENTOS_APLICADOS,
    PROVEEDORES_PAGADOS_POR_PAGAR,
    RESULTADOS_LABORATORIO,
    LECHE_POR_PROVEEDOR,
    LITROS_POR_TURNO,
    ENTREGAS_PROVEEDOR,
}

data class ReporteTabla(
    val titulo: String,
    val subtitulo: String,
    val columnas: List<String>,
    val filas: List<List<String>>,
    val totales: List<String> = emptyList(),
)
