package com.example.ecolacteoshuata.presentation.proveedor.comunicados

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.Comunicado
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.TipoComunicado
import com.example.ecolacteoshuata.domain.usecase.ObserveComunicadosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ProveedorComunicadosViewModel(observeComunicadosUseCase: ObserveComunicadosUseCase) : ViewModel() {
    /** "Filtrar por tipo" de Laravel; `null` = todos. */
    private val _filtroTipo = MutableStateFlow<TipoComunicado?>(null)
    val filtroTipo: StateFlow<TipoComunicado?> = _filtroTipo.asStateFlow()

    // Solo los dirigidos a proveedores o a todos los roles (`Comunicado::visibleParaRol` en Laravel).
    val comunicados: StateFlow<List<Comunicado>> = combine(observeComunicadosUseCase(), _filtroTipo) { lista, tipo ->
        lista.filter { it.visibleParaRol(Rol.PROVEEDOR) && (tipo == null || it.tipo == tipo) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun filtrar(tipo: TipoComunicado?) { _filtroTipo.value = tipo }
}
