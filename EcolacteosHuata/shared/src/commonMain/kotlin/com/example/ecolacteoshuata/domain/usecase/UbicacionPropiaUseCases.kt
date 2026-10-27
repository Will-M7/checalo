package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.domain.model.PuntoGeo
import com.example.ecolacteoshuata.domain.model.Ruta
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class UbicacionPropiaInput(
    val fincaUbicacion: String,
    val fincaReferencia: String,
    val ubicacion: PuntoGeo?,
    val acopiadorId: String?,
)

/**
 * "Ubicación de mi finca" del perfil del proveedor, igual que `Proveedor::guardarElegidoPorProveedor`
 * de Laravel: guarda la finca y el acopiador y, si algo cambió, deja al proveedor en la ruta de ese
 * acopiador (la que ya tenía con él o la activa que pasa más cerca), hace que el camino pase por su
 * finca y lo saca de las rutas de otros acopiadores. Devuelve el mensaje para mostrar.
 */
class GuardarUbicacionPropiaUseCase(
    private val proveedorRepository: ProveedorRepository,
    private val rutaRepository: RutaRepository,
) {
    suspend operator fun invoke(usuarioId: String, input: UbicacionPropiaInput): AppResult<String> {
        if (!Validators.noVacio(input.fincaUbicacion)) return AppResult.Error("Ingresa la ubicación / dirección de tu finca.")
        val acopiadorId = input.acopiadorId ?: return AppResult.Error("Selecciona el acopiador que recoge tu leche.")
        val anterior = proveedorRepository.getByUsuarioId(usuarioId) ?: return AppResult.Error("Tu cuenta no tiene datos de proveedor.")

        val puntoAnterior = punto(anterior.latitud, anterior.longitud)
        val puntoNuevo = input.ubicacion
        proveedorRepository.actualizarFinca(anterior.id, input.fincaUbicacion.trim(), input.fincaReferencia.trim(), puntoNuevo?.lat, puntoNuevo?.lng)
        proveedorRepository.asignarAcopiador(anterior.id, acopiadorId)

        val rutas = rutaRepository.getAll()
        val cambio = anterior.acopiadorId != acopiadorId ||
            anterior.fincaUbicacion != input.fincaUbicacion.trim() ||
            puntoAnterior != puntoNuevo
        if (!cambio) return AppResult.Success(mensaje(rutas.any { anterior.id in it.proveedorIds }))

        val activasDelAcopiador = rutas.filter { it.acopiadorId == acopiadorId && it.activa }
        val ruta = activasDelAcopiador.firstOrNull { anterior.id in it.proveedorIds }
            ?: rutaMasCercana(activasDelAcopiador, puntoNuevo)

        // Sale de las demás rutas.
        rutas.filter { it.id != ruta?.id && anterior.id in it.proveedorIds }.forEach { otra ->
            rutaRepository.asignarProveedores(otra.id, otra.proveedorIds - anterior.id)
        }
        if (ruta == null) return AppResult.Success(mensaje(enRuta = false))

        if (anterior.id !in ruta.proveedorIds) {
            rutaRepository.asignarProveedores(ruta.id, ruta.proveedorIds + anterior.id)
        }
        if (puntoNuevo != null) {
            rutaRepository.actualizarTrazo(ruta.id, ubicarEnTrazo(ruta.trazo, puntoNuevo, puntoAnterior))
        }
        return AppResult.Success(mensaje(enRuta = true))
    }

    private fun mensaje(enRuta: Boolean) =
        if (enRuta) "Ubicación guardada. Ya estás en la ruta de tu acopiador."
        else "Ubicación guardada. Tu acopiador aún no tiene ruta: se avisó a planta."

    private fun punto(lat: Double?, lng: Double?) = if (lat != null && lng != null) PuntoGeo(lat, lng) else null

    /** `Acopiador::rutaMasCercana`: sin punto, la primera por nombre; las rutas sin camino van al final. */
    private fun rutaMasCercana(rutas: List<Ruta>, punto: PuntoGeo?): Ruta? {
        val ordenadas = rutas.sortedBy { it.nombre }
        if (punto == null) return ordenadas.firstOrNull()
        return ordenadas.minByOrNull { ruta -> ruta.trazo.minOfOrNull { distancia(punto, it) } ?: Double.MAX_VALUE }
    }

    /** `Ruta::ubicarEnTrazo`: quita el punto anterior e inserta el nuevo donde menos alarga el camino. */
    private fun ubicarEnTrazo(trazo: List<PuntoGeo>, punto: PuntoGeo, anterior: PuntoGeo?): List<PuntoGeo> {
        val base = trazo.filterNot { anterior != null && mismoPunto(it, anterior) }
        if (base.any { mismoPunto(it, punto) }) return base
        if (base.isEmpty()) return listOf(punto)

        val opciones = mutableMapOf(0 to distancia(punto, base.first()), base.size to distancia(base.last(), punto))
        for (i in 0 until base.size - 1) {
            opciones[i + 1] = distancia(base[i], punto) + distancia(punto, base[i + 1]) - distancia(base[i], base[i + 1])
        }
        val posicion = opciones.minByOrNull { it.value }!!.key
        return base.toMutableList().apply { add(posicion, punto) }
    }

    private fun mismoPunto(a: PuntoGeo, b: PuntoGeo) = abs(a.lat - b.lat) < 1e-7 && abs(a.lng - b.lng) < 1e-7

    /** `Ruta::distancia`: grados corregidos por latitud; basta para comparar dentro de la zona. */
    private fun distancia(a: PuntoGeo, b: PuntoGeo): Double {
        val dx = (a.lng - b.lng) * cos(((a.lat + b.lat) / 2) * PI / 180)
        val dy = a.lat - b.lat
        return sqrt(dx * dx + dy * dy)
    }
}
