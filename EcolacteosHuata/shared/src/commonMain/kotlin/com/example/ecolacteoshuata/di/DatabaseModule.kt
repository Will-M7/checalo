package com.example.ecolacteoshuata.di

import app.cash.sqldelight.db.SqlDriver
import com.example.ecolacteoshuata.data.sync.RegistroCambios
import com.example.ecolacteoshuata.data.local.DatabaseDriverFactory
import com.example.ecolacteoshuata.db.AppDatabase
import org.koin.dsl.module

val databaseModule = module {
    single<SqlDriver> { get<DatabaseDriverFactory>().createDriver().also { RegistroCambios.instalar(it) } }
    single { AppDatabase(get<SqlDriver>()).also { com.example.ecolacteoshuata.data.local.MigracionAutorizacionHeredada.ejecutar(it) } }
}
