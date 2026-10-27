package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import kotlinx.coroutines.flow.Flow

class ObserveRutasUseCase(private val repository: RutaRepository) {
    operator fun invoke(acopiadorId: String): Flow<List<Ruta>> = repository.observeByAcopiador(acopiadorId)
}

class GuardarRutaUseCase(private val repository: RutaRepository) {
    suspend operator fun invoke(ruta: Ruta): AppResult<Unit> {
        if (!Validators.noVacio(ruta.nombre)) return AppResult.Error("Ingrese el nombre de la ruta.")
        if (ruta.acopiadorId.isBlank()) return AppResult.Error("Seleccione el acopiador de la ruta.")
        if (ruta.id.isBlank()) {
            repository.crear(ruta.copy(id = IdGenerator.newId()))
        } else {
            repository.actualizar(ruta)
        }
        return AppResult.Success(Unit)
    }
}

class EliminarRutaUseCase(private val repository: RutaRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}

class AsignarProveedoresARutaUseCase(private val repository: RutaRepository) {
    suspend operator fun invoke(rutaId: String, proveedorIds: List<String>) =
        repository.asignarProveedores(rutaId, proveedorIds)
}

/**
 * Agrega un proveedor al final de la ruta, como Ruta::agregarProveedor de Laravel: sale de cualquier
 * otra ruta y pasa a pertenecer al acopiador de esta (para que lo vea en Recojo/Entrega).
 */
class AgregarProveedorARutaUseCase(
    private val repository: RutaRepository,
    private val proveedorRepository: ProveedorRepository,
) {
    suspend operator fun invoke(rutaId: String, proveedorId: String) {
        val ruta = repository.getById(rutaId) ?: return
        repository.getAll()
            .filter { it.id != rutaId && proveedorId in it.proveedorIds }
            .forEach { otra -> repository.asignarProveedores(otra.id, otra.proveedorIds - proveedorId) }
        if (proveedorId !in ruta.proveedorIds) repository.asignarProveedores(rutaId, ruta.proveedorIds + proveedorId)
        proveedorRepository.asignarAcopiador(proveedorId, ruta.acopiadorId)
    }
}

class QuitarProveedorDeRutaUseCase(private val repository: RutaRepository) {
    suspend operator fun invoke(rutaId: String, proveedorId: String) {
        val ruta = repository.getById(rutaId) ?: return
        repository.asignarProveedores(rutaId, ruta.proveedorIds - proveedorId)
    }
}

class ObserveTodosLosTurnosUseCase(private val repository: TurnoRepository) {
    operator fun invoke(): Flow<List<Turno>> = repository.observeAll()
}

class ObserveTurnosUseCase(private val repository: TurnoRepository) {
    operator fun invoke(acopiadorId: String): Flow<List<Turno>> = repository.observeByAcopiador(acopiadorId)
}

class AgregarTurnoUseCase(private val repository: TurnoRepository) {
    suspend operator fun invoke(turno: Turno): AppResult<Unit> {
        if (!Validators.noVacio(turno.nombre) || !Validators.noVacio(turno.horaInicio) || !Validators.noVacio(turno.horaFin)) {
            return AppResult.Error("Complete el nombre y los horarios del turno.")
        }
        repository.crear(turno.copy(id = IdGenerator.newId()))
        return AppResult.Success(Unit)
    }
}

class ActualizarTurnoUseCase(private val repository: TurnoRepository) {
    suspend operator fun invoke(turno: Turno): AppResult<Unit> {
        if (!Validators.noVacio(turno.nombre) || !Validators.noVacio(turno.horaInicio) || !Validators.noVacio(turno.horaFin)) {
            return AppResult.Error("Complete el nombre y los horarios del turno.")
        }
        repository.actualizar(turno)
        return AppResult.Success(Unit)
    }
}

class EliminarTurnoUseCase(private val repository: TurnoRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}
