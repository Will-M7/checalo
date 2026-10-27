package com.example.ecolacteoshuata.domain.repository

import com.example.ecolacteoshuata.domain.model.Usuario
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val usuarioActual: StateFlow<Usuario?>
    fun setUsuario(usuario: Usuario?)
}
