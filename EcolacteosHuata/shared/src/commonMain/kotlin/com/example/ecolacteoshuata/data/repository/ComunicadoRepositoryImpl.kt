package com.example.ecolacteoshuata.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.data.mapper.toDomain
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.Comunicado
import com.example.ecolacteoshuata.domain.model.Empresa
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.repository.ComunicadoRepository
import com.example.ecolacteoshuata.domain.repository.EmpresaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ComunicadoRepositoryImpl(private val db: AppDatabase) : ComunicadoRepository {
    private val queries = db.comunicadoQueries

    override fun observeAll(): Flow<List<Comunicado>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Comunicado> = queries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun crear(comunicado: Comunicado) {
        queries.insert(
            id = comunicado.id.ifBlank { IdGenerator.newId() },
            tipo = comunicado.tipo.name,
            titulo = comunicado.titulo,
            descripcion = comunicado.descripcion,
            fecha = comunicado.fecha.toString(),
            autor_id = comunicado.autorId,
            roles = comunicado.roles?.let { Rol.codificarLista(it) },
        )
    }

    override suspend fun eliminar(id: String) {
        queries.deleteById(id)
    }
}

class EmpresaRepositoryImpl(private val db: AppDatabase) : EmpresaRepository {
    private val queries = db.empresaQueries

    override fun observe(): Flow<Empresa?> =
        queries.selectPrimera().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toDomain() }

    override suspend fun get(): Empresa? = queries.selectPrimera().executeAsOneOrNull()?.toDomain()

    override suspend fun actualizar(empresa: Empresa) {
        val existente = get()
        if (existente == null) {
            queries.insert(
                id = empresa.id.ifBlank { IdGenerator.newId() },
                razon_social = empresa.razonSocial,
                ruc = empresa.ruc,
                telefono = empresa.telefono,
                direccion = empresa.direccion,
            )
        } else {
            queries.update(
                razon_social = empresa.razonSocial,
                ruc = empresa.ruc,
                telefono = empresa.telefono,
                direccion = empresa.direccion,
                id = existente.id,
            )
        }
    }
}
