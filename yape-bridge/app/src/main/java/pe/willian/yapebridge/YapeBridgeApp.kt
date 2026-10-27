package pe.willian.yapebridge

import android.app.Application
import pe.willian.yapebridge.bridge.BackendSettings
import pe.willian.yapebridge.bridge.BackendBridge

/**
 * Inicializa la configuración local del LAB antes de que existan
 * Activities o servicios (así el NotificationListener ya lee la URL
 * guardada y no el default).
 */
class YapeBridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        pe.willian.yapebridge.bridge.DeviceCredentials.init(this)
        BackendSettings.init(this)
        BackendBridge.init(this)
    }
}
