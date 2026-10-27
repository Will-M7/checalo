package com.example.ecolacteoshuata.di

import com.example.ecolacteoshuata.core.pdf.PdfExporter
import com.example.ecolacteoshuata.data.local.DatabaseDriverFactory
import com.example.ecolacteoshuata.data.platform.AndroidNetworkMonitor
import com.example.ecolacteoshuata.data.platform.AndroidNotificadorSistema
import com.example.ecolacteoshuata.domain.platform.NotificadorSistema
import com.example.ecolacteoshuata.data.platform.AndroidTokenStorage
import com.example.ecolacteoshuata.domain.platform.NetworkMonitor
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Motor OkHttp para Android
    single<HttpClientEngine> { OkHttp.create() }
    single { DatabaseDriverFactory(androidContext()) }
    single<TokenStorage> { AndroidTokenStorage(androidContext()) }
    single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
    single<NotificadorSistema> { AndroidNotificadorSistema(androidContext()) }
    single { PdfExporter(androidContext()) }
}
