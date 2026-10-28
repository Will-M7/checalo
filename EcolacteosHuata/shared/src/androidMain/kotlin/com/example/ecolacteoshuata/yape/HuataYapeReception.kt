package com.example.ecolacteoshuata.yape

import android.content.Context
import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.ecolacteoshuata.data.remote.ApiConfig
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.platform.*
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.yape.bridge.*
import com.example.ecolacteoshuata.yape.model.CapturedNotification
import com.example.ecolacteoshuata.yape.store.OutboxDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Evidence must stay on the configured Laravel endpoint, including when a server returns a redirect. */
internal fun HttpClient.yapeTransport(): HttpClient = config { followRedirects = false }

private data class YapeSession(val user: String, val token: String, val base: String) {
    val scope: String get() = MessageDigest.getInstance("SHA-256").digest("$base|$user".toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Android-only adapter. Capture and encryption are reused; every transmission uses live Huata authorization. */
class HuataYapeReception(private val context: Context, private val client: HttpClient, private val config: ApiConfig,
    private val connection: ConexionServidorRepository, private val db: AppDatabase, private val users: com.example.ecolacteoshuata.domain.repository.SessionRepository) : YapeReception {
    private val transport = client.yapeTransport()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val network = Mutex()
    private val captureLock = Any()
    private val _state = MutableStateFlow(YapeReceptionState())
    override val state = _state.asStateFlow()
    private var heartbeatJob: Job? = null
    @Volatile var connected = false
    @Volatile private var boundScope = ""
    @Volatile private var authorizationAt = 0L
    @Volatile private var remoteStatus = ""
    init { DeviceCredentials.init(context) }
    fun start() {
        val request = PeriodicWorkRequestBuilder<HuataYapeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("huata-yape-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
        appScope.launch { org.koin.core.context.GlobalContext.get().get<SyncService>().estado.collect {
            if (it == EstadoSincronizacion.SINCRONIZADO) refresh()
        } }
        appScope.launch { users.usuarioActual.collectLatest {
            synchronized(captureLock) { boundScope=""; _state.value=YapeReceptionState() }
            runOnce(false); requestDrain()
        } }
    }
    private suspend fun session(): YapeSession? {
        val id = db.autorizacionLocalQueries.sesionActual().executeAsOneOrNull() ?: return null
        val user = db.usuarioQueries.selectById(id).executeAsOneOrNull() ?: return null
        if (user.activo != 1L) return null
        val c = connection.get()
        val token = c.token?.takeIf { it.isNotBlank() } ?: return null
        if (!c.activa) return null
        val mobile = config.baseUrl(c.host, c.puerto)
        val base = CanonicalProtocol.secureUrl(mobile.removeSuffix("api/mobile/"), (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)) ?: return null
        return YapeSession(id, token, base.trimEnd('/'))
    }
    private fun permission(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 27) (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .isNotificationListenerAccessGranted(ComponentName(context, HuataYapeListener::class.java))
        else NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)
    private suspend fun post(s: YapeSession, path: String, body: JsonObject): Pair<Int, JsonObject> {
        val r = transport.post(s.base + "/api/mobile/yape/" + path) {
            bearerAuth(s.token); header("X-Huata-Installation", DeviceCredentials.installation)
            contentType(ContentType.Application.Json); setBody(body)
        }
        check(session()?.let { it.scope == s.scope && it.token == s.token } == true)
        val text = r.bodyAsText(); check(text.length <= 32768)
        return r.status.value to (runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: buildJsonObject {})
    }
    private fun failure(status: Int): String = when(status) {
        401,403 -> "La sesión o el dispositivo no están autorizados. La cola se conserva."
        409,422 -> "El servidor requiere revisar la vinculación o la transferencia del Bridge anterior. La cola se conserva."
        else -> "Sin conexión o sin confirmación del servidor. La cola se conserva."
    }
    private fun apply(s: YapeSession, data: JsonObject, at: Long = System.currentTimeMillis(), online: Boolean = true) = synchronized(captureLock) {
        val authorized = data["authorized"]?.jsonPrimitive?.booleanOrNull == true
        boundScope = s.scope; remoteStatus = data["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        authorizationAt = at
        DeviceCredentials.binding = s.scope
        if (authorized) DeviceCredentials.receiver(data["receiverId"]?.jsonPrimitive?.contentOrNull.orEmpty())
        DeviceCredentials.cache(s.scope, buildJsonObject { put("at", authorizationAt); put("data", data) }.toString())
        val counts = if (authorized && DeviceCredentials.receiverId.isNotBlank()) OutboxDatabase.get(context).counts() else (0 to 0)
        if (authorized && data["serverAccepted"]?.jsonPrimitive?.booleanOrNull == true) DeviceCredentials.enabled(s.scope,true)
        val enabled = DeviceCredentials.enabled(s.scope)
        _state.value = YapeReceptionState(authorized, when {
            !authorized -> "Desactivada"
            !permission() -> "Aceptado, pendiente de permiso Android"
            enabled && connected && remoteStatus == "active" -> "Activa"
            remoteStatus == "authorized" -> "Pendiente de vinculación técnica"
            else -> "Listener pendiente"
        }, data["receiver"]?.jsonPrimitive?.contentOrNull.orEmpty(), permission(), connected, enabled,
            data["lastCommunication"]?.jsonPrimitive?.contentOrNull, counts.first, counts.second,
            busy = _state.value.busy, online = online, phone = data["phone"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            requests = data["requests"]?.jsonArray?.map { it.jsonObject.let { q ->
                YapeReceptionRequest(q.getValue("id").jsonPrimitive.content, q.getValue("name").jsonPrimitive.content,
                    q.getValue("phone").jsonPrimitive.content, q.getValue("qrUrl").jsonPrimitive.content)
            } } ?: emptyList(),
        )
        updateDiagnostics()
    }
    override fun refresh() { appScope.launch { runCatching { runOnce(false) } } }
    private suspend fun refreshStatus(s: YapeSession): Boolean {
        val (status, result) = post(s,"status",buildJsonObject {
            put("installation",DeviceCredentials.installation); put("label", (Build.MANUFACTURER+" "+Build.MODEL).take(100))
            put("permission",permission()); put("listener",connected && permission()); put("consent",DeviceCredentials.enabled(s.scope))
        })
        if (status !in 200..299) {
            if (status in listOf(401,403)) { apply(s,buildJsonObject { put("authorized",false) }); _state.value=_state.value.copy(error=failure(status)) }
            else _state.value = _state.value.copy(status="Sin conexión",error=failure(status),online=false)
            return false
        }
        apply(s,result); return _state.value.authorized
    }
    override suspend fun qr(request: YapeReceptionRequest): ByteArray? {
        val s = session() ?: return null
        return loadReceptionQr(transport, s.base, request.qrUrl, s.token)
    }
    override fun decide(requestId: String, accept: Boolean) { appScope.launch {
        if (_state.value.busy) return@launch
        _state.value = _state.value.copy(busy=true,error=null)
        try {
            val s = session() ?: return@launch
            val saved = network.withLock {
                val (code, result) = post(s,"decision",buildJsonObject {
                    put("installation",DeviceCredentials.installation); put("requestId",requestId); put("accept",accept)
                })
                if (code !in 200..299 || result["saved"]?.jsonPrimitive?.booleanOrNull != true) {
                    _state.value = _state.value.copy(error=failure(code),online=code in listOf(409,422))
                    false
                } else true
            }
            if (!saved && _state.value.online) runOnce(false)
            if (saved) {
                if (accept) DeviceCredentials.enabled(s.scope,true)
                runOnce(false)
                if (accept && !permission()) permissions()
                requestDrain()
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _state.value = _state.value.copy(error="No se confirmó la decisión. Se consultará al reconectar.",online=false) }
        finally { _state.value = _state.value.copy(busy=false) }
    } }
    private fun updateDiagnostics() {
        if (!_state.value.authorized || DeviceCredentials.binding.isBlank()) return
        val diagnostics = OutboxDatabase.get(context).diagnostics()
        _state.value = _state.value.copy(sent=diagnostics.sent, recoverable=diagnostics.recoverable,
            recent=diagnostics.recent)
    }
    override fun activate() { appScope.launch {
        val s = session() ?: return@launch
        DeviceCredentials.enabled(s.scope,true)
        if (!permission()) { permissions(); refresh(); return@launch }
        runCatching { runOnce(true) }.onFailure { _state.value = _state.value.copy(busy=false,status="Sin conexión",error="No se recibió autorización del servidor. La cola se conserva.") }
    } }
    override fun pause() { appScope.launch {
        session()?.let { s -> DeviceCredentials.enabled(s.scope,false); _state.value = _state.value.copy(enabled=false,status="Desactivada"); runCatching { runOnce(false) } }
    } }
    override fun permissions() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { _state.value = _state.value.copy(error="Abre Ajustes de Android → Acceso a notificaciones → Huata.") }
    }
    fun listenerChanged(value: Boolean) {
        connected = value
        heartbeatJob?.cancel()
        // Core exige comunicación reciente. El trabajo periódico de rescate sigue en 15 minutos.
        // No se mantiene despierto el teléfono ni se fuerza el servicio si Android lo detiene.
        if (value) heartbeatJob = appScope.launch {
            while (isActive && connected) {
                delay(60_000)
                if (_state.value.authorized && _state.value.enabled) runOnce(false)
            }
        }
        refresh(); requestDrain()
    }
    fun requestDrain() {
        val request = OneTimeWorkRequestBuilder<HuataYapeWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("huata-yape-outbox",ExistingWorkPolicy.KEEP,request)
    }
    private fun captureAuthorized(s: YapeSession): Boolean {
        if (boundScope != s.scope) {
            val cached = DeviceCredentials.cached(s.scope)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            val at = cached?.get("at")?.jsonPrimitive?.longOrNull ?: 0L
            if (System.currentTimeMillis() - at > 24*60*60*1000L) return false
            cached?.get("data")?.jsonObject?.let { apply(s,it,at,online=false) }
        }
        return ReceptionCaptureGate.allowed(_state.value.authorized, DeviceCredentials.enabled(s.scope), permission(), s.scope, boundScope, authorizationAt, System.currentTimeMillis())
    }
    fun mayCapture(): Boolean {
        val s = runBlocking(Dispatchers.IO) { session() } ?: return false
        return synchronized(captureLock) { captureAuthorized(s) }
    }
    /** No network IO here. The encrypted disk record precedes dispatch. */
    fun capture(n: CapturedNotification) {
        val s = runBlocking(Dispatchers.IO) { session() } ?: return
        synchronized(captureLock) {
            if (!captureAuthorized(s)) return
            if (!CanonicalProtocol.capture(n.packageName,listOf(n.title,n.text,n.bigText,n.subText,n.summaryText))) return
            DeviceCredentials.binding = s.scope
            OutboxDatabase.get(context).insert(n)
            val counts = OutboxDatabase.get(context).counts()
            _state.value = _state.value.copy(pending=counts.first,review=counts.second)
            updateDiagnostics()
        }
        requestDrain()
    }
    suspend fun runOnce(activate: Boolean = false): Boolean = network.withLock {
        val s = session() ?: run { synchronized(captureLock) { boundScope=""; _state.value=YapeReceptionState() }; return@withLock true }
        _state.value = _state.value.copy(busy=true)
        try {
            synchronized(captureLock) { captureAuthorized(s) }
            if (!refreshStatus(s)) return@withLock _state.value.status != "Sin conexión"
            if (!DeviceCredentials.enabled(s.scope)) return@withLock true
            if (permission() && !connected) runCatching { android.service.notification.NotificationListenerService.requestRebind(ComponentName(context,HuataYapeListener::class.java)) }
            if (remoteStatus == "authorized" && connected && permission()) {
                val (code,data) = post(s,"activate",buildJsonObject { put("installation",DeviceCredentials.installation) })
                if (code !in 200..299) { _state.value=_state.value.copy(error=failure(code)); return@withLock false }
                apply(s,data)
            }
            if (remoteStatus != "active") return@withLock false
            val (health,ack) = post(s,"heartbeat",buildJsonObject { put("listenerConnected",connected && permission()) })
            if (health !in 200..299 || ack["accepted"]?.jsonPrimitive?.booleanOrNull != true || ack["coreMode"]?.jsonPrimitive?.contentOrNull != "commercial" || ack["receiptOnly"]?.jsonPrimitive?.booleanOrNull != true) {
                if (health in listOf(401,403)) apply(s,buildJsonObject { put("authorized",false) })
                _state.value=_state.value.copy(status="Sin conexión",error=failure(health)); return@withLock false
            }
            val outbox = synchronized(captureLock) { DeviceCredentials.binding=s.scope; OutboxDatabase.get(context) }
            _state.value = _state.value.copy(lastCommunication=java.time.Instant.now().toString())
            var complete=true
            for (e in outbox.pending().take(50)) {
                if (session()?.scope != s.scope || !DeviceCredentials.enabled(s.scope)) return@withLock true
                outbox.markAttempt(e.eventId)
                val (code,result)=post(s,"evidence",Json.parseToJsonElement(CanonicalProtocol.json(e)).jsonObject)
                if (CanonicalDelivery.accepts(e.eventId, DeliveryAck(code,result["accepted"]?.jsonPrimitive?.booleanOrNull,result["eventId"]?.jsonPrimitive?.contentOrNull)) && result["receiptOnly"]?.jsonPrimitive?.booleanOrNull == true && result["coreMode"]?.jsonPrimitive?.contentOrNull == "commercial" && result["eventId"]?.jsonPrimitive?.contentOrNull == e.eventId) {
                    if (result["reviewRequired"]?.jsonPrimitive?.booleanOrNull == true || result["result"]?.jsonPrimitive?.contentOrNull.orEmpty().startsWith("REVIEW_")) outbox.markReview(e.eventId) else outbox.markSent(e.eventId)
                } else {
                    if (code in listOf(401,403)) { apply(s,buildJsonObject { put("authorized",false) }); _state.value=_state.value.copy(error=failure(code)); return@withLock true }
                    _state.value=_state.value.copy(error=failure(code))
                    if (code in listOf(400,409,413,415,422)) { outbox.markLocalReview(e.eventId); continue }
                    complete=false; break
                }
            }
            val counts=outbox.counts()
            _state.value=_state.value.copy(lastCommunication=java.time.Instant.now().toString(),pending=counts.first,review=counts.second)
            updateDiagnostics()
            complete && outbox.pending().isEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _state.value=_state.value.copy(status="Sin conexión",error="No se recibió confirmación. Los eventos pendientes permanecen cifrados.",online=false); false }
        finally { _state.value = _state.value.copy(busy=false) }
    }
}

class HuataYapeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val runtime = org.koin.core.context.GlobalContext.get().get<YapeReception>() as? HuataYapeReception ?: return@withContext Result.success()
        if (runtime.runOnce()) Result.success() else Result.retry()
    }
}
