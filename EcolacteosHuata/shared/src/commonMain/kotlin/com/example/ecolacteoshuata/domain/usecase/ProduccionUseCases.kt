package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.domain.model.Compra
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.model.InventarioInsumoItem
import com.example.ecolacteoshuata.domain.model.ProduccionRegistro
import com.example.ecolacteoshuata.domain.model.Receta
import com.example.ecolacteoshuata.domain.model.TurnoProduccion
import com.example.ecolacteoshuata.domain.model.UnidadMedida
import com.example.ecolacteoshuata.domain.repository.CompraRepository
import com.example.ecolacteoshuata.domain.repository.InsumoRepository
import com.example.ecolacteoshuata.domain.repository.ProduccionRepository
import com.example.ecolacteoshuata.domain.repository.RecetaRepository
import com.example.ecolacteoshuata.domain.repository.TurnoProduccionRepository
import com.example.ecolacteoshuata.domain.repository.UnidadMedidaRepository
import kotlinx.coroutines.flow.Flow

// Unidades de medida
class ObserveUnidadesMedidaUseCase(private val repository: UnidadMedidaRepository) {
    operator fun invoke(): Flow<List<UnidadMedida>> = repository.observeAll()
}

class GuardarUnidadMedidaUseCase(private val repository: UnidadMedidaRepository) {
    suspend operator fun invoke(unidad: UnidadMedida): AppResult<Unit> {
        if (!Validators.noVacio(unidad.nombre) || !Validators.noVacio(unidad.simbolo)) {
            return AppResult.Error("Ingrese nombre y símbolo de la unidad.")
        }
        if (unidad.id.isBlank()) repository.crear(unidad.copy(id = IdGenerator.newId())) else repository.actualizar(unidad)
        return AppResult.Success(Unit)
    }
}

class EliminarUnidadMedidaUseCase(
    private val repository: UnidadMedidaRepository,
    private val insumoRepository: InsumoRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> {
        val enUso = insumoRepository.getAll().count { it.unidadMedidaId == id }
        if (enUso > 0) return AppResult.Error("No se puede eliminar: la usa(n) $enUso insumo(s).")
        repository.eliminar(id)
        return AppResult.Success(Unit)
    }
}

// Insumos
class ObserveInsumosUseCase(private val repository: InsumoRepository) {
    operator fun invoke(): Flow<List<Insumo>> = repository.observeAll()
}

class GuardarInsumoUseCase(private val repository: InsumoRepository) {
    suspend operator fun invoke(insumo: Insumo): AppResult<Unit> {
        if (!Validators.noVacio(insumo.nombre)) return AppResult.Error("Ingrese el nombre del insumo.")
        if (insumo.unidadMedidaId.isBlank()) return AppResult.Error("Seleccione una unidad de medida.")
        if (insumo.costoUnitario < 0) return AppResult.Error("El costo unitario no puede ser negativo.")
        if (insumo.id.isBlank()) repository.crear(insumo.copy(id = IdGenerator.newId())) else repository.actualizar(insumo)
        return AppResult.Success(Unit)
    }
}

class EliminarInsumoUseCase(private val repository: InsumoRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}

// Recetas
class ObserveRecetasUseCase(private val repository: RecetaRepository) {
    operator fun invoke(): Flow<List<Receta>> = repository.observeAll()
}

class GuardarRecetaUseCase(private val repository: RecetaRepository) {
    suspend operator fun invoke(receta: Receta): AppResult<Unit> {
        if (!Validators.noVacio(receta.nombre)) return AppResult.Error("Ingrese el nombre de la receta.")
        if (receta.detalle.isEmpty()) return AppResult.Error("Agregue al menos un insumo a la receta.")
        if (receta.id.isBlank()) repository.crear(receta.copy(id = IdGenerator.newId())) else repository.actualizar(receta)
        return AppResult.Success(Unit)
    }
}

class EliminarRecetaUseCase(private val repository: RecetaRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}

// Compras
class ObserveComprasUseCase(private val repository: CompraRepository) {
    operator fun invoke(): Flow<List<Compra>> = repository.observeAll()
}

class RegistrarCompraUseCase(private val repository: CompraRepository) {
    suspend operator fun invoke(compra: Compra): AppResult<Compra> {
        if (compra.detalle.isEmpty()) return AppResult.Error("Agregue al menos un insumo a la compra.")
        repository.crear(compra)
        return AppResult.Success(compra)
    }
}

// Producción (registros)
class ObserveProduccionUseCase(private val repository: ProduccionRepository) {
    operator fun invoke(): Flow<List<ProduccionRegistro>> = repository.observeAll()
}

class RegistrarProduccionUseCase(private val repository: ProduccionRepository) {
    suspend operator fun invoke(registro: ProduccionRegistro): AppResult<ProduccionRegistro> {
        if (registro.recetaId.isBlank()) return AppResult.Error("Seleccione una receta.")
        if (registro.cantidadLotes <= 0) return AppResult.Error("La cantidad debe ser mayor a 0.")
        repository.crear(registro)
        return AppResult.Success(registro)
    }
}

/** Editar o "Actualizar producción": guarda los cambios y la deja otra vez planificada. */
class ActualizarProduccionUseCase(private val repository: ProduccionRepository) {
    suspend operator fun invoke(registro: ProduccionRegistro): AppResult<Unit> {
        if (registro.recetaId.isBlank()) return AppResult.Error("Seleccione una receta.")
        if (registro.cantidadLotes <= 0) return AppResult.Error("La cantidad debe ser mayor a 0.")
        repository.actualizar(registro)
        return AppResult.Success(Unit)
    }
}

class PublicarProduccionUseCase(private val repository: ProduccionRepository) {
    suspend operator fun invoke(id: String) = repository.publicar(id)
}

/** Elimina la producción y devuelve al stock los insumos que consumía; no si ya tiene ventas. */
class EliminarProduccionUseCase(private val repository: ProduccionRepository) {
    suspend operator fun invoke(id: String): AppResult<Unit> {
        val produccion = repository.getById(id) ?: return AppResult.Success(Unit)
        if (produccion.cantidadVendida > 0) return AppResult.Error("No se puede eliminar: esta producción ya tiene ventas registradas.")
        repository.eliminar(id)
        return AppResult.Success(Unit)
    }
}

// Turnos de producción
class ObserveTurnosProduccionUseCase(private val repository: TurnoProduccionRepository) {
    operator fun invoke(): Flow<List<TurnoProduccion>> = repository.observeAll()
}

class AgregarTurnoProduccionUseCase(private val repository: TurnoProduccionRepository) {
    suspend operator fun invoke(turno: TurnoProduccion): AppResult<Unit> {
        if (!Validators.noVacio(turno.nombre) || !Validators.noVacio(turno.horaInicio) || !Validators.noVacio(turno.horaFin)) {
            return AppResult.Error("Complete el nombre y los horarios del turno.")
        }
        repository.crear(turno.copy(id = IdGenerator.newId()))
        return AppResult.Success(Unit)
    }
}

class EliminarTurnoProduccionUseCase(private val repository: TurnoProduccionRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}

// Inventario de insumos: el stock es el real (lo suben las compras y lo bajan las producciones, como
// en Laravel); comprado y consumido se muestran como referencia.
class GetInventarioInsumosUseCase(
    private val insumoRepository: InsumoRepository,
    private val compraRepository: CompraRepository,
    private val recetaRepository: RecetaRepository,
    private val produccionRepository: ProduccionRepository,
) {
    suspend operator fun invoke(): List<InventarioInsumoItem> {
        val insumos = insumoRepository.getAll()
        val comprasPorInsumo = compraRepository.getAll()
            .flatMap { it.detalle }
            .groupBy { it.insumoId }
            .mapValues { (_, detalles) -> detalles.sumOf { it.cantidad } }

        val recetas = recetaRepository.getAll().associateBy { it.id }
        val consumoPorInsumo = mutableMapOf<String, Double>()
        produccionRepository.getAll().forEach { registro ->
            val receta = recetas[registro.recetaId] ?: return@forEach
            receta.detalle.forEach { item ->
                consumoPorInsumo[item.insumoId] = (consumoPorInsumo[item.insumoId] ?: 0.0) + item.cantidad * registro.cantidadLotes
            }
        }

        return insumos.map { insumo ->
            val comprado = comprasPorInsumo[insumo.id] ?: 0.0
            val consumido = consumoPorInsumo[insumo.id] ?: 0.0
            InventarioInsumoItem(insumo = insumo, comprado = comprado, consumido = consumido, stock = insumo.stockActual)
        }
    }
}
