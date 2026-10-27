package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.Validators
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.domain.model.Acopiador
import com.example.ecolacteoshuata.domain.model.Proveedor
import kotlinx.coroutines.flow.Flow

class ObserveUsuariosUseCase(private val repository: UsuarioRepository) {
    operator fun invoke(): Flow<List<Usuario>> = repository.observeAll()
}

class GetUsuariosUseCase(private val repository: UsuarioRepository) {
    suspend operator fun invoke(): List<Usuario> = repository.getAll()
}

/** Datos de la finca que Laravel pide cuando el usuario tiene el rol Proveedor. */
data class DatosProveedorInput(
    val acopiadorId: String? = null,
    val fincaUbicacion: String = "",
    val fincaReferencia: String = "",
    /** Punto marcado en el mapa (`latitud`/`longitud` de Laravel); opcional. */
    val latitud: Double? = null,
    val longitud: Double? = null,
)

/** Formulario "Nuevo usuario" / "Editar usuario" (`id == null` crea uno nuevo). */
data class GuardarUsuarioInput(
    val id: String?,
    val nombres: String,
    val apellidos: String,
    val documento: String,
    val celular: String,
    val rol: Rol?,
    val usuario: String,
    val password: String,
    val activo: Boolean,
    val proveedor: DatosProveedorInput = DatosProveedorInput(),
    val rolesDinamicos: Set<String>? = null,
)

/**
 * Crea las filas de acopiador/proveedor que necesita un usuario según sus roles, igual que
 * `Acopiador::firstOrCreate` y `Proveedor::guardarConUbicacion` en Laravel.
 */
class PerfilesPorRol(
    private val proveedorRepository: ProveedorRepository,
    private val acopiadorRepository: AcopiadorRepository,
) {
    /** Mismas reglas que Laravel cuando uno de los roles es Proveedor. */
    fun validarProveedor(roles: Collection<Rol>, datos: DatosProveedorInput): String? {
        if (Rol.PROVEEDOR !in roles) return null
        if (datos.acopiadorId == null) return "Selecciona el acopiador del proveedor."
        if (!Validators.noVacio(datos.fincaUbicacion)) return "Ingresa la ubicación de la finca."
        return null
    }

    /**
     * [datos] `null` = solo asegurar que el perfil exista, sin tocar la finca ni el acopiador
     * de un proveedor que ya los tiene (lo usa "Asignar roles", que solo asigna roles).
     */
    suspend fun asegurar(usuario: Usuario, datos: DatosProveedorInput?) {
        if (usuario.tieneRol(Rol.ACOPIADOR) && acopiadorRepository.getByUsuarioId(usuario.id) == null) {
            acopiadorRepository.crear(
                Acopiador(id = IdGenerator.newId(), usuarioId = usuario.id, nombreCompleto = usuario.nombreCompleto, rutaNombre = "")
            )
        }
        if (usuario.tieneRol(Rol.PROVEEDOR)) {
            val existente = proveedorRepository.getByUsuarioId(usuario.id)
            if (existente == null) {
                proveedorRepository.crear(
                    Proveedor(
                        id = IdGenerator.newId(),
                        usuarioId = usuario.id,
                        nombreCompleto = usuario.nombreCompleto,
                        fincaUbicacion = datos?.fincaUbicacion.orEmpty().trim(),
                        fincaReferencia = datos?.fincaReferencia.orEmpty().trim(),
                        latitud = datos?.latitud,
                        longitud = datos?.longitud,
                        acopiadorId = datos?.acopiadorId,
                        bloqueado = false,
                        alteraciones = 0,
                    )
                )
            } else if (datos != null) {
                proveedorRepository.actualizarFinca(
                    existente.id, datos.fincaUbicacion.trim(), datos.fincaReferencia.trim(), datos.latitud, datos.longitud,
                )
                proveedorRepository.asignarAcopiador(existente.id, datos.acopiadorId)
            }
        }
    }
}

/** Guarda un usuario con las validaciones de `admin/usuarios` de Laravel. */
class GuardarUsuarioUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val perfiles: PerfilesPorRol,
    private val asignarRoles: AsignarRolesUseCase? = null,
    private val administracion: AdministrarRolesUseCase? = null,
) {
    suspend operator fun invoke(input: GuardarUsuarioInput): AppResult<Usuario> {
        if (input.rolesDinamicos != null) {
            val central = administracion ?: return AppResult.Error("La administración central no está disponible.")
            val previo = input.id?.let { usuarioRepository.getById(it) }
            val p = input.proveedor
            val datos = com.example.ecolacteoshuata.domain.model.GuardarUsuarioCentral(
                input.nombres.trim(), input.apellidos.trim(), input.documento.trim(), input.celular.trim(),
                input.usuario.trim(), input.password, input.activo, input.rolesDinamicos.toList(),
                p.fincaUbicacion, p.fincaReferencia, p.acopiadorId, p.latitud, p.longitud)
            return when (val r = central.guardarUsuario(input.id, datos)) {
                is AppResult.Error -> r
                is AppResult.Success -> {
                    val a = r.data.autorizacion
                    val categorias = a.categorias_historicas.orEmpty().mapNotNull(Rol::fromServerValue)
                    val usuario = Usuario(r.data.id, datos.nombres, datos.apellidos, datos.documento, datos.celular,
                        categorias.firstOrNull(), datos.usuario, previo?.passwordHash.orEmpty(), datos.activo,
                        previo?.creadoEn ?: nowLocalDateTime(), categorias.drop(1).toSet(),
                        a.roles.any { it.id == "tomar_asistencia" }, a)
                    usuarioRepository.guardarDesdeServidor(usuario, input.password)
                    AppResult.Success(usuario)
                }
            }
        }
        val documento = input.documento.trim()
        val nombreUsuario = input.usuario.trim()
        if (!Validators.noVacio(input.nombres) || !Validators.noVacio(input.apellidos)) {
            return AppResult.Error("Ingresa nombres y apellidos.")
        }
        if (!Validators.noVacio(documento) || documento.length > 15) {
            return AppResult.Error("Ingresa el DNI/RUC (máximo 15 caracteres).")
        }
        if (!Validators.noVacio(nombreUsuario)) return AppResult.Error("Ingresa el usuario de acceso.")
        // Al crear es obligatoria; al editar, vacía significa "no cambiarla".
        if ((input.id == null || input.password.isNotEmpty()) && input.password.length < 4) {
            return AppResult.Error("La contraseña debe tener al menos 4 caracteres.")
        }
        val otros = usuarioRepository.getAll().filter { it.id != input.id }
        if (otros.any { it.documento.trim() == documento }) return AppResult.Error("Ya existe un usuario con ese DNI/RUC.")
        if (otros.any { it.usuario.equals(nombreUsuario, ignoreCase = true) }) return AppResult.Error("Ese nombre de usuario ya existe.")

        val actual = input.id?.let { usuarioRepository.getById(it) }
        // Si el rol principal cambia, el anterior deja de ser adicional-implícito; los demás se conservan.
        val adicionales = actual?.rolesAdicionales.orEmpty().filterNot { it == input.rol }.toSet()
        val categoria = input.rol ?: return AppResult.Error("Selecciona una categoría histórica o utiliza la asignación central.")
        perfiles.validarProveedor(adicionales + categoria, input.proveedor)?.let { return AppResult.Error(it) }
        if (actual != null && actual.rol != input.rol) {
            val central = asignarRoles ?: return AppResult.Error("Cambia los roles mediante la asignación central con conexión.")
            val asignados = actual.autorizacion?.roles ?: return AppResult.Error("Sincroniza las asignaciones del usuario antes de cambiar su rol.")
            val codigos = asignados.map { it.id }.toSet() - actual.rol?.valorServidor.orEmpty() + categoria.valorServidor
            when (val cambio = central(actual.id, codigos, asignados.any { it.id == "tomar_asistencia" })) {
                is AppResult.Error -> return AppResult.Error(cambio.message)
                is AppResult.Success -> Unit
            }
        }

        val usuario = Usuario(
            id = input.id ?: IdGenerator.newId(),
            nombres = input.nombres.trim(),
            apellidos = input.apellidos.trim(),
            documento = documento,
            celular = input.celular.trim(),
            rol = input.rol,
            usuario = nombreUsuario,
            passwordHash = actual?.passwordHash.orEmpty(),
            activo = input.activo,
            creadoEn = actual?.creadoEn ?: nowLocalDateTime(),
            rolesAdicionales = adicionales,
        )
        if (actual == null) {
            usuarioRepository.crear(usuario, input.password)
        } else {
            usuarioRepository.actualizar(usuario, input.password.takeIf { it.isNotBlank() })
        }
        perfiles.asegurar(usuario, input.proveedor)
        return AppResult.Success(usuario)
    }
}

/** "Asignar rol a usuarios" de Laravel: uno o varios roles por usuario. */
class AsignarRolesUseCase(
    private val conexion: com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository,
    private val api: com.example.ecolacteoshuata.data.remote.datasource.SyncRemoteDataSource,
    private val db: com.example.ecolacteoshuata.db.AppDatabase,
) {
    /** La pantalla nativa usa la misma asignación central; nunca concede permisos offline. */
    suspend operator fun invoke(usuarioId: String, roles: Set<String>, tomaAsistencia: Boolean): AppResult<Unit> {
        val c = conexion.get()
        if (!c.activa || c.token.isNullOrBlank()) return AppResult.Error("Conecta con Laravel para cambiar autorizaciones. Los roles se administran centralmente.")
        val seleccion = roles - "tomar_asistencia"
        return try {
            val a = api.asignarRoles(c.host, c.puerto, usuarioId, seleccion)
            com.example.ecolacteoshuata.data.repository.AutorizacionLocal.guardar(db, a)
            AppResult.Success(Unit)
        } catch (e: Exception) { AppResult.Error(e.message ?: "Laravel no aceptó la asignación.") }
    }
}

class ActualizarUsuarioUseCase(private val repository: UsuarioRepository) {
    suspend operator fun invoke(usuario: Usuario, nuevaPassword: String?): AppResult<Unit> {
        if (!Validators.noVacio(usuario.nombres) || !Validators.noVacio(usuario.apellidos)) {
            return AppResult.Error("Ingrese nombres y apellidos.")
        }
        repository.actualizar(usuario, nuevaPassword?.takeIf { it.isNotBlank() })
        return AppResult.Success(Unit)
    }
}

class EliminarUsuarioUseCase(private val repository: UsuarioRepository) {
    suspend operator fun invoke(id: String) = repository.eliminar(id)
}
