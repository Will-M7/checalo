package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Asistencia
import com.example.ecolacteoshuata.domain.model.EstadoAsistencia
import com.example.ecolacteoshuata.domain.model.EstadoJustificacion
import com.example.ecolacteoshuata.domain.model.HorarioAsistencia
import com.example.ecolacteoshuata.domain.model.Justificacion
import com.example.ecolacteoshuata.domain.model.MiAsistencia
import com.example.ecolacteoshuata.domain.repository.AsistenciaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import com.example.ecolacteoshuata.db.Asistencia as AsistenciaEntity
import com.example.ecolacteoshuata.db.Horario_asistencia as HorarioEntity

private fun HorarioEntity.toDomain() = HorarioAsistencia(
    id = id,
    nombre = nombre,
    horaInicio = hora_inicio,
    limitePresente = hora_limite_presente,
    limiteTardanza = hora_limite_tardanza,
    activo = activo == 1L,
)

private fun AsistenciaEntity.toDomain(): Asistencia? = Asistencia(
    id = id,
    usuarioId = usuario_id,
    fecha = LocalDate.parse(fecha),
    horarioId = horario_asistencia_id,
    estado = EstadoAsistencia.desde(estado) ?: return null,
    horaMarcada = hora_marcada,
)

class AsistenciaRepositoryImpl(private val db: AppDatabase) : AsistenciaRepository {
    private val queries = db.asistenciaQueries

    override fun observeHorarios(): Flow<List<HorarioAsistencia>> =
        queries.selectHorarios().asFlow().mapToList(Dispatchers.Default).map { lista -> lista.map { it.toDomain() } }

    override fun observeDiasAcopiadorEntre(desde: LocalDate, hasta: LocalDate): Flow<Set<LocalDate>> =
        queries.selectDiasEntre(desde.toString(), hasta.toString()).asFlow().mapToList(Dispatchers.Default)
            .map { lista -> lista.map { LocalDate.parse(it.fecha) }.toSet() }

    override fun observeAsistenciasEntre(desde: LocalDate, hasta: LocalDate): Flow<List<Asistencia>> =
        queries.selectAsistenciasEntre(desde.toString(), hasta.toString()).asFlow().mapToList(Dispatchers.Default)
            .map { lista -> lista.mapNotNull { it.toDomain() } }

    override fun observeJustificaciones(): Flow<List<Justificacion>> =
        queries.selectJustificaciones().asFlow().mapToList(Dispatchers.Default).map { lista ->
            lista.mapNotNull { fila ->
                Justificacion(
                    id = fila.id,
                    asistenciaId = fila.asistencia_id,
                    usuarioId = fila.usuario_id,
                    fecha = LocalDate.parse(fila.fecha),
                    horarioId = fila.horario_asistencia_id,
                    estadoAsistencia = EstadoAsistencia.desde(fila.estado_asistencia) ?: return@mapNotNull null,
                    motivo = fila.motivo,
                    tieneArchivo = fila.tiene_archivo == 1L,
                    estado = EstadoJustificacion.desde(fila.estado),
                    enviadaEn = fila.enviada_en?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
                )
            }
        }

    override suspend fun guardarAsistenciaGeneral(fecha: LocalDate, estados: Map<String, EstadoAsistencia>, registradoPor: String) {
        db.transaction {
            estados.forEach { (usuarioId, estado) -> guardarMarca(usuarioId, fecha, null, estado, registradoPor) }
        }
    }

    override suspend fun guardarMarcasAcopiador(
        usuarioId: String,
        fecha: LocalDate,
        estados: Map<String, EstadoAsistencia?>,
        registradoPor: String,
    ) {
        db.transaction {
            estados.forEach { (horarioId, estado) ->
                if (estado == null) {
                    queries.selectMarca(usuarioId, fecha.toString(), horarioId).executeAsOneOrNull()
                        ?.let { queries.deleteAsistencia(it.id) }
                } else {
                    guardarMarca(usuarioId, fecha, horarioId, estado, registradoPor)
                }
            }
        }
    }

    /** Una sola marca por trabajador, día y horario; solo se toca si cambió (así no se resube). */
    private fun guardarMarca(usuarioId: String, fecha: LocalDate, horarioId: String?, estado: EstadoAsistencia, registradoPor: String) {
        val existente = queries.selectMarca(usuarioId, fecha.toString(), horarioId.orEmpty()).executeAsOneOrNull()
        when {
            existente == null -> queries.insertAsistencia(
                IdGenerator.newId(), usuarioId, fecha.toString(), horarioId, estado.name, null, registradoPor,
            )
            existente.estado != estado.name -> queries.updateEstadoAsistencia(estado.name, registradoPor, existente.id)
        }
    }

    override suspend fun alternarDiaAcopiador(fecha: LocalDate) {
        val existente = queries.selectDiaPorFecha(fecha.toString()).executeAsOneOrNull()
        if (existente != null) queries.deleteDia(existente.id) else queries.insertDia(IdGenerator.newId(), fecha.toString())
    }

    override suspend fun reemplazarDiasAcopiador(desde: LocalDate, hasta: LocalDate, fechas: List<LocalDate>) {
        db.transaction {
            val actuales = queries.selectDiasEntre(desde.toString(), hasta.toString()).executeAsList()
            val nuevas = fechas.map { it.toString() }.toSet()
            // Solo se agregan o quitan los días que cambian: los que se quedan no se resuben.
            actuales.filter { it.fecha !in nuevas }.forEach { queries.deleteDia(it.id) }
            val yaEstan = actuales.map { it.fecha }.toSet()
            nuevas.filter { it !in yaEstan }.forEach { queries.insertDia(IdGenerator.newId(), it) }
        }
    }

    override suspend fun guardarHorario(horario: HorarioAsistencia) {
        val activo = if (horario.activo) 1L else 0L
        if (horario.id.isBlank()) {
            queries.insertHorario(IdGenerator.newId(), horario.nombre, horario.horaInicio, horario.limitePresente, horario.limiteTardanza, activo)
        } else {
            queries.updateHorario(horario.nombre, horario.horaInicio, horario.limitePresente, horario.limiteTardanza, activo, horario.id)
        }
    }

    override suspend fun eliminarHorario(id: String) {
        db.transaction {
            queries.deleteAsistenciasDeHorario(id)
            queries.deleteHorario(id)
        }
    }

    override fun observeMisAsistenciasEntre(usuarioId: String, desde: LocalDate, hasta: LocalDate): Flow<List<MiAsistencia>> =
        queries.selectMisAsistenciasEntre(usuarioId, desde.toString(), hasta.toString()).asFlow().mapToList(Dispatchers.Default)
            .map { lista ->
                lista.mapNotNull { fila ->
                    MiAsistencia(
                        asistencia = Asistencia(
                            id = fila.id,
                            usuarioId = fila.usuario_id,
                            fecha = LocalDate.parse(fila.fecha),
                            horarioId = fila.horario_asistencia_id,
                            estado = EstadoAsistencia.desde(fila.estado) ?: return@mapNotNull null,
                            horaMarcada = fila.hora_marcada,
                        ),
                        justificacionId = fila.justificacion_id,
                        justificacionEstado = fila.justificacion_estado?.let { EstadoJustificacion.desde(it) },
                    )
                }
            }

    override suspend fun marcarAsistenciaPropia(usuarioId: String, horarioId: String, fecha: LocalDate, horaMarcada: String, estado: EstadoAsistencia) {
        if (queries.selectMarca(usuarioId, fecha.toString(), horarioId).executeAsOneOrNull() != null) return
        queries.insertAsistencia(IdGenerator.newId(), usuarioId, fecha.toString(), horarioId, estado.name, horaMarcada, null)
    }

    override suspend fun justificar(asistenciaId: String, motivo: String, hoy: LocalDate) {
        val anterior = queries.selectJustificacionPorAsistencia(asistenciaId).executeAsOneOrNull()
        if (anterior != null) {
            queries.reenviarJustificacion(motivo, hoy.toString(), anterior.id)
        } else {
            queries.insertJustificacion(IdGenerator.newId(), asistenciaId, motivo, hoy.toString())
        }
    }

    override suspend fun revisarJustificacion(id: String, estado: EstadoJustificacion) {
        queries.updateEstadoJustificacion(estado.name, id)
    }
}
