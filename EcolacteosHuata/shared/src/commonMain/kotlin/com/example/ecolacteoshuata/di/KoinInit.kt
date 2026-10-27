package com.example.ecolacteoshuata.di

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Punto único de arranque de Koin para Android (EcolacteosApplication) e iOS (iOSApp.swift con
 * initKoinIos). Sin datos de demo: la base SQLite arranca vacía y todo lo trae la primera
 * descarga desde Laravel apenas se configura la conexión con el servidor.
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    stopKoinSafely()
    startKoin {
        config?.invoke(this)
        modules(appModules)
    }
}

private fun stopKoinSafely() {
    try {
        stopKoin()
    } catch (_: Throwable) {
        // Todavía no había Koin iniciado: nada que detener.
    }
}
