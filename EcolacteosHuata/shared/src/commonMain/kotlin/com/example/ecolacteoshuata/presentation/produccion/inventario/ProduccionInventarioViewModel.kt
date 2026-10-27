package com.example.ecolacteoshuata.presentation.produccion.inventario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.domain.model.InventarioInsumoItem
import com.example.ecolacteoshuata.domain.usecase.GetInventarioInsumosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProduccionInventarioViewModel(
    private val getInventarioInsumosUseCase: GetInventarioInsumosUseCase,
) : ViewModel() {
    private val _items = MutableStateFlow<List<InventarioInsumoItem>>(emptyList())
    val items: StateFlow<List<InventarioInsumoItem>> = _items.asStateFlow()

    init { cargar() }

    fun cargar() {
        viewModelScope.launch { _items.value = getInventarioInsumosUseCase() }
    }
}
