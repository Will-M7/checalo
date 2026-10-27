package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Asistencia
import com.example.ecolacteoshuata.domain.model.EstadoAsistencia
import com.example.ecolacteoshuata.domain.model.EstadoJustificacion
import com.example.ecolacteoshuata.domain.model.HorarioAsistencia
import com.example.ecolacteoshuata.domain.model.Justificacion
import com.example.ecolacteoshuata.domain.model.MiAsistencia
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** "Tomar asistencia" (tablas de asistencia que se sincronizan con Laravel). */
interface AsistenciaRepository {
    fun observeHorarios(): Flow<List<HorarioAsistencia>>
    fun observeDiasAcopiadorEntre(desde: LocalDate, hasta: LocalDate): Flow<Set<LocalDate>>
    fun observeAsistenciasEntre(desde: LocalDate, hasta: LocalDate): Flow<List<Asistencia>>
    fun observeJustificaciones(): Flow<List<Justificacion>>

    /** Asistencia general del día: un estado por trabajador (crea o actualiza su registro). */
    suspend fun guardarAsistenciaGeneral(fecha: LocalDate, estados: Map<String, EstadoAsistencia>, registradoPor: String)

    /** Marcas de un acopiador en el día, por horario; `null` quita la marca de ese horario. */
    suspend fun guardarMarcasAcopiador(usuarioId: String, fecha: LocalDate, estados: Map<String, EstadoAsistencia?>, registradoPor: String)

    /** Agrega o quita un día de asistencia de los acopiadores. */
    suspend fun alternarDiaAcopiador(fecha: LocalDate)

    /** Deja como días de asistencia entre [desde] y [hasta] solo los de [fechas]. */
    suspend fun reemplazarDiasAcopiador(desde: LocalDate, hasta: LocalDate, fechas: List<LocalDate>)

    /** Crea (id en blanco) o actualiza un horario. */
    suspend fun guardarHorario(horario: HorarioAsistencia)

    /** Elimina el horario junto con las marcas de los acopiadores en ese horario. */
    suspend fun eliminarHorario(id: String)

    /** Asistencias de un trabajador entre dos fechas, con su justificación. */
    fun observeMisAsistenciasEntre(usuarioId: String, desde: LocalDate, hasta: LocalDate): Flow<List<MiAsistencia>>

    /** El acopiador marca "Asistí" en un horario abierto (si ya marcó ese horario hoy, no hace nada). */
    suspend fun marcarAsistenciaPropia(usuarioId: String, horarioId: String, fecha: LocalDate, horaMarcada: String, estado: EstadoAsistencia)

    /** Envía (o reenvía, si fue rechazada) la justificación de una falta o tardanza propia. */
    suspend fun justificar(asistenciaId: String, motivo: String, hoy: LocalDate)

    suspend fun revisarJustificacion(id: String, estado: EstadoJustificacion)
}
