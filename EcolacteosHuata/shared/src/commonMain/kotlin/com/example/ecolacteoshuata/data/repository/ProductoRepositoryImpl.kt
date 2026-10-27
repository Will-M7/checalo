package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ecolacteoshuata.core.util.redondear2
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Producto
import com.example.ecolacteoshuata.domain.repository.ProductoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.example.ecolacteoshuata.db.Produccion as ProduccionEntity

class ProductoRepositoryImpl(private val db: AppDatabase) : ProductoRepository {
    private val queries = db.produccionQueries

    /** Costo de la receta por unidad, como `Receta::costoTotal()` en Laravel. */
    private fun precioDe(recetaId: String): Double = redondear2(
        db.recetaQueries.selectDetalleByReceta(recetaId).executeAsList().sumOf { detalle ->
            detalle.cantidad * (db.insumoQueries.selectById(detalle.insumo_id).executeAsOneOrNull()?.costo_unitario ?: 0.0)
        },
    )

    private fun ProduccionEntity.toProducto(): Producto {
        val disponible = (cantidad_producida - cantidad_vendida).coerceAtLeast(0.0)
        val receta = db.recetaQueries.selectById(receta_id).executeAsOneOrNull()?.nombre.orEmpty()
        return Producto(
            id = id,
            nombre = nombre.ifBlank { receta },
            descripcion = listOf(lote, fecha).filter { it.isNotBlank() }.joinToString(" · "),
            precio = precioDe(receta_id),
            activo = disponible > 0,
            disponible = disponible,
        )
    }

    // También cambia el precio si cambia la receta o el costo de un insumo.
    override fun observeAll(): Flow<List<Producto>> = combine(
        queries.selectCompletadas().asFlow().mapToList(Dispatchers.Default),
        db.recetaQueries.selectDetalleAll().asFlow().mapToList(Dispatchers.Default),
        db.insumoQueries.selectAll().asFlow().mapToList(Dispatchers.Default),
    ) { producciones, _, _ -> producciones.map { it.toProducto() } }

    override fun observeActivos(): Flow<List<Producto>> = observeAll().map { lista -> lista.filter { it.activo } }

    override suspend fun getAll(): List<Producto> = queries.selectCompletadas().executeAsList().map { it.toProducto() }

    override suspend fun getActivos(): List<Producto> = getAll().filter { it.activo }

    override suspend fun getById(id: String): Producto? = queries.selectById(id).executeAsOneOrNull()?.toProducto()

    override suspend fun countActivos(): Long = getActivos().size.toLong()
}
