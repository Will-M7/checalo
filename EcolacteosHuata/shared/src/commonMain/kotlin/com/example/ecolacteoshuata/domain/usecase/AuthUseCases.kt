package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.model.UsuarioRemoto
import com.example.ecolacteoshuata.domain.repository.AuthRemoteDataSource
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.repository.SessionRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val sessionRepository: SessionRepository,
    private val conexionServidorRepository: ConexionServidorRepository,
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val syncService: SyncService,
    private val scope: CoroutineScope,
) {
    suspend operator fun invoke(usuario: String, password: String): AppResult<Usuario> {
        if (usuario.isBlank() || password.isBlank()) {
            return AppResult.Error("Verifique su usuario y su contraseña.")
        }
        val conexion = conexionServidorRepository.get()
        val resultado = if (conexion.activa && conexion.host.isNotBlank()) {
            iniciarSesionRemota(conexion.host, conexion.puerto, usuario, password)
        } else {
            // Recién instalada, la base está vacía: primero hay que conectarse a Laravel.
            if (usuarioRepository.getAll().isEmpty()) {
                return AppResult.Error(
                    "El teléfono aún no tiene datos. Toca \"Servidor\" y configura la conexión " +
                        "para descargar la información desde Laravel.",
                )
            }
            val encontrado = usuarioRepository.verificarCredenciales(usuario, password)
                ?: return AppResult.Error("Verifique su usuario y su contraseña.")
            AppResult.Success(encontrado)
        }
        if (resultado is AppResult.Success) {
            sessionRepository.setUsuario(resultado.data)
            // La identidad nueva debe estar establecida antes de que el sync revise el token.
            if (conexion.activa && conexion.host.isNotBlank()) scope.launch { syncService.sincronizarAhora() }
        }
        return resultado
    }

    /**
     * Valida las credenciales contra el servidor Laravel/MySQL configurado y guarda (o
     * actualiza) el usuario en la base local, para que el resto de la app siga funcionando
     * offline-first una vez iniciada la sesión.
     */
    private suspend fun iniciarSesionRemota(host: String, puerto: String, usuario: String, password: String): AppResult<Usuario> {
        val remoto = authRemoteDataSource.login(host, puerto, usuario, password)
        if (remoto is AppResult.Error) {
            if (!remoto.sinConexion) return AppResult.Error(remoto.message)
            // Sin señal (típico en la ruta): se valida contra la copia local de la cuenta, que
            // queda guardada tras el primer ingreso con conexión. Así la app sigue funcionando
            // offline; los datos se actualizan en la próxima sincronización.
            val local = usuarioRepository.verificarCredenciales(usuario, password)
                ?: return AppResult.Error(
                    remoto.message + " Si ya ingresaste antes en este teléfono con conexión, revisa tu contraseña; " +
                        "si es tu primer ingreso, necesitas conectarte al servidor una vez.",
                )
            return AppResult.Success(local)
        }
        val datos = (remoto as AppResult.Success<UsuarioRemoto>).data

        datos.autorizacion?.let { usuarioRepository.guardarAutorizacion(it) }
        val existente = usuarioRepository.getByUsuario(datos.usuario)
        val usuarioLocal = Usuario(
            id = datos.id,
            nombres = datos.nombres,
            apellidos = datos.apellidos,
            documento = datos.documento,
            celular = datos.celular,
            rol = datos.rol,
            rolesAdicionales = datos.roles.filterNot { it == datos.rol }.toSet(),
            autorizacion = datos.autorizacion,
            tomaAsistencia = datos.tomaAsistencia,
            usuario = datos.usuario,
            passwordHash = existente?.passwordHash ?: "",
            activo = datos.activo,
            creadoEn = existente?.creadoEn ?: nowLocalDateTime(),
        )
        usuarioRepository.guardarDesdeServidor(usuarioLocal, password)
        val guardado = usuarioRepository.getByUsuario(datos.usuario)
            ?: return AppResult.Error("No se pudo guardar el usuario en el dispositivo.")
        // Son los datos que Laravel acaba de dar: no se anotan como cambio del teléfono.
        usuarioRepository.olvidarCambiosPendientes(guardado.id)

        // Con el token, este teléfono ya puede descargar todo el sistema (y, con él, las
        // contraseñas de todos los usuarios para que puedan ingresar sin señal).
        sessionRepository.setUsuario(guardado)
        conexionServidorRepository.guardarToken(datos.token)

        // No bloquea el login: la app navega de una vez y el SyncBanner (SessionHeader) refleja
        // el progreso a medida que avanza.
        return AppResult.Success(guardado)
    }
}

class GetSessionUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(): StateFlow<Usuario?> = sessionRepository.usuarioActual
}

class LogoutUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke() {
        sessionRepository.setUsuario(null)
    }
}
