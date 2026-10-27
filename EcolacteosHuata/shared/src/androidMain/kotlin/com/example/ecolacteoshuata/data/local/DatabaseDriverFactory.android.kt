package com.example.ecolacteoshuata.data.local

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.ecolacteoshuata.db.AppDatabase

private const val NOMBRE_BD = "ecolacteoshuata.db"

actual class DatabaseDriverFactory(private val context: Context) {
    /** Migra sin borrar datos. Un fallo requiere revisión técnica y conserva la base original. */
    actual fun createDriver(): SqlDriver {
        val driver = AndroidSqliteDriver(AppDatabase.Schema, context, NOMBRE_BD)
        try {
            driver.executeQuery(null, "SELECT 1", { QueryResult.Value(it.next().value) }, 0)
            return driver
        } catch (e: Exception) {
            runCatching { driver.close() }
            throw IllegalStateException("No se pudo abrir la base local. Se conservaron sus datos; requiere revisión técnica.", e)
        }
    }
}
