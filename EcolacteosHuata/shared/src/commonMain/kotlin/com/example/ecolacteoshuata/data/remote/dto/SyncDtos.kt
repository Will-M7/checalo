package com.example.ecolacteoshuata.data.remote.dto

import kotlinx.serialization.Serializable

// Contrato JSON de `GET /api/mobile/sync` (MobileSyncController de Laravel). Los nombres van en
// snake_case igual que el JSON; los campos que Laravel puede no mandar tienen valor por defecto.

@Serializable
data class SyncResponseDto(
    val servidor_hora: String,
    val altas: List<AltaSyncDto> = emptyList(),
    val autorizacion: com.example.ecolacteoshuata.domain.model.AutorizacionUsuario? = null,
    val empresa: List<EmpresaDto> = emptyList(),
    val precio_leche: List<PrecioLecheDto> = emptyList(),
    val usuarios: List<UsuarioSyncDto> = emptyList(),
    val acopiadores: List<AcopiadorDto> = emptyList(),
    val proveedores: List<ProveedorSyncDto> = emptyList(),
    val turnos: List<TurnoDto> = emptyList(),
    val turnos_produccion: List<TurnoProduccionDto> = emptyList(),
    val rutas: List<RutaSyncDto> = emptyList(),
    val clientes: List<ClienteSyncDto> = emptyList(),
    val unidades_medida: List<UnidadMedidaDto> = emptyList(),
    val insumos: List<InsumoSyncDto> = emptyList(),
    val recetas: List<RecetaSyncDto> = emptyList(),
    val receta_detalles: List<RecetaDetalleSyncDto> = emptyList(),
    val comunicados: List<ComunicadoSyncDto> = emptyList(),
    val recojos: List<RecojoSyncDto> = emptyList(),
    val resultados_laboratorio: List<ResultadoLaboratorioSyncDto> = emptyList(),
    val entregas_planta: List<EntregaPlantaDto> = emptyList(),
    val pagos_proveedor: List<PagoProveedorSyncDto> = emptyList(),
    val compras: List<CompraSyncDto> = emptyList(),
    val compra_detalles: List<CompraDetalleDto> = emptyList(),
    val descuentos: List<DescuentoSyncDto> = emptyList(),
    /** Producciones: lo que se vende (stock = producida − vendida). */
    val producciones: List<ProduccionSyncDto> = emptyList(),
    /** Ventas con sus ítems. */
    val ventas: List<VentaSyncDto> = emptyList(),
    /** "Tomar asistencia": horarios y días de los acopiadores, asistencias y justificaciones. */
    val horarios_asistencia: List<HorarioAsistenciaSyncDto> = emptyList(),
    val dias_asistencia_acopiador: List<DiaAsistenciaSyncDto> = emptyList(),
    val asistencias: List<AsistenciaSyncDto> = emptyList(),
    val justificaciones: List<JustificacionSyncDto> = emptyList(),
    /** Campana del usuario del token; `null` = el servidor no las manda (no se toca la lista local). */
    val notificaciones: List<NotificacionSyncDto>? = null,
    /** Ids que siguen existiendo en Laravel, por tabla local: lo que falte se borró allá. */
    val ids: Map<String, List<String>>? = null,
)

@Serializable
data class DescuentoSyncDto(val id: String, val nombre: String, val porcentaje: Double, val activo: Boolean)

@Serializable
data class EmpresaDto(val id: String, val razon_social: String, val ruc: String, val telefono: String, val direccion: String)

@Serializable
data class PrecioLecheDto(val id: String, val precio_litro: Double, val vigente_desde: String)

@Serializable
data class UsuarioSyncDto(
    val autorizacion: com.example.ecolacteoshuata.domain.model.AutorizacionUsuario? = null,
    val id: String,
    val nombres: String,
    val apellidos: String,
    val documento: String,
    val celular: String,
    val rol: String?,
    val usuario: String,
    val activo: Boolean,
    /** Todos los roles del usuario (`rolesAsignados()` de Laravel), el principal incluido. */
    val roles: List<String> = emptyList(),
    /** Hash bcrypt de Laravel: permite iniciar sesión sin señal (nunca llega la contraseña). */
    val password_hash: String? = null,
    /** El administrador le asignó "Tomar asistencia". */
    val toma_asistencia: Boolean = false,
)

@Serializable
data class AcopiadorDto(val id: String, val usuario_id: String, val ruta_nombre: String)

@Serializable
data class ProveedorSyncDto(
    val id: String,
    val usuario_id: String,
    val finca_ubicacion: String,
    val finca_referencia: String,
    val latitud: Double?,
    val longitud: Double?,
    val acopiador_id: String?,
    val bloqueado: Boolean,
    val alteraciones: Int,
)

@Serializable
data class TurnoDto(val id: String, val acopiador_id: String, val nombre: String, val hora_inicio: String, val hora_fin: String)

@Serializable
data class TurnoProduccionDto(val id: String, val nombre: String, val hora_inicio: String, val hora_fin: String)

@Serializable
data class RutaSyncDto(
    val id: String,
    val acopiador_id: String,
    val nombre: String,
    val activa: Boolean,
    /** En el orden de recojo (pivot `orden` de Laravel). */
    val proveedor_ids: List<String> = emptyList(),
    /** Camino trazado por planta: lista de [lat, lng]. */
    val trazo: List<List<Double>> = emptyList(),
)

@Serializable
data class ClienteSyncDto(val id: String, val nombres: String, val apellidos: String, val documento: String, val compras_count: Int)

@Serializable
data class UnidadMedidaDto(val id: String, val nombre: String, val simbolo: String)

@Serializable
data class InsumoSyncDto(
    val id: String,
    val nombre: String,
    val unidad_medida_id: String,
    val costo_unitario: Double = 0.0,
    /** Stock real en Laravel; `null` si el servidor todavía no lo manda. */
    val stock_actual: Double? = null,
)

@Serializable
data class ProduccionSyncDto(
    val id: String,
    val fecha: String,
    val nombre: String = "",
    val receta_id: String,
    val cantidad_producida: Double,
    val cantidad_vendida: Double = 0.0,
    val lote: String = "",
    val turno_id: String? = null,
    val responsable_id: String? = null,
    /** "planificada" o "completada". */
    val estado: String,
)

@Serializable
data class VentaItemSyncDto(
    val id: String,
    val produccion_id: String? = null,
    val nombre: String,
    val precio_unitario: Double,
    val cantidad: Int,
    val subtotal: Double,
)

@Serializable
data class VentaSyncDto(
    val id: String,
    val numero_boleta: String,
    val cliente_id: String? = null,
    val cliente_nombre_libre: String? = null,
    val cliente_doc_libre: String? = null,
    val vendedor_id: String? = null,
    /** Día y hora de registro: "2026-10-20T09:30:00". */
    val fecha: String,
    val subtotal: Double,
    val descuento: Double,
    val descuento_tipo: String = "",
    val total: Double,
    val metodo_pago: String,
    val efectivo_recibido: Double? = null,
    val vuelto: Double? = null,
    val items: List<VentaItemSyncDto> = emptyList(),
)

@Serializable
data class RecetaSyncDto(val id: String, val nombre: String)

@Serializable
data class RecetaDetalleSyncDto(val id: String, val receta_id: String, val insumo_id: String, val cantidad: Double)

@Serializable
data class ComunicadoSyncDto(
    val id: String,
    val tipo: String?,
    val titulo: String,
    val descripcion: String,
    val fecha: String,
    val autor_id: String,
    /** Roles destinatarios; `null` = todos los roles. */
    val roles: List<String>? = null,
)

@Serializable
data class RecojoSyncDto(
    val id: String,
    val proveedor_id: String,
    val acopiador_id: String,
    val turno_id: String,
    val fecha: String,
    val litros: Double,
    /** Lo que entregó el proveedor (`litros` es lo que llegó y se paga). */
    val litros_entregados: Double? = null,
    val estado_leche: String,
    val estado_otro: String,
    val precio_litro: Double,
    val total: Double,
    val pagado: Boolean,
    val metodo_pago: String?,
    val entregado: Boolean,
)

@Serializable
data class ResultadoLaboratorioSyncDto(
    val id: String,
    val proveedor_id: String,
    val fecha: String,
    val turno_id: String?,
    val resultado: String,
    val densidad: Double?,
    val acidez: Double?,
    val temperatura: Double?,
    val observaciones: String,
    val conforme: Boolean,
    val enviado_produccion: Boolean = false,
    val litros: Double? = null,
    val grasa: Double? = null,
    val sng: Double? = null,
    val proteina: Double? = null,
    val lactosa: Double? = null,
    val sales: Double? = null,
    val total_solidos: Double? = null,
    val agua_anadida: Double? = null,
    val punto_congelacion: Double? = null,
    val ph: Double? = null,
    val sn: String? = null,
    val modo: String? = null,
    val hora_analisis: String? = null,
    val fecha_analisis: String? = null,
)

@Serializable
data class EntregaPlantaDto(
    val id: String,
    val acopiador_id: String,
    val turno_id: String,
    val fecha: String,
    val litros_total: Double,
    val total_pagado: Double,
    val total_deuda: Double,
    val proveedores_no_recogidos: Int,
    val guia: String,
)

@Serializable
data class PagoProveedorSyncDto(
    val id: String,
    val proveedor_id: String,
    val recojo_id: String?,
    val fecha: String,
    val monto: Double,
    val metodo_pago: String,
    val periodo: String,
)

@Serializable
data class CompraSyncDto(val id: String, val fecha: String, val descripcion: String, val total: Double)

@Serializable
data class CompraDetalleDto(
    val id: String,
    val compra_id: String,
    val insumo_id: String,
    val cantidad: Double,
    val costo_unitario: Double,
    val subtotal: Double,
)

@Serializable
data class HorarioAsistenciaSyncDto(
    val id: String,
    val nombre: String,
    /** "HH:MM". */
    val hora_inicio: String,
    val hora_limite_presente: String,
    val hora_limite_tardanza: String,
    val activo: Boolean,
)

@Serializable
data class DiaAsistenciaSyncDto(val id: String, val fecha: String)

@Serializable
data class AsistenciaSyncDto(
    val id: String,
    val usuario_id: String,
    val fecha: String,
    /** `null` = asistencia general; con horario = marca de un acopiador. */
    val horario_asistencia_id: String? = null,
    /** "presente", "tardanza" o "falta". */
    val estado: String,
    val hora_marcada: String? = null,
    val registrado_por: String? = null,
)

@Serializable
data class JustificacionSyncDto(
    val id: String,
    val asistencia_id: String,
    val motivo: String,
    val tiene_archivo: Boolean = false,
    /** "pendiente", "aceptada" o "rechazada". */
    val estado: String,
    val enviada_en: String? = null,
)


@Serializable
data class NotificacionSyncDto(
    /** UUID de la tabla `notifications` de Laravel. */
    val id: String,
    val tipo: String,
    val titulo: String,
    val mensaje: String = "",
    val leida: Boolean = false,
    val creada_en: String? = null,
)

@Serializable
data class AltaSyncDto(val tabla: String, val registro_id: String, val creado_en: String?)
