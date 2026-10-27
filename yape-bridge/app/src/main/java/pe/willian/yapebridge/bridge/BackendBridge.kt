package pe.willian.yapebridge.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import pe.willian.yapebridge.BuildConfig
import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.store.*

/** Un solo ingreso: Huata autentica mediante el Core privado; un ACK no acredita dinero. */
object BackendBridge {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var appContext: Context? = null
    private val transport = BridgeHttpTransport(allowHttp = BuildConfig.DEBUG)

    @Synchronized fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        runCatching { OutboxDatabase.get(context).recentSummaries().asReversed().forEach(NotificationStore::push) }
        val periodic = PeriodicWorkRequestBuilder<OutboxWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("yape-bridge-outbox-safety", ExistingPeriodicWorkPolicy.KEEP, periodic)
        requestDrain()
        // WorkManager persists through process death/reboot; this callback accelerates a live reconnect.
        runCatching {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { requestDrain(); heartbeatAsync() }
                })
        }
        executor.scheduleWithFixedDelay({ runCatching { heartbeatBlocking(); refreshQueue(); if (BridgeStore.pendingCount.value > 0) requestDrain() } }, 0, 30, TimeUnit.SECONDS)
    }

    private fun networkConstraint() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun requestDrain() {
        val context = appContext ?: return
        val work = OneTimeWorkRequestBuilder<OutboxWorker>().setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("yape-bridge-outbox", ExistingWorkPolicy.KEEP, work)
    }
    fun refreshQueue() {
        val context = appContext ?: return
        runCatching { OutboxDatabase.get(context).counts().also { BridgeStore.queueCounts(it.first, it.second) } }
            .onFailure { BridgeStore.markSendFailed(null, "No se pudo consultar la cola. Conserva los datos y revisa el dispositivo.") }
    }
    fun configurationChanged() {
        BridgeStore.resetHealth()
        refreshQueue()
        requestDrain()
        heartbeatAsync()
    }
    fun heartbeatAsync() { executor.execute { heartbeatBlocking() } }
    private fun identity(event:String):JSONObject? {
        if(DeviceCredentials.mode!="personal")return null
        val context=appContext ?: return null
        val proof=HuataIdentity.proof(context,DeviceCredentials.deviceId,event) ?: return null
        val server=CanonicalProtocol.secureUrl(proof.optString("backendUrl"),BuildConfig.DEBUG)
        if(server!=null && server!=BackendSettings.baseUrl.value)BackendSettings.save(context,server)
        return proof
    }
    private fun heartbeatBlocking() {
        if (BackendSettings.baseUrl.value.isBlank() || DeviceCredentials.key.isBlank()) {
            BridgeStore.markHealthFailed("Configura el servidor Huata y vincula la credencial del dispositivo.")
            return
        }
        try {
            val proof=identity("heartbeat")
            val body=JSONObject().put("listenerConnected",NotificationStore.listenerConnected.value)
            proof?.let { body.put("identityProof",it.optString("proof")).put("identityNonce",it.optString("nonce")) }
            val response = transport.post(BackendSettings.baseUrl.value, BridgeConfig.HEALTH_PATH, DeviceCredentials.key,body.toString())
            if (response.status !in 200..299) { BridgeStore.markHealthFailed(errorFor(response.status)); return }
            val ack = JSONObject(response.body)
            if (ack.opt("accepted") != true || ack.opt("coreMode") != "commercial" || ack.opt("receiptOnly") != true) {
                BridgeStore.markHealthFailed("El endpoint no es el ingreso comercial de Huata. Revisa la URL y el puerto.")
            } else BridgeStore.markHealthOk()
        } catch (_: Exception) { BridgeStore.markHealthFailed("Sin conexión autenticada. Revisa red, URL, protocolo y disponibilidad de Huata.") }
    }
    fun sendAsync(notification: CapturedNotification) {
        val context = appContext ?: return
        if (DeviceCredentials.receiverId.isBlank()) {
            BridgeStore.markSendFailed(notification.packageName, "Vincula primero el receptor autorizado; no se atribuyen eventos a una cuenta desconocida.")
            return
        }
        val event = runCatching { OutboxDatabase.get(context).insert(notification) }.getOrElse {
            BridgeStore.markSendFailed(notification.packageName, "No se pudo conservar el evento cifrado; requiere revisión")
            return
        }
        refreshQueue()
        requestDrain()
        executor.execute { trySend(event) }
    }
    internal fun trySend(event: BridgeOutboxEvent): Boolean {
        val db = OutboxDatabase.get(appContext ?: return false)
        val credential = DeviceCredentials.key
        val proof=identity(event.eventId)
        if(DeviceCredentials.mode=="personal" && proof==null) {
            heartbeatBlocking()
            BridgeStore.markSendFailed(event.packageName,"Sesión Huata no compatible o no disponible. Evento conservado para reintento.")
            return false
        }
        val base = BackendSettings.baseUrl.value
        var review=false
        var reason = "Sin ACK válido; evento conservado para reintento."
        val delivered = CanonicalDelivery.deliver(event, DeviceCredentials.receiverId, credential,
            markAttempt = { db.markAttempt(it) }, transmit = { body ->
                val payload=JSONObject(body)
                proof?.let { payload.put("identityProof",it.optString("proof")).put("identityNonce",it.optString("nonce")) }
                val response = transport.post(base, BridgeConfig.NOTIFICATION_PATH, credential, payload.toString())
                if (response.status !in 200..299) reason = errorFor(response.status)
                val ack = JSONObject(response.body)
                review=ack.optBoolean("reviewRequired",false) || ack.optString("result").startsWith("REVIEW_")
                DeliveryAck(response.status, ack.opt("accepted"), ack.opt("eventId"))
            }, markSent = { if(review)db.markReview(it) else db.markSent(it) })
        if (delivered) BridgeStore.markSendOk(event.packageName)
        else BridgeStore.markSendFailed(event.packageName, reason)
        refreshQueue()
        return delivered
    }
    fun testConnection() { BridgeStore.markHealthConnecting(); heartbeatAsync(); requestDrain() }
    private fun errorFor(status: Int) = when (status) {
        401 -> "Credencial de dispositivo rechazada; revisa la vinculación. La cola se conserva."
        403 -> "Acceso o transporte rechazado. Revisa HTTPS y configuración privada."
        400, 409, 422 -> "Evento o receptor rechazado; requiere revisión. La cola se conserva."
        404 -> "No existe el ingreso Bridge de Huata en esa URL. Revisa servidor y puerto."
        429 -> "Límite temporal del servidor; se reintentará sin borrar pendientes."
        503 -> "Vinculación comercial pendiente o motor no disponible. La cola se conserva."
        else -> "Sin confirmación válida del servidor (HTTP $status). La cola se conserva."
    }
}
