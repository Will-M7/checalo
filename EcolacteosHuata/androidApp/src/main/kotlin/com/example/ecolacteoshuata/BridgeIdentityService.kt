package com.example.ecolacteoshuata

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.repository.SessionRepository
import com.example.ecolacteoshuata.data.remote.ApiConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import org.koin.core.context.GlobalContext

/** Signature-protected IPC. Returns only a short-lived backend proof, never the mobile bearer. */
class BridgeIdentityService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            GlobalContext.get().get<SessionRepository>().usuarioActual.drop(1).collect {
                for (pkg in listOf("pe.willian.yapebridge","pe.willian.yapebridge.debug"))
                    sendBroadcast(Intent("huata.bridge.SESSION_CHANGED").setPackage(pkg),"com.example.ecolacteoshuata.BRIDGE_SESSION")
            }
        }
    }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
    override fun onBind(intent:Intent):IBinder=object:Binder() {
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            if(code!=1)return super.onTransact(code,data,reply,flags)
            data.enforceInterface("huata.bridge.session.v1")
            val uid=getCallingUid()
            val packages=packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
            if(packages.none { it in setOf("pe.willian.yapebridge","pe.willian.yapebridge.debug") } || packageManager.checkSignatures(uid,applicationInfo.uid)!=PackageManager.SIGNATURE_MATCH) throw SecurityException("Untrusted caller")
            val device=data.readString().orEmpty();val nonce=data.readString().orEmpty();val event=data.readString().orEmpty()
            require(device.length in 1..100 && nonce.length in 32..128 && event.length in 1..128)
            val result=runCatching { runBlocking(Dispatchers.IO) { withTimeout(8000) {
                GlobalContext.get().get<com.example.ecolacteoshuata.data.remote.BridgeSessionAssertion>().proof(device,nonce,event)
            } } }.getOrDefault("")
            reply?.writeNoException();reply?.writeString(result)
            return true
        }
    }
}
