package com.example.ecolacteoshuata

import android.app.Application
import com.example.ecolacteoshuata.data.sync.SincronizacionWorker
import com.example.ecolacteoshuata.di.initKoin
import org.koin.android.ext.koin.androidContext

class EcolacteosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@EcolacteosApplication)
        }
        // Sincroniza cada 15 min aunque la app esté cerrada: así llegan las notificaciones.
        SincronizacionWorker.programar(this)
    }
}
