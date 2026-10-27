package com.example.ecolacteoshuata.presentation.login

import com.example.ecolacteoshuata.domain.model.ConexionServidor
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import com.example.ecolacteoshuata.domain.model.Usuario

data class LoginUiState(
    val usuario: String = "",
    val password: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val usuarioLogueado: Usuario? = null,
    val estadoSync: EstadoSincronizacion = EstadoSincronizacion.SIN_SENAL,
    val pendientes: Int = 0,
    val conexion: ConexionServidor = ConexionServidor(),
)
