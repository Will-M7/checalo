package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow

class GetUsuarioByIdUseCase(private val repository: UsuarioRepository) {
    suspend operator fun invoke(id: String): Usuario? = repository.getById(id)
}

class GetProveedorPorUsuarioUseCase(private val repository: ProveedorRepository) {
    suspend operator fun invoke(usuarioId: String): Proveedor? = repository.getByUsuarioId(usuarioId)
}

class GetAcopiadorPorUsuarioUseCase(private val repository: AcopiadorRepository) {
    suspend operator fun invoke(usuarioId: String): Acopiador? = repository.getByUsuarioId(usuarioId)
}

class ObserveAcopiadoresUseCase(private val repository: AcopiadorRepository) {
    operator fun invoke(): Flow<List<Acopiador>> = repository.observeAll()
}

class ObserveProveedoresUseCase(private val repository: ProveedorRepository) {
    operator fun invoke(): Flow<List<Proveedor>> = repository.observeAll()
}

class GetProveedoresPorAcopiadorUseCase(private val repository: ProveedorRepository) {
    suspend operator fun invoke(acopiadorId: String): List<Proveedor> = repository.getByAcopiador(acopiadorId)
}

/**
 * Asigna (o quita) el acopiador de un proveedor y mantiene coherentes las paradas de las rutas:
 * el proveedor sale de cualquier ruta y, si queda asignado, entra al final de la primera ruta
 * activa de ese acopiador. Sin esto el "Mapa de rutas" no mostraba al proveedor recién asignado
 * y seguía mostrando al que se quitó.
 */
class AsignarAcopiadorAProveedorUseCase(
    private val repository: ProveedorRepository,
    private val rutaRepository: RutaRepository,
) {
    suspend operator fun invoke(proveedorId: String, acopiadorId: String?) {
        repository.asignarAcopiador(proveedorId, acopiadorId)
        rutaRepository.getAll()
            .filter { proveedorId in it.proveedorIds && it.acopiadorId != acopiadorId }
            .forEach { ruta -> rutaRepository.asignarProveedores(ruta.id, ruta.proveedorIds - proveedorId) }
        if (acopiadorId == null) return
        val rutas = rutaRepository.getByAcopiador(acopiadorId)
        if (rutas.any { proveedorId in it.proveedorIds }) return
        val destino = rutas.firstOrNull { it.activa } ?: rutas.firstOrNull() ?: return
        rutaRepository.asignarProveedores(destino.id, destino.proveedorIds + proveedorId)
    }
}
