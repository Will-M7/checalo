package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import kotlinx.coroutines.flow.Flow

// Casos de uso del "Mapa de rutas" (planta / administrador) y del "Mapa de recojo"
// (acopiador), equivalentes a las páginas Livewire planta.mapa-rutas y acopiador.mapa-recojo.

class ObserveTodasLasRutasUseCase(private val repository: RutaRepository) {
    operator fun invoke(): Flow<List<Ruta>> = repository.observeAll()
}

/** Sube o baja una parada una posición en el orden de recojo. */
class MoverParadaUseCase(private val repository: RutaRepository) {
    suspend operator fun invoke(ruta: Ruta, proveedorId: String, direccion: Int) {
        val ids = ruta.proveedorIds.toMutableList()
        val posicion = ids.indexOf(proveedorId)
        val destino = posicion + if (direccion < 0) -1 else 1
        if (posicion < 0 || destino !in ids.indices) return
        ids[posicion] = ids[destino].also { ids[destino] = ids[posicion] }
        repository.reordenarProveedores(ruta.id, ids)
    }
}

class GuardarTrazoRutaUseCase(private val repository: RutaRepository) {
    suspend operator fun invoke(rutaId: String, trazo: List<PuntoGeo>) = repository.actualizarTrazo(rutaId, trazo)
}

/** Guarda el punto de la finca que planta tocó en el mapa (sin cambiar su dirección escrita). */
class UbicarProveedorEnMapaUseCase(private val repository: ProveedorRepository) {
    suspend operator fun invoke(proveedorId: String, punto: PuntoGeo): AppResult<Unit> {
        if (punto.lat !in -90.0..90.0 || punto.lng !in -180.0..180.0) return AppResult.Error("Ubicación fuera de rango.")
        val proveedor = repository.getById(proveedorId) ?: return AppResult.Error("Proveedor no encontrado.")
        repository.actualizarFinca(proveedor.id, proveedor.fincaUbicacion, proveedor.fincaReferencia, punto.lat, punto.lng)
        return AppResult.Success(Unit)
    }
}

/**
 * Proveedores ya recogidos en la ronda actual del acopiador: mismo criterio que la web
 * (recojos de hoy que todavía no se entregaron a planta).
 */
class GetProveedoresRecogidosHoyUseCase(private val repository: RecojoRepository) {
    suspend operator fun invoke(acopiadorId: String): Set<String> =
        repository.getByAcopiadorYFecha(acopiadorId, todayLocalDate())
            .filter { !it.entregado }
            .map { it.proveedorId }
            .toSet()
}

/** Proveedores con algún recojo registrado hoy, de cualquier acopiador (detalle de una ruta en Rutas). */
class GetProveedoresRecogidosEnElDiaUseCase(private val repository: RecojoRepository) {
    suspend operator fun invoke(): Set<String> {
        val hoy = todayLocalDate()
        return repository.getEntreFechas(hoy, hoy).map { it.proveedorId }.toSet()
    }
}
