package com.example.ecolacteoshuata.data.repository

import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionRepositoryImpl(private val db: com.example.ecolacteoshuata.db.AppDatabase) : SessionRepository {
    private val _usuarioActual = MutableStateFlow<Usuario?>(null)
    override val usuarioActual: StateFlow<Usuario?> = _usuarioActual.asStateFlow()

    override fun setUsuario(usuario: Usuario?) {
        db.autorizacionLocalQueries.sesion(usuario?.id)
        _usuarioActual.value = usuario
    }
}
