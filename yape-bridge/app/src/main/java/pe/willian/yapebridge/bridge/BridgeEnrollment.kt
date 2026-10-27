package pe.willian.yapebridge.bridge

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import pe.willian.yapebridge.BuildConfig
import pe.willian.yapebridge.store.BridgeStore
import java.util.UUID
import java.util.concurrent.Executors

object BridgeEnrollment {
    private val executor=Executors.newSingleThreadExecutor()
    fun receive(context:Context,uri:Uri?) {
        if(uri?.scheme!="huatabridge" || uri.host!="enroll")return
        val server=CanonicalProtocol.secureUrl(uri.getQueryParameter("server").orEmpty(),BuildConfig.DEBUG) ?: return
        val ticket=uri.getQueryParameter("ticket")?.takeIf { it.matches(Regex("[a-zA-Z0-9]{64}")) } ?: return
        executor.execute {
            try {
                val prefs=context.getSharedPreferences("bridge_enrollment",Context.MODE_PRIVATE)
                val claim=prefs.getString("claim",null) ?: (UUID.randomUUID().toString()+UUID.randomUUID().toString()).also { prefs.edit().putString("claim",it).commit() }
                val body=JSONObject().put("ticket",ticket).put("claim",claim).toString()
                val response=BridgeHttpTransport(BuildConfig.DEBUG).post(server,"/api/bridge/enroll","",body)
                check(response.status in 200..299)
                val cfg=JSONObject(response.body)
                check(DeviceCredentials.save(cfg.getString("receiverId"),cfg.getString("credential")))
                DeviceCredentials.metadata(cfg.getString("deviceId"),cfg.getString("mode"))
                check(BackendSettings.save(context,server)==null)
                BackendBridge.configurationChanged()
            } catch (_:Exception) { BridgeStore.markHealthFailed("No se completó la vinculación. Abre un enlace vigente de Huata; se conservan los datos existentes.") }
        }
    }
}
