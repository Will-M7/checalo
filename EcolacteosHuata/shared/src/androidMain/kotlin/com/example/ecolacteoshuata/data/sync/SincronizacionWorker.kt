package com.example.ecolacteoshuata.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Sincroniza con Laravel en segundo plano (aunque la app esté cerrada), para que las
 * notificaciones de comunicados y asesoramiento lleguen al celular y lo pendiente se suba.
 */
class SincronizacionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val syncService: SyncService by inject()

    override suspend fun doWork(): Result = runCatching { syncService.sincronizarEnSegundoPlano() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        private const val NOMBRE = "sincronizacion_periodica"

        /** Cada 15 minutos (el mínimo de Android), solo con red. */
        fun programar(context: Context) {
            val trabajo = PeriodicWorkRequestBuilder<SincronizacionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.KEEP, trabajo)
        }
    }
}
