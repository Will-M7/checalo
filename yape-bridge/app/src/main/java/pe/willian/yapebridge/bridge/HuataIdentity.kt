package pe.willian.yapebridge.bridge

import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Signature-checked Binder IPC; never reads another app's storage or receives its mobile token. */
object HuataIdentity {
    @Volatile private var binder:IBinder?=null
    @Volatile private var binding=false
    private var latch=CountDownLatch(1)
    fun proof(context:Context,device:String,event:String):JSONObject? {
        val pkg="com.example.ecolacteoshuata.huata"
        if(context.packageManager.checkSignatures(context.packageName,pkg)!=PackageManager.SIGNATURE_MATCH)return null
        synchronized(this) {
            if(binder==null && !binding) {
                binding=true;latch=CountDownLatch(1)
                val connection=object:ServiceConnection {
                    override fun onServiceConnected(name:ComponentName,service:IBinder) { binder=service;binding=false;latch.countDown() }
                    override fun onServiceDisconnected(name:ComponentName) { binder=null;binding=false }
                    override fun onNullBinding(name:ComponentName) { binder=null;binding=false;latch.countDown() }
                }
                val intent=Intent().setComponent(ComponentName(pkg,"com.example.ecolacteoshuata.BridgeIdentityService"))
                if(!runCatching { context.bindService(intent,connection,Context.BIND_AUTO_CREATE) }.getOrDefault(false)) { binding=false;latch.countDown() }
            }
        }
        if(binder==null)latch.await(3,TimeUnit.SECONDS)
        val service=binder ?: return null
        val data=Parcel.obtain();val reply=Parcel.obtain()
        return try {
            val nonce=UUID.randomUUID().toString()+UUID.randomUUID().toString()
            data.writeInterfaceToken("huata.bridge.session.v1");data.writeString(device);data.writeString(nonce);data.writeString(event)
            if(!service.transact(1,data,reply,0))return null
            reply.readException();val raw=reply.readString().orEmpty()
            if(raw.isBlank())null else JSONObject(raw).takeIf { it.optString("nonce")==nonce && it.optString("proof").length==64 }
        } catch (_:Exception) { null } finally { data.recycle();reply.recycle() }
    }
}
class HuataSessionChangedReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) { BackendBridge.heartbeatAsync();BackendBridge.requestDrain() }
}
