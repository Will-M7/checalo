package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.data.sync.ConteoSync
import com.example.ecolacteoshuata.data.sync.ProgresoSync
import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.domain.model.ConexionServidor
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.model.UsuarioRemoto
import com.example.ecolacteoshuata.domain.repository.AuthRemoteDataSource
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.repository.SessionRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginUseCaseTest {

    private val cuentaLocal = Usuario(
        id = "u1", nombres = "Rufino", apellidos = "Quispe", documento = "", celular = "",
        rol = Rol.ACOPIADOR, usuario = "acopio", passwordHash = "", activo = true, creadoEn = nowLocalDateTime(),
    )

    private inner class Usuarios : UsuarioRepository {
        override fun observeAll(): Flow<List<Usuario>> = flowOf(listOf(cuentaLocal))
        override suspend fun getAll() = listOf(cuentaLocal)
        override suspend fun getById(id: String) = cuentaLocal.takeIf { it.id == id }
        override suspend fun getByUsuario(usuario: String) = cuentaLocal.takeIf { it.usuario == usuario }
        override suspend fun getByRol(rol: Rol) = listOf(cuentaLocal)
        override suspend fun crear(usuario: Usuario, passwordPlano: String) = Unit
        override suspend fun actualizar(usuario: Usuario, passwordPlano: String?) = Unit
        override suspend fun eliminar(id: String) = Unit
        override suspend fun countActivos() = 1L
        override suspend fun countAll() = 1L
        override fun observeById(id: String): Flow<Usuario?> = flowOf(cuentaLocal.takeIf { it.id == id })
        override suspend fun asignarTomaAsistencia(id: String, tomaAsistencia: Boolean) = Unit
        override suspend fun verificarCredenciales(usuario: String, passwordPlano: String) =
            cuentaLocal.takeIf { usuario == "acopio" && passwordPlano == "1234" }
    }

    private class Sesion : SessionRepository {
        override val usuarioActual = MutableStateFlow<Usuario?>(null)
        override fun setUsuario(usuario: Usuario?) { usuarioActual.value = usuario }
    }

    private class Conexion : ConexionServidorRepository {
        private val c = ConexionServidor(host = "192.168.1.10", puerto = "8000", activa = true, ultimaSincronizacion = "x")
        override fun observe() = flowOf(c)
        override suspend fun get() = c
        override suspend fun guardar(conexion: ConexionServidor) = Unit
        override suspend fun actualizarUltimaSincronizacion(fecha: String) = Unit
        override suspend fun reiniciarSincronizacion() = Unit
        override suspend fun guardarToken(token: String?) = Unit
    }

    private class Remoto(private val r: AppResult<UsuarioRemoto>) : AuthRemoteDataSource {
        override suspend fun login(host: String, puerto: String, usuario: String, password: String) = r
    }

    private class Sync : SyncService {
        override val estado: StateFlow<EstadoSincronizacion> = MutableStateFlow(EstadoSincronizacion.SIN_SENAL)
        override val pendientes: StateFlow<Int> = MutableStateFlow(0)
        override val progreso: StateFlow<ProgresoSync?> = MutableStateFlow(null)
        override val ultimoError: StateFlow<String?> = MutableStateFlow(null)
        override val ultimoResumen: StateFlow<List<ConteoSync>> = MutableStateFlow(emptyList())
        override fun actualizarPendientes(cantidad: Int) = Unit
        override suspend fun sincronizarAhora() = Unit
        override suspend fun descargarTodoDeNuevo() = Unit
        override suspend fun probarConexion(host: String, puerto: String): AppResult<Long> = AppResult.Success(0)
    }

    private fun login(remoto: AppResult<UsuarioRemoto>) =
        LoginUseCase(Usuarios(), Sesion(), Conexion(), Remoto(remoto), Sync(), TestScope())

    @Test
    fun sinSenal_entraConLaCuentaGuardadaEnElTelefono() = runTest {
        val r = login(AppResult.Error("No se pudo conectar", sinConexion = true))("acopio", "1234")
        assertTrue(r is AppResult.Success)
        assertEquals("u1", r.data.id)
    }

    @Test
    fun sinSenal_conContrasenaEquivocada_noEntra() = runTest {
        val r = login(AppResult.Error("No se pudo conectar", sinConexion = true))("acopio", "0000")
        assertTrue(r is AppResult.Error)
    }

    @Test
    fun servidorRechazaCredenciales_noUsaLaCuentaLocal() = runTest {
        // El servidor respondió "incorrecto" (p. ej. cambiaron la contraseña en Laravel): manda el servidor.
        val r = login(AppResult.Error("Usuario o contraseña incorrectos."))("acopio", "1234")
        assertTrue(r is AppResult.Error)
    }
}
