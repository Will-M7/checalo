package com.example.ecolacteoshuata.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class AppNavController(inicial: Screen) {
    var pila by mutableStateOf(listOf(inicial))
        private set

    val actual: Screen get() = pila.last()
    val puedeVolver: Boolean get() = pila.size > 1

    fun navegar(screen: Screen) {
        pila = pila + screen
    }

    fun volver() {
        if (puedeVolver) pila = pila.dropLast(1)
    }

    fun irARaiz(screen: Screen) {
        pila = listOf(screen)
    }
}

@Composable
fun rememberAppNavController(inicial: Screen): AppNavController = remember { AppNavController(inicial) }
