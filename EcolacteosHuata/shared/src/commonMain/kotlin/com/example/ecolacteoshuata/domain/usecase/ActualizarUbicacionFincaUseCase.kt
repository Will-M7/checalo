package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository

class ActualizarUbicacionFincaUseCase(private val repository: ProveedorRepository) {
    suspend operator fun invoke(proveedorId: String, ubicacion: String, referencia: String, lat: Double?, lon: Double?): AppResult<Unit> {
        if (!Validators.noVacio(ubicacion)) return AppResult.Error("Ingrese la ubicación de la finca.")
        repository.actualizarFinca(proveedorId, ubicacion, referencia, lat, lon)
        return AppResult.Success(Unit)
    }
}
