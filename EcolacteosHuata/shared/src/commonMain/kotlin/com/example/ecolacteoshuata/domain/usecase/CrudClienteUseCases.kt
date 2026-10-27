package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.domain.model.Cliente
import com.example.ecolacteoshuata.domain.repository.ClienteRepository
import kotlinx.coroutines.flow.Flow

class ObserveClientesUseCase(private val repository: ClienteRepository) {
    operator fun invoke(): Flow<List<Cliente>> = repository.observeAll()
}

class GuardarClienteUseCase(private val repository: ClienteRepository) {
    suspend operator fun invoke(cliente: Cliente): AppResult<Unit> {
        if (!Validators.noVacio(cliente.nombres)) return AppResult.Error("Ingrese el nombre del cliente.")
        if (!Validators.esDocumentoValido(cliente.documento)) return AppResult.Error("El documento debe tener entre 8 y 11 dígitos.")
        // Como en Laravel (`unique:clientes,documento`): un DNI/RUC por cliente.
        if (repository.getAll().any { it.documento == cliente.documento.trim() && it.id != cliente.id }) {
            return AppResult.Error("Ya existe un cliente con el DNI/RUC ${cliente.documento.trim()}.")
        }
        if (cliente.id.isBlank()) {
            repository.crear(cliente.copy(id = IdGenerator.newId()))
        } else {
            repository.actualizar(cliente)
        }
        return AppResult.Success(Unit)
    }
}

class EliminarClienteUseCase(private val repository: ClienteRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}

class SetDescuentoClienteUseCase(private val repository: ClienteRepository) {
    suspend operator fun invoke(
        clienteId: String,
        favorito: Boolean,
        descuentoFavorito: Double,
        mayorista: Boolean,
        descuentoMayorista: Double,
    ): AppResult<Unit> {
        if (favorito && (descuentoFavorito <= 0 || descuentoFavorito > 100)) {
            return AppResult.Error("El descuento de cliente favorito debe estar entre 1% y 100%.")
        }
        if (mayorista && (descuentoMayorista <= 0 || descuentoMayorista > 100)) {
            return AppResult.Error("El descuento mayorista debe estar entre 1% y 100%.")
        }
        repository.setDescuentos(clienteId, favorito, descuentoFavorito, mayorista, descuentoMayorista)
        return AppResult.Success(Unit)
    }
}
