package com.example.ecolacteoshuata.data.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.ecolacteoshuata.domain.platform.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Red disponible según ConnectivityManager (permiso ACCESS_NETWORK_STATE). Laravel corre en la
 * red local, así que basta con que haya una red conectada (no se exige salida a Internet).
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _enLinea = MutableStateFlow(hayRedActiva())
    override val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _enLinea.value = true }
        override fun onLost(network: Network) { _enLinea.value = hayRedActiva() }
        override fun onUnavailable() { _enLinea.value = false }
    }

    init {
        connectivity?.registerDefaultNetworkCallback(callback)
    }

    private fun hayRedActiva(): Boolean {
        val cm = connectivity ?: return true
        val capacidades = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capacidades.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capacidades.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capacidades.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
