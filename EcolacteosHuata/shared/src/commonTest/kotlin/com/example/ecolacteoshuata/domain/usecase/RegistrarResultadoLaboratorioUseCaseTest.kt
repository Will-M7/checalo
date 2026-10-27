package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeProveedorRepository(initial: Proveedor) : ProveedorRepository {
    private val estado = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Proveedor>> = throw NotImplementedError()
    override suspend fun getAll(): List<Proveedor> = listOf(estado.value)
    override suspend fun getById(id: String): Proveedor? = estado.value.takeIf { it.id == id }
    override suspend fun getByUsuarioId(usuarioId: String): Proveedor? = null
    override suspend fun getByAcopiador(acopiadorId: String): List<Proveedor> = emptyList()
    override suspend fun crear(proveedor: Proveedor) = Unit
    override suspend fun actualizarFinca(id: String, ubicacion: String, referencia: String, lat: Double?, lon: Double?) = Unit
    override suspend fun asignarAcopiador(id: String, acopiadorId: String?) = Unit

    override suspend fun incrementarAlteraciones(id: String): Int {
        estado.value = estado.value.copy(alteraciones = estado.value.alteraciones + 1)
        return estado.value.alteraciones
    }

    override suspend fun resetAlteraciones(id: String) {
        estado.value = estado.value.copy(alteraciones = 0)
    }

    override suspend fun bloquear(id: String, bloqueado: Boolean) {
        estado.value = estado.value.copy(bloqueado = bloqueado)
    }

    override suspend fun eliminar(id: String) = Unit

    fun actual() = estado.value
}

private class FakeResultadoLaboratorioRepository : ResultadoLaboratorioRepository {
    private val resultados = mutableListOf<ResultadoLaboratorio>()

    override fun observeByProveedor(proveedorId: String): Flow<List<ResultadoLaboratorio>> = throw NotImplementedError()
    override suspend fun getAll(): List<ResultadoLaboratorio> = resultados
    override suspend fun getByProveedor(proveedorId: String): List<ResultadoLaboratorio> = resultados.filter { it.proveedorId == proveedorId }
    override suspend fun crear(resultado: ResultadoLaboratorio) { resultados += resultado }
    override suspend fun countNoConformes(proveedorId: String): Long = resultados.count { it.proveedorId == proveedorId && !it.conforme }.toLong()
    override fun observeRecientes(): Flow<List<ResultadoLaboratorio>> = throw NotImplementedError()
    override suspend fun getById(id: String): ResultadoLaboratorio? = resultados.firstOrNull { it.id == id }
    override suspend fun actualizar(resultado: ResultadoLaboratorio) { resultados.replaceAll { if (it.id == resultado.id) resultado else it } }
    override suspend fun marcarEnviadoProduccion(id: String) = Unit
}

class RegistrarResultadoLaboratorioUseCaseTest {
    private fun nuevoProveedor() = Proveedor(
        id = "prov1", usuarioId = "u1", nombreCompleto = "Proveedor de prueba",
        fincaUbicacion = "Huata", fincaReferencia = "", latitud = null, longitud = null,
        acopiadorId = "acop1", bloqueado = false, alteraciones = 0,
    )

    private fun input(resultado: ResultadoTipo) = NuevoResultadoLabInput(
        proveedorId = "prov1", fecha = todayLocalDate(), turnoId = null,
        resultado = resultado, densidad = null, acidez = null, temperatura = null, observaciones = "",
    )

    @Test
    fun cadaResultadoNoConformeIncrementaLasAlteraciones() = runTest {
        val proveedorRepo = FakeProveedorRepository(nuevoProveedor())
        val useCase = RegistrarResultadoLaboratorioUseCase(FakeResultadoLaboratorioRepository(), proveedorRepo)

        val resultado = useCase(input(ResultadoTipo.ACIDIFICADO))

        assertTrue(resultado is AppResult.Success)
        assertEquals(1, proveedorRepo.actual().alteraciones)
        assertFalse(resultado.data.bloqueadoAutomaticamente)
    }

    @Test
    fun resultadoConformeNoIncrementaAlteraciones() = runTest {
        val proveedorRepo = FakeProveedorRepository(nuevoProveedor())
        val useCase = RegistrarResultadoLaboratorioUseCase(FakeResultadoLaboratorioRepository(), proveedorRepo)

        useCase(input(ResultadoTipo.CONFORME))

        assertEquals(0, proveedorRepo.actual().alteraciones)
    }

    @Test
    fun alTercerAvisoElProveedorQuedaBloqueado() = runTest {
        val proveedorRepo = FakeProveedorRepository(nuevoProveedor())
        val useCase = RegistrarResultadoLaboratorioUseCase(FakeResultadoLaboratorioRepository(), proveedorRepo)

        useCase(input(ResultadoTipo.ACIDIFICADO))
        useCase(input(ResultadoTipo.AGUADO))
        val tercerResultado = useCase(input(ResultadoTipo.SOLEADO)) as AppResult.Success

        assertEquals(3, proveedorRepo.actual().alteraciones)
        assertTrue(tercerResultado.data.bloqueadoAutomaticamente)
        assertTrue(proveedorRepo.actual().bloqueado)
    }

    @Test
    fun editarUnResultadoNoSumaOtraAlteracion() = runTest {
        val proveedorRepo = FakeProveedorRepository(nuevoProveedor())
        val resultados = FakeResultadoLaboratorioRepository()
        val useCase = RegistrarResultadoLaboratorioUseCase(resultados, proveedorRepo)

        val registrado = useCase(input(ResultadoTipo.ACIDIFICADO)) as AppResult.Success
        val editado = useCase(input(ResultadoTipo.AGUADO).copy(resultadoId = registrado.data.resultado.id))

        assertTrue(editado is AppResult.Success)
        assertEquals(1, proveedorRepo.actual().alteraciones)
        assertEquals(1, resultados.getAll().size)
        assertEquals(ResultadoTipo.AGUADO, resultados.getAll().first().resultado)
    }

    @Test
    fun bloquearProveedorUseCaseMarcaBloqueadoYReiniciaAlteraciones() = runTest {
        val proveedorRepo = FakeProveedorRepository(nuevoProveedor().copy(alteraciones = 3))
        val bloquear = BloquearProveedorUseCase(proveedorRepo)

        bloquear(proveedorRepo.actual().id, true)

        assertTrue(proveedorRepo.actual().bloqueado)
    }
}
