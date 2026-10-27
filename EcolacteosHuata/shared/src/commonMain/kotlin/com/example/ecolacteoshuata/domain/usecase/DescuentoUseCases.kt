package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.domain.model.Descuento
import com.example.ecolacteoshuata.domain.repository.DescuentoRepository

/** Mismas reglas que `admin/descuentos` de Laravel: nombre obligatorio y porcentaje entre 0.01 y 100. */
class GuardarDescuentoUseCase(private val repository: DescuentoRepository) {
    suspend operator fun invoke(id: String?, nombre: String, porcentajeTexto: String, activo: Boolean): AppResult<Unit> {
        if (!Validators.noVacio(nombre)) return AppResult.Error("Ingresa el nombre del descuento.")
        val porcentaje = porcentajeTexto.trim().replace(',', '.').toDoubleOrNull()
        if (porcentaje == null || porcentaje < 0.01 || porcentaje > 100) {
            return AppResult.Error("El porcentaje debe estar entre 0.01 y 100.")
        }
        repository.guardar(Descuento(id = id.orEmpty(), nombre = nombre.trim(), porcentaje = porcentaje, activo = activo))
        return AppResult.Success(Unit)
    }
}
