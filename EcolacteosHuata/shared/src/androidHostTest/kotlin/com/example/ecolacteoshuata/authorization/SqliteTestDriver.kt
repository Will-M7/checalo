package com.example.ecolacteoshuata.authorization

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.*
import java.io.File
import java.net.URLClassLoader
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Properties

/** Real SQLite for host tests; uses the JDBC binary already supplied by the build environment. */
internal class SqliteTestDriver(url: String = "jdbc:sqlite::memory:") : SqlDriver {
    private val conexion: Connection
    private var transaccion: Transacter.Transaction? = null
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()
    init {
        val jar = System.getenv("HUATA_SQLITE_JDBC_JAR")?.let(::File)
            ?: File(System.getenv("GRADLE_USER_HOME") ?: "${System.getProperty("user.home")}/.gradle", "caches/modules-2/files-2.1/org.xerial/sqlite-jdbc").walkTopDown().first { it.extension == "jar" }
        val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader)
        val jdbc = loader.loadClass("org.sqlite.JDBC").getDeclaredConstructor().newInstance() as java.sql.Driver
        conexion = jdbc.connect(url, Properties())
    }
    private fun preparado(sql: String, binders: (SqlPreparedStatement.() -> Unit)?): PreparedStatement =
        conexion.prepareStatement(sql).also { statement -> binders?.invoke(object : SqlPreparedStatement {
            override fun bindBytes(index: Int, bytes: ByteArray?) { statement.setBytes(index + 1, bytes) }
            override fun bindLong(index: Int, long: Long?) { statement.setObject(index + 1, long) }
            override fun bindDouble(index: Int, double: Double?) { statement.setObject(index + 1, double) }
            override fun bindString(index: Int, string: String?) { statement.setString(index + 1, string) }
            override fun bindBoolean(index: Int, boolean: Boolean?) { statement.setObject(index + 1, boolean) }
        }) }
    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> =
        preparado(sql, binders).use { it.execute(); QueryResult.Value(it.updateCount.coerceAtLeast(0).toLong()) }
    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> =
        preparado(sql, binders).use { st -> st.executeQuery().use { rs -> mapper(Cursor(rs)) } }
    private class Cursor(val r: ResultSet) : SqlCursor {
        override fun next() = QueryResult.Value(r.next())
        override fun getString(index: Int): String? = r.getString(index + 1)
        override fun getLong(index: Int): Long? = r.getLong(index + 1).takeUnless { r.wasNull() }
        override fun getDouble(index: Int): Double? = r.getDouble(index + 1).takeUnless { r.wasNull() }
        override fun getBytes(index: Int): ByteArray? = r.getBytes(index + 1)
        override fun getBoolean(index: Int): Boolean? = r.getBoolean(index + 1).takeUnless { r.wasNull() }
    }
    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val anterior = transaccion
        if (anterior == null) conexion.autoCommit = false
        val nueva = object : Transacter.Transaction() {
            override val enclosingTransaction = anterior
            override fun endTransaction(successful: Boolean): QueryResult<Unit> {
                transaccion = anterior
                if (anterior == null) {
                    if (successful) conexion.commit() else conexion.rollback()
                    conexion.autoCommit = true
                }
                return QueryResult.Unit
            }
        }
        transaccion = nueva
        return QueryResult.Value(nueva)
    }
    override fun currentTransaction() = transaccion
    override fun addListener(vararg queryKeys: String, listener: Query.Listener) { queryKeys.forEach { listeners.getOrPut(it) { mutableSetOf() }.add(listener) } }
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) { queryKeys.forEach { listeners[it]?.remove(listener) } }
    override fun notifyListeners(vararg queryKeys: String) { queryKeys.flatMap { listeners[it].orEmpty() }.toSet().forEach { it.queryResultsChanged() } }
    override fun close() { conexion.close() }
}
