package com.example.ecolacteoshuata.presentation.admin.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.usecase.EliminarUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveUsuariosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminUsuariosViewModel(
    observeUsuariosUseCase: ObserveUsuariosUseCase,
    private val eliminarUsuarioUseCase: EliminarUsuarioUseCase,
) : ViewModel() {
    val usuarios: StateFlow<List<Usuario>> = observeUsuariosUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun eliminar(id: String) {
        viewModelScope.launch { eliminarUsuarioUseCase(id) }
    }
}
