package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Compra
import com.example.ecolacteoshuata.domain.model.Insumo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UnidadMedidaRepositoryImpl(private val db: AppDatabase) : UnidadMedidaRepository {
    private val queries = db.unidadMedidaQueries

    override fun observeAll(): Flow<List<UnidadMedida>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<UnidadMedida> = queries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun crear(unidad: UnidadMedida) {
        queries.insert(id = unidad.id.ifBlank { IdGenerator.newId() }, nombre = unidad.nombre, simbolo = unidad.simbolo)
    }

    override suspend fun actualizar(unidad: UnidadMedida) {
        queries.update(nombre = unidad.nombre, simbolo = unidad.simbolo, id = unidad.id)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}

class InsumoRepositoryImpl(private val db: AppDatabase) : InsumoRepository {
    private val queries = db.insumoQueries

    private fun com.example.ecolacteoshuata.db.Insumo.mapConUnidad(): Insumo {
        val simbolo = db.unidadMedidaQueries.selectById(unidad_medida_id).executeAsOneOrNull()?.simbolo ?: ""
        return toDomain(simbolo)
    }

    override fun observeAll(): Flow<List<Insumo>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.mapConUnidad() } }

    override suspend fun getAll(): List<Insumo> = queries.selectAll().executeAsList().map { it.mapConUnidad() }

    override suspend fun getById(id: String): Insumo? = queries.selectById(id).executeAsOneOrNull()?.mapConUnidad()

    override suspend fun crear(insumo: Insumo) {
        queries.insert(
            id = insumo.id.ifBlank { IdGenerator.newId() },
            nombre = insumo.nombre,
            unidad_medida_id = insumo.unidadMedidaId,
            stock_minimo = insumo.stockMinimo,
            costo_unitario = insumo.costoUnitario,
            stock_actual = insumo.stockActual,
            stock_inicial = insumo.stockActual,
        )
    }

    override suspend fun actualizar(insumo: Insumo) {
        val actual = queries.selectById(insumo.id).executeAsOneOrNull()
        // Si se corrigió el stock a mano, Laravel lo toma al subirlo (como al editar el insumo en la web).
        val corregido = actual != null && (actual.stock_editado == 1L || actual.stock_actual != insumo.stockActual)
        queries.update(
            nombre = insumo.nombre,
            unidad_medida_id = insumo.unidadMedidaId,
            stock_minimo = insumo.stockMinimo,
            costo_unitario = insumo.costoUnitario,
            stock_actual = insumo.stockActual,
            stock_editado = if (corregido) 1L else 0L,
            id = insumo.id,
        )
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}

class RecetaRepositoryImpl(private val db: AppDatabase) : RecetaRepository {
    private val queries = db.recetaQueries

    private fun com.example.ecolacteoshuata.db.Receta.mapConDetalle(): Receta {
        val detalle = queries.selectDetalleByReceta(id).executeAsList().map {
            val nombre = db.insumoQueries.selectById(it.insumo_id).executeAsOneOrNull()?.nombre ?: ""
            it.toDomain(nombre)
        }
        return toDomain(detalle)
    }

    override fun observeAll(): Flow<List<Receta>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.mapConDetalle() } }

    override suspend fun getAll(): List<Receta> = queries.selectAll().executeAsList().map { it.mapConDetalle() }

    override suspend fun getById(id: String): Receta? = queries.selectById(id).executeAsOneOrNull()?.mapConDetalle()

    override suspend fun crear(receta: Receta) {
        val recetaId = receta.id.ifBlank { IdGenerator.newId() }
        queries.transaction {
            queries.insert(id = recetaId, nombre = receta.nombre, descripcion = receta.descripcion, producto_id = receta.productoId)
            receta.detalle.forEach {
                queries.insertDetalle(id = IdGenerator.newId(), receta_id = recetaId, insumo_id = it.insumoId, cantidad = it.cantidad)
            }
        }
    }

    override suspend fun actualizar(receta: Receta) {
        queries.transaction {
            queries.update(nombre = receta.nombre, descripcion = receta.descripcion, producto_id = receta.productoId, id = receta.id)
            queries.deleteDetalleByReceta(receta.id)
            receta.detalle.forEach {
                queries.insertDetalle(id = IdGenerator.newId(), receta_id = receta.id, insumo_id = it.insumoId, cantidad = it.cantidad)
            }
        }
    }

    override suspend fun eliminar(id: String) {
        queries.deleteDetalleByReceta(id)
        queries.deleteById(id)
    }
}

class CompraRepositoryImpl(private val db: AppDatabase) : CompraRepository {
    private val queries = db.compraQueries

    private fun com.example.ecolacteoshuata.db.Compra.mapConDetalle(): Compra {
        val detalle = queries.selectDetalleByCompra(id).executeAsList().map {
            val nombre = db.insumoQueries.selectById(it.insumo_id).executeAsOneOrNull()?.nombre ?: ""
            it.toDomain(nombre)
        }
        return toDomain(detalle)
    }

    override fun observeAll(): Flow<List<Compra>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.mapConDetalle() } }

    override suspend fun getAll(): List<Compra> = queries.selectAll().executeAsList().map { it.mapConDetalle() }

    override suspend fun crear(compra: Compra) {
        val compraId = compra.id.ifBlank { IdGenerator.newId() }
        queries.transaction {
            queries.insert(
                id = compraId,
                fecha = compra.fecha.toString(),
                proveedor_insumo = compra.proveedorInsumo,
                descripcion = compra.descripcion,
                total = compra.total,
            )
            compra.detalle.forEach {
                queries.insertDetalle(
                    id = IdGenerator.newId(),
                    compra_id = compraId,
                    insumo_id = it.insumoId,
                    cantidad = it.cantidad,
                    costo_unitario = it.costoUnitario,
                    subtotal = it.subtotal,
                )
                registrarCompra(db, it.insumoId, it.cantidad, it.costoUnitario)
            }
        }
    }
}

class ProduccionRepositoryImpl(private val db: AppDatabase) : ProduccionRepository {
    private val queries = db.produccionQueries

    private fun com.example.ecolacteoshuata.db.Produccion.mapConReceta(): ProduccionRegistro {
        val recetaNombre = db.recetaQueries.selectById(receta_id).executeAsOneOrNull()?.nombre ?: ""
        return toDomain(recetaNombre)
    }

    override fun observeAll(): Flow<List<ProduccionRegistro>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.mapConReceta() } }

    override suspend fun getAll(): List<ProduccionRegistro> = queries.selectAll().executeAsList().map { it.mapConReceta() }

    override suspend fun getById(id: String): ProduccionRegistro? = queries.selectById(id).executeAsOneOrNull()?.mapConReceta()

    private fun nombreReceta(recetaId: String) = db.recetaQueries.selectById(recetaId).executeAsOneOrNull()?.nombre.orEmpty()

    /** Como `consumirInsumos()` de Laravel: descuenta del stock lo que usa la receta (o lo devuelve si [signo] es -1). */
    private fun consumir(recetaId: String, cantidad: Double, signo: Int = 1) {
        db.recetaQueries.selectDetalleByReceta(recetaId).executeAsList().forEach { detalle ->
            val insumo = db.insumoQueries.selectById(detalle.insumo_id).executeAsOneOrNull() ?: return@forEach
            val stock = (insumo.stock_actual - signo * detalle.cantidad * cantidad).coerceAtLeast(0.0)
            db.insumoQueries.actualizarStockYCosto(stock, insumo.costo_unitario, insumo.id)
        }
    }

    override suspend fun crear(registro: ProduccionRegistro) {
        val id = registro.id.ifBlank { IdGenerator.newId() }
        // Mismo formato de lote que Laravel: L-AAAAMMDDhhmmss-NNN.
        val lote = registro.lote.ifBlank {
            "L-" + nowLocalDateTime().toString().filter { it.isDigit() }.take(14) + "-" + (100..999).random()
        }
        queries.transaction {
            queries.insert(
                id = id,
                fecha = registro.fecha.toString(),
                receta_id = registro.recetaId,
                producto_id = registro.productoId,
                cantidad_lotes = registro.cantidadLotes,
                cantidad_producida = registro.cantidadProducida,
                observaciones = registro.observaciones,
                turno_id = registro.turnoId,
                nombre = registro.nombre.ifBlank { nombreReceta(registro.recetaId) },
                lote = lote,
                responsable_id = registro.responsableId,
            )
            consumir(registro.recetaId, registro.cantidadProducida)
        }
    }

    override suspend fun actualizar(registro: ProduccionRegistro) {
        val anterior = queries.selectById(registro.id).executeAsOneOrNull() ?: return
        queries.transaction {
            // Devuelve lo que consumía y descuenta lo nuevo; Laravel recalcula su stock al recibirlo.
            consumir(anterior.receta_id, anterior.cantidad_producida, signo = -1)
            queries.update(
                receta_id = registro.recetaId,
                producto_id = registro.productoId,
                cantidad_lotes = registro.cantidadLotes,
                cantidad_producida = registro.cantidadProducida,
                turno_id = registro.turnoId,
                nombre = anterior.nombre.takeIf { registro.recetaId == anterior.receta_id && it.isNotBlank() }
                    ?: nombreReceta(registro.recetaId),
                id = registro.id,
            )
            consumir(registro.recetaId, registro.cantidadProducida)
        }
    }

    override suspend fun publicar(id: String) {
        queries.publicar(id)
    }

    /** Como `revertirConsumo()` de Laravel: el stock de insumos que usaba vuelve al inventario. */
    override suspend fun eliminar(id: String) {
        val produccion = queries.selectById(id).executeAsOneOrNull() ?: return
        queries.transaction {
            consumir(produccion.receta_id, produccion.cantidad_producida, signo = -1)
            queries.deleteById(id)
        }
    }
}

class TurnoProduccionRepositoryImpl(private val db: AppDatabase) : TurnoProduccionRepository {
    private val queries = db.turnoProduccionQueries

    override fun observeAll(): Flow<List<TurnoProduccion>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    override suspend fun crear(turno: TurnoProduccion) {
        queries.insert(id = turno.id.ifBlank { IdGenerator.newId() }, nombre = turno.nombre, hora_inicio = turno.horaInicio, hora_fin = turno.horaFin)
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}

/**
 * Suma una compra al stock del insumo y recalcula el costo unitario como promedio ponderado,
 * igual que `Insumo::registrarCompra()` en Laravel.
 */
internal fun registrarCompra(db: AppDatabase, insumoId: String, cantidad: Double, costoUnitario: Double) {
    val insumo = db.insumoQueries.selectById(insumoId).executeAsOneOrNull() ?: return
    val stockPrevio = insumo.stock_actual.coerceAtLeast(0.0)
    val nuevoStock = stockPrevio + cantidad
    val nuevoCosto = if (nuevoStock > 0) (stockPrevio * insumo.costo_unitario + cantidad * costoUnitario) / nuevoStock else costoUnitario
    db.insumoQueries.actualizarStockYCosto(nuevoStock, nuevoCosto, insumoId)
}
