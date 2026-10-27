package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Comunicado
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.TipoComunicado
import com.example.ecolacteoshuata.domain.repository.ComunicadoRepository
import kotlinx.coroutines.flow.Flow

class ObserveComunicadosUseCase(private val repository: ComunicadoRepository) {
    operator fun invoke(): Flow<List<Comunicado>> = repository.observeAll()
}

class PublicarComunicadoUseCase(private val repository: ComunicadoRepository) {
    /** [roles] `null` = para todos los roles, como la casilla "Todos los roles" de Laravel. */
    suspend operator fun invoke(
        tipo: TipoComunicado,
        titulo: String,
        descripcion: String,
        autorId: String,
        roles: Set<Rol>? = null,
    ): AppResult<Unit> {
        if (!Validators.noVacio(titulo) || !Validators.noVacio(descripcion)) {
            return AppResult.Error("Complete el título y la descripción.")
        }
        if (roles != null && roles.isEmpty()) {
            return AppResult.Error("Selecciona al menos un rol, o marca \"Todos los roles\".")
        }
        repository.crear(
            Comunicado(
                id = IdGenerator.newId(),
                tipo = tipo,
                titulo = titulo,
                descripcion = descripcion,
                fecha = todayLocalDate(),
                autorId = autorId,
                roles = roles,
            )
        )
        return AppResult.Success(Unit)
    }
}

class EliminarComunicadoUseCase(private val repository: ComunicadoRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}
