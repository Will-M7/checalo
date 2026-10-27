package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.domain.model.Producto
import com.example.ecolacteoshuata.domain.repository.ProductoRepository
import kotlinx.coroutines.flow.Flow

/** Producciones publicadas listas para vender (en Laravel no hay catálogo de productos). */
class ObserveProductosUseCase(private val repository: ProductoRepository) {
    operator fun invoke(soloActivos: Boolean = false): Flow<List<Producto>> =
        if (soloActivos) repository.observeActivos() else repository.observeAll()
}
