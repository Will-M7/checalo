package com.example.ecolacteoshuata.domain.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * ¿Hay red ahora? Android usa ConnectivityManager y iOS NWPathMonitor. Es una interfaz (y no
 * expect/actual) porque tiene estado, en Android necesita el Context y en las pruebas se
 * reemplaza por un falso.
 */
interface NetworkMonitor {
    val enLinea: StateFlow<Boolean>

    fun hayConexion(): Boolean = enLinea.value
}
