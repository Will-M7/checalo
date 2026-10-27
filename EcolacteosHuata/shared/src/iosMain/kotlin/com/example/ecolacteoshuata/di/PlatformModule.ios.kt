package com.example.ecolacteoshuata.di

import com.example.ecolacteoshuata.core.pdf.PdfExporter
import com.example.ecolacteoshuata.data.local.DatabaseDriverFactory
import com.example.ecolacteoshuata.data.platform.IosNetworkMonitor
import com.example.ecolacteoshuata.data.platform.IosNotificadorSistema
import com.example.ecolacteoshuata.domain.platform.NotificadorSistema
import com.example.ecolacteoshuata.data.platform.IosTokenStorage
import com.example.ecolacteoshuata.domain.platform.NetworkMonitor
import com.example.ecolacteoshuata.domain.platform.TokenStorage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Motor Darwin (NSURLSession) para iOS
    single<HttpClientEngine> { Darwin.create() }
    single { DatabaseDriverFactory() }
    single<TokenStorage> { IosTokenStorage() }
    single<NetworkMonitor> { IosNetworkMonitor() }
    single<NotificadorSistema> { IosNotificadorSistema() }
    single { PdfExporter() }
}
