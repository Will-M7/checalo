package com.example.ecolacteoshuata.presentation.common.conexion

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.data.sync.ConteoSync
import com.example.ecolacteoshuata.data.sync.ProgresoSync
import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.core.util.AppResult
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyyHm
import com.example.ecolacteoshuata.domain.model.ConexionServidor
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed interface PruebaConexion {
    data object Ninguna : PruebaConexion
    data object Probando : PruebaConexion
    data class Exitosa(val milisegundos: Long) : PruebaConexion
    data class Fallida(val motivo: String) : PruebaConexion
}

data class ConexionSyncUiState(
    val guardada: ConexionServidor = ConexionServidor(),
    val host: String = "",
    val puerto: String = "8000",
    val activa: Boolean = false,
    val estado: EstadoSincronizacion = EstadoSincronizacion.SIN_SENAL,
    val progreso: ProgresoSync? = null,
    val error: String? = null,
    val resumen: List<ConteoSync> = emptyList(),
    val pendientes: Int = 0,
    val revisiones: List<com.example.ecolacteoshuata.db.Cambio_revision> = emptyList(),
    val prueba: PruebaConexion = PruebaConexion.Ninguna,
    val ultimaSincronizacionTexto: String? = null,
    val mensaje: String? = null,
) {
    /** Hay cambios en el formulario que todavía no se guardaron. */
    val sinGuardar: Boolean
        get() = host.trim() != guardada.host.trim() || puerto.trim() != guardada.puerto.trim() || activa != guardada.activa
    val puedeSincronizar: Boolean
        get() = guardada.activa && guardada.host.isNotBlank() && estado != EstadoSincronizacion.SINCRONIZANDO
}

/** Panel "Conexión y sincronización": configurar el servidor Laravel, probarlo y sincronizar. */
class ConexionSyncViewModel(
    private val conexionServidorRepository: ConexionServidorRepository,
    private val syncService: SyncService,
    private val db: com.example.ecolacteoshuata.db.AppDatabase,
    private val sesion: com.example.ecolacteoshuata.domain.repository.SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ConexionSyncUiState())
    val state: StateFlow<ConexionSyncUiState> = _state.asStateFlow()

    private var formularioCargado = false

    init {
        viewModelScope.launch {
            combine(db.autorizacionLocalQueries.revisiones().asFlow().mapToList(kotlinx.coroutines.Dispatchers.Default), sesion.usuarioActual) { filas, user ->
                val administracion = user != null && listOf("admin.roles", "admin.usuarios", "admin.asignar-roles").all { user.autorizacion?.permite(it) == true }
                filas.filter { (it.actor_id == user?.id && user != null) || administracion }
            }.collect { _state.value = _state.value.copy(revisiones = it) }
        }
        viewModelScope.launch {
            conexionServidorRepository.observe().collect { c ->
                val s = _state.value
                _state.value = s.copy(
                    guardada = c,
                    ultimaSincronizacionTexto = describirFecha(c.ultimaSincronizacion),
                ).let {
                    // Rellena el formulario una sola vez para no pisar lo que el usuario escribe.
                    if (formularioCargado) it else it.copy(host = c.host, puerto = c.puerto.ifBlank { "8000" }, activa = c.activa)
                }
                formularioCargado = true
            }
        }
        viewModelScope.launch {
            combine(syncService.estado, syncService.progreso, syncService.ultimoError, syncService.ultimoResumen, syncService.pendientes) {
                    estado, progreso, error, resumen, pendientes ->
                _state.value.copy(estado = estado, progreso = progreso, error = error, resumen = resumen, pendientes = pendientes)
            }.collect { _state.value = it }
        }
    }

    fun onHostChange(valor: String) {
        _state.value = _state.value.copy(host = valor, prueba = PruebaConexion.Ninguna)
    }

    fun onPuertoChange(valor: String) {
        _state.value = _state.value.copy(puerto = valor.filter { it.isDigit() }.take(5), prueba = PruebaConexion.Ninguna)
    }

    fun onActivaChange(valor: Boolean) {
        _state.value = _state.value.copy(activa = valor)
    }

    fun probar() {
        val s = _state.value
        _state.value = s.copy(prueba = PruebaConexion.Probando)
        viewModelScope.launch {
            val r = syncService.probarConexion(s.host.trim(), s.puerto.trim())
            _state.value = _state.value.copy(
                prueba = when (r) {
                    is AppResult.Success -> PruebaConexion.Exitosa(r.data)
                    is AppResult.Error -> PruebaConexion.Fallida(r.message)
                },
            )
        }
    }

    fun guardar() {
        val s = _state.value
        if (s.activa && s.host.isBlank()) {
            _state.value = s.copy(mensaje = "Ingresa la IP del servidor (ej. 192.168.1.10).")
            return
        }
        viewModelScope.launch {
            conexionServidorRepository.guardar(ConexionServidor(host = s.host.trim(), puerto = s.puerto.trim(), activa = s.activa))
            _state.value = _state.value.copy(
                mensaje = if (s.activa) "Conexión guardada." else "Modo local activado: la app usará solo los datos del teléfono.",
            )
            // Si ya había sincronizado con este mismo servidor, trae los cambios de una vez. Si
            // es un servidor nuevo, la pantalla de primera descarga se encarga de bajar todo.
            val guardada = conexionServidorRepository.get()
            if (s.activa && guardada.token == null) {
                _state.value = _state.value.copy(
                    mensaje = "Conexión guardada. Inicia sesión con conexión para descargar los datos del servidor.",
                )
            }
            if (s.activa && guardada.token != null && guardada.ultimaSincronizacion != null) syncService.sincronizarAhora()
        }
    }

    fun sincronizar() {
        viewModelScope.launch { syncService.sincronizarAhora() }
    }

    fun descargarTodo() {
        viewModelScope.launch { syncService.descargarTodoDeNuevo() }
    }

    /** Recalcula "hace X min" al abrir el panel. */
    fun refrescar() {
        val s = _state.value
        _state.value = s.copy(ultimaSincronizacionTexto = describirFecha(s.guardada.ultimaSincronizacion))
    }

    fun consumirMensaje() {
        _state.value = _state.value.copy(mensaje = null)
    }

    @OptIn(ExperimentalTime::class)
    private fun describirFecha(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val instante = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
        val minutos = (Clock.System.now() - instante).inWholeMinutes
        val relativo = when {
            minutos < 1 -> "hace un momento"
            minutos < 60 -> "hace $minutos min"
            minutos < 60 * 24 -> "hace ${minutos / 60} h"
            else -> "hace ${minutos / (60 * 24)} día(s)"
        }
        return "${instante.toLocalDateTime(TimeZone.currentSystemDefault()).formatDdMmmYyyyHm()} · $relativo"
    }
}
