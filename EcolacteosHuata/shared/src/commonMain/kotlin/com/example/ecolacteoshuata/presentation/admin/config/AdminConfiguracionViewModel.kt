package com.example.ecolacteoshuata.presentation.admin.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.domain.model.Empresa
import com.example.ecolacteoshuata.domain.repository.EmpresaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminConfiguracionViewModel(private val empresaRepository: EmpresaRepository) : ViewModel() {
    val empresa: StateFlow<Empresa?> = empresaRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _guardadoOk = MutableStateFlow(false)
    val guardadoOk: StateFlow<Boolean> = _guardadoOk

    fun guardar(razonSocial: String, ruc: String, telefono: String, direccion: String) {
        viewModelScope.launch {
            empresaRepository.actualizar(
                Empresa(id = empresa.value?.id ?: IdGenerator.newId(), razonSocial = razonSocial, ruc = ruc, telefono = telefono, direccion = direccion)
            )
            _guardadoOk.value = true
        }
    }

    fun confirmacionConsumida() { _guardadoOk.value = false }
}
