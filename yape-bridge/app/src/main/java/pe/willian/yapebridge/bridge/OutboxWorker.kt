package pe.willian.yapebridge.bridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.willian.yapebridge.store.OutboxDatabase

class OutboxWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = OutboxDatabase.get(applicationContext)
        val complete = runCatching { CanonicalDelivery.drain(db.pending(), BackendBridge::trySend) }.getOrDefault(false)
        BackendBridge.refreshQueue()
        if (complete) Result.success() else Result.retry()
    }
}
