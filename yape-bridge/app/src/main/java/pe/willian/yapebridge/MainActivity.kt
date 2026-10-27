package pe.willian.yapebridge

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.bridge.BackendBridge
import pe.willian.yapebridge.bridge.BackendSettings
import pe.willian.yapebridge.bridge.DeviceCredentials
import androidx.compose.ui.text.input.PasswordVisualTransformation
import pe.willian.yapebridge.bridge.BridgeConfig
import pe.willian.yapebridge.store.BridgeStatus
import pe.willian.yapebridge.store.BridgeStore
import pe.willian.yapebridge.store.HealthStatus
import pe.willian.yapebridge.store.NotificationStore
import pe.willian.yapebridge.ui.ThemeYapeBridgeLab
import pe.willian.yapebridge.util.NotificationAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        pe.willian.yapebridge.bridge.BridgeEnrollment.receive(this,intent.data)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pe.willian.yapebridge.bridge.BridgeEnrollment.receive(this,intent?.data)
        setContent {
            ThemeYapeBridgeLab {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LabScreen(
                        onOpenSettings = {
                            startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LabScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessEnabled by remember { mutableStateOf(false) }
    val notifications by NotificationStore.notifications.collectAsState()
    val listenerConnected by NotificationStore.listenerConnected.collectAsState()

    val bridgeStatus by BridgeStore.status.collectAsState()
    val lastPackageSent by BridgeStore.lastPackageSent.collectAsState()
    val lastAttemptPackage by BridgeStore.lastAttemptPackage.collectAsState()
    val lastSendAt by BridgeStore.lastSendAt.collectAsState()
    val lastBridgeError by BridgeStore.lastError.collectAsState()

    // Tarjeta BACKEND: URL configurable + health check.
    val savedBaseUrl by BackendSettings.baseUrl.collectAsState()
    val pendientes by BridgeStore.pendingCount.collectAsState()
    val revision by BridgeStore.reviewCount.collectAsState()
    val healthStatus by BridgeStore.healthStatus.collectAsState()
    val healthError by BridgeStore.healthError.collectAsState()
    // Se reinicia solo cuando cambia la URL guardada (tras GUARDAR).
    var urlInput by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var receiverInput by remember { mutableStateOf(DeviceCredentials.receiverId) }
    var credentialInput by remember { mutableStateOf("") }

    fun refreshAccess() {
        accessEnabled = NotificationAccess.isEnabled(context)
    }

    // Re-evaluar el acceso cada vez que la pantalla vuelve a primer plano
    // (p. ej. al regresar desde Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "YAPE BRIDGE",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        StatusRow(
            accessEnabled = accessEnabled,
            listenerConnected = listenerConnected,
            bridgeStatus = bridgeStatus,
            lastPackageSent = lastPackageSent,
            lastAttemptPackage = lastAttemptPackage,
            lastSendAt = lastSendAt,
            lastBridgeError = lastBridgeError
        )

        BackendCard(
            urlInput = urlInput,
            onUrlChange = { value ->
                urlInput = value
                urlError = null
            },
            urlError = urlError,
            healthStatus = healthStatus,
            healthError = healthError,
            onSave = {
                val saved = BackendSettings.save(context, urlInput)
                if (saved == null) {
                    urlError = "Usa una URL completa de Huata (protocolo, IP/hostname y puerto). HTTP solo en compilaciones debug; release requiere HTTPS."
                } else {
                    urlError = null
                    urlInput = saved
                    // La URL cambió: el estado de salud anterior ya no aplica.
                    BackendBridge.configurationChanged()
                }
            },
            onTest = { BackendBridge.testConnection() }
        )

        Text("Pendientes de envío: $pendientes · Otros receptores / revisión: $revision", style = MaterialTheme.typography.bodySmall)
        Text("Un evento recibido no confirma dinero. Huata consulta el estado conciliado por Payment Core.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value=receiverInput,onValueChange={ receiverInput=it },label={ Text("Referencia del receptor autorizado") },singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=credentialInput,onValueChange={ credentialInput=it },label={ Text("Credencial del dispositivo (se guarda cifrada)") },singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(onClick={
            if(DeviceCredentials.save(receiverInput.trim(),credentialInput)) { credentialInput=""; BackendBridge.configurationChanged(); BackendBridge.testConnection() }
            else urlError="El receptor y una credencial técnica válida son obligatorios"
        }) { Text("Vincular dispositivo") }

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir configuración de acceso a notificaciones")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Últimas notificaciones (${notifications.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = { NotificationStore.clear() }) {
                Text("Limpiar registros")
            }
        }

        if (notifications.isEmpty()) {
            Card(colors = CardDefaults.cardColors()) {
                Text(
                    text = "Sin registros. Concede el acceso y espera una notificación. " +
                        "Se registrarán localmente (máx. 30).",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                notifications.forEach { item ->
                    NotificationCard(item)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    accessEnabled: Boolean,
    listenerConnected: Boolean,
    bridgeStatus: BridgeStatus,
    lastPackageSent: String?,
    lastAttemptPackage: String?,
    lastSendAt: Long?,
    lastBridgeError: String?
) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Notification access: " + if (accessEnabled) "ENABLED" else "DISABLED",
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (listenerConnected) "LISTENER ACTIVE" else "LISTENER INACTIVE",
                color = if (listenerConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Transmisión de eventos: " + bridgeStatus.name,
                color = when (bridgeStatus) {
                    BridgeStatus.LAST_SEND_OK -> MaterialTheme.colorScheme.primary
                    BridgeStatus.SEND_FAILED -> MaterialTheme.colorScheme.error
                    BridgeStatus.CONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold
            )
            // Solo el package name: nunca el contenido de la notificación.
            Text(
                text = "Last package sent: " + (lastPackageSent ?: "—"),
                style = MaterialTheme.typography.bodyMedium
            )
            if (bridgeStatus == BridgeStatus.SEND_FAILED) {
                Text(
                    text = "Último intento fallido: " + (lastAttemptPackage ?: "—") +
                        (lastSendAt?.let { " @ " + formatTime(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                lastBridgeError?.let {
                    Text(
                        text = "Error: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (lastSendAt != null) {
                Text(
                    text = "Último envío: " + formatTime(lastSendAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tarjeta BACKEND: URL del backend editable (persistida en local),
 * botones GUARDAR / PROBAR CONEXIÓN y estado del health check.
 */
@Composable
private fun BackendCard(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    urlError: String?,
    healthStatus: HealthStatus,
    healthError: String?,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "BACKEND", fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                label = { Text("Servidor Huata (URL completa)") },
                singleLine = true,
                isError = urlError != null,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Incluye http/https, IP o hostname y puerto de Laravel. HTTP solo para pruebas LAN en debug; release requiere HTTPS.", style = MaterialTheme.typography.bodySmall)
            if (runCatching { java.net.URI(urlInput).host }.getOrNull() in listOf("localhost", "127.0.0.1", "::1", "[::1]")) {
                Text("Esta dirección apunta al propio teléfono. Configura un servidor Huata alcanzable por WiFi.", style = MaterialTheme.typography.bodySmall)
            }
            urlError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("GUARDAR")
                }
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("PROBAR CONEXIÓN")
                }
            }

            Text(
                text = "Conexión autenticada: " + healthStatus.name,
                color = when (healthStatus) {
                    HealthStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                    HealthStatus.FAILED -> MaterialTheme.colorScheme.error
                    HealthStatus.CONNECTING,
                    HealthStatus.NOT_TESTED -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold
            )
            val preview = BridgeConfig.normalizeBaseUrl(urlInput)
                ?.let { BridgeConfig.healthUrl(it) }
            if (preview != null) {
                Text(
                    text = "POST $preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (healthStatus == HealthStatus.FAILED) {
                healthError?.let {
                    Text(
                        text = "Error: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: CapturedNotification) {
    var showExtras by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "App package: ${item.packageName}", fontWeight = FontWeight.SemiBold)
            Text(text = "Hora: ${formatTime(item.postedAt)}")
            item.title?.let { Text(text = "Title: $it") }
            item.text?.let { Text(text = "Text: $it") }
            item.bigText?.let { Text(text = "BigText: $it") }
            item.subText?.let { Text(text = "SubText: $it") }
            item.summaryText?.let { Text(text = "Summary: $it") }
            Text(
                text = "Security code detected: " + if (item.securityCode != null) "YES (*** detected)" else "NO",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = { showExtras = !showExtras }) {
                Text(if (showExtras) "Ocultar extras" else "Mostrar extras")
            }
            if (showExtras) {
                Text(
                    text = "Extras ocultos por privacidad. Solo se usa el indicador de código detectado.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    } catch (_: Exception) {
        millis.toString()
    }
}
