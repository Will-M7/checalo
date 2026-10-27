package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.domain.model.PrecioLeche
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

class ObservePrecioLecheUseCase(private val repository: PrecioLecheRepository) {
    operator fun invoke(): Flow<PrecioLeche?> = repository.observeVigente()
}

class GetPrecioLecheUseCase(private val repository: PrecioLecheRepository) {
    suspend operator fun invoke(): Double = repository.getVigente()?.precioLitro ?: 0.0
}

class SetPrecioLecheUseCase(private val repository: PrecioLecheRepository) {
    /** Como `registrarPrecio` de Laravel: precio >= 0 y la fecha desde la que rige. */
    suspend operator fun invoke(precioLitro: Double?, vigenteDesde: LocalDate): AppResult<Unit> {
        if (precioLitro == null || precioLitro < 0.0) return AppResult.Error("Ingresa un precio por litro válido (0 o más).")
        repository.setNuevoPrecio(precioLitro, vigenteDesde)
        return AppResult.Success(Unit)
    }
}
