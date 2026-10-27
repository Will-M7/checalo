package com.example.ecolacteoshuata.di

import com.example.ecolacteoshuata.data.remote.ApiConfig
import com.example.ecolacteoshuata.data.remote.HttpClientFactory
import com.example.ecolacteoshuata.data.remote.datasource.AuthRemoteDataSourceImpl
import com.example.ecolacteoshuata.data.remote.datasource.SyncRemoteDataSource
import com.example.ecolacteoshuata.domain.repository.AuthRemoteDataSource
import org.koin.dsl.module

/** Red: un único HttpClient para toda la app y las fuentes remotas que lo usan. */
val networkModule = module {
    single { com.example.ecolacteoshuata.data.remote.BridgeSessionAssertion(get(),get(),get(),get()) }
    single { ApiConfig() }
    single { HttpClientFactory.create(engine = get(), config = get(), tokenStorage = get()) }
    single { SyncRemoteDataSource(get(), get()) }
    single<com.example.ecolacteoshuata.data.remote.datasource.AdministracionRolesRemota> { get<SyncRemoteDataSource>() }
    single<AuthRemoteDataSource> { AuthRemoteDataSourceImpl(get(), get()) }
}

/** Orden recomendado: plataforma primero, luego red, base local, datos, dominio y presentación. */
val appModules = listOf(platformModule, networkModule, databaseModule, dataModule, domainModule, viewModelModule)
