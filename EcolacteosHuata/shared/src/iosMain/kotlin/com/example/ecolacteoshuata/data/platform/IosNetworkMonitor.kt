package com.example.ecolacteoshuata.data.platform

import com.example.ecolacteoshuata.domain.platform.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/** Red disponible según NWPathMonitor (framework Network): `satisfied` = hay red. */
class IosNetworkMonitor : NetworkMonitor {
    private val _enLinea = MutableStateFlow(true)
    override val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { ruta ->
            _enLinea.value = nw_path_get_status(ruta) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}
