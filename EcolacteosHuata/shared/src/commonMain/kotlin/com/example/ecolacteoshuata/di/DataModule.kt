package com.example.ecolacteoshuata.di

import com.example.ecolacteoshuata.data.sync.RealSyncService
import com.example.ecolacteoshuata.data.sync.SyncService
import com.example.ecolacteoshuata.data.seed.SeedData
import com.example.ecolacteoshuata.data.repository.AcopiadorRepositoryImpl
import com.example.ecolacteoshuata.data.repository.AsistenciaRepositoryImpl
import com.example.ecolacteoshuata.data.repository.NotificacionRepositoryImpl
import com.example.ecolacteoshuata.domain.repository.NotificacionRepository
import com.example.ecolacteoshuata.domain.repository.AsistenciaRepository
import com.example.ecolacteoshuata.data.repository.ClienteRepositoryImpl
import com.example.ecolacteoshuata.data.repository.ComunicadoRepositoryImpl
import com.example.ecolacteoshuata.data.repository.ConexionServidorRepositoryImpl
import com.example.ecolacteoshuata.data.repository.EmpresaRepositoryImpl
import com.example.ecolacteoshuata.data.repository.EntregaPlantaRepositoryImpl
import com.example.ecolacteoshuata.data.repository.CompraRepositoryImpl
import com.example.ecolacteoshuata.data.repository.InsumoRepositoryImpl
import com.example.ecolacteoshuata.data.repository.ProduccionRepositoryImpl
import com.example.ecolacteoshuata.data.repository.RecetaRepositoryImpl
import com.example.ecolacteoshuata.data.repository.TurnoProduccionRepositoryImpl
import com.example.ecolacteoshuata.data.repository.DescuentoRepositoryImpl
import com.example.ecolacteoshuata.data.repository.UnidadMedidaRepositoryImpl
import com.example.ecolacteoshuata.data.repository.PagoProveedorRepositoryImpl
import com.example.ecolacteoshuata.data.repository.PrecioLecheRepositoryImpl
import com.example.ecolacteoshuata.data.repository.ProductoRepositoryImpl
import com.example.ecolacteoshuata.data.repository.ProveedorRepositoryImpl
import com.example.ecolacteoshuata.data.repository.RecojoRepositoryImpl
import com.example.ecolacteoshuata.data.repository.ResultadoLaboratorioRepositoryImpl
import com.example.ecolacteoshuata.data.repository.RutaRepositoryImpl
import com.example.ecolacteoshuata.data.repository.SessionRepositoryImpl
import com.example.ecolacteoshuata.data.repository.TurnoRepositoryImpl
import com.example.ecolacteoshuata.data.repository.UsuarioRepositoryImpl
import com.example.ecolacteoshuata.data.repository.VentaRepositoryImpl
import com.example.ecolacteoshuata.domain.repository.AcopiadorRepository
import com.example.ecolacteoshuata.domain.repository.AuthRemoteDataSource
import com.example.ecolacteoshuata.domain.repository.ClienteRepository
import com.example.ecolacteoshuata.domain.repository.ComunicadoRepository
import com.example.ecolacteoshuata.domain.repository.ConexionServidorRepository
import com.example.ecolacteoshuata.domain.repository.EmpresaRepository
import com.example.ecolacteoshuata.domain.repository.EntregaPlantaRepository
import com.example.ecolacteoshuata.domain.repository.CompraRepository
import com.example.ecolacteoshuata.domain.repository.InsumoRepository
import com.example.ecolacteoshuata.domain.repository.ProduccionRepository
import com.example.ecolacteoshuata.domain.repository.RecetaRepository
import com.example.ecolacteoshuata.domain.repository.TurnoProduccionRepository
import com.example.ecolacteoshuata.domain.repository.DescuentoRepository
import com.example.ecolacteoshuata.domain.repository.UnidadMedidaRepository
import com.example.ecolacteoshuata.domain.repository.PagoProveedorRepository
import com.example.ecolacteoshuata.domain.repository.PrecioLecheRepository
import com.example.ecolacteoshuata.domain.repository.ProductoRepository
import com.example.ecolacteoshuata.domain.repository.ProveedorRepository
import com.example.ecolacteoshuata.domain.repository.RecojoRepository
import com.example.ecolacteoshuata.domain.repository.ResultadoLaboratorioRepository
import com.example.ecolacteoshuata.domain.repository.RutaRepository
import com.example.ecolacteoshuata.domain.repository.SessionRepository
import com.example.ecolacteoshuata.domain.repository.TurnoRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.domain.repository.VentaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val dataModule = module {
    single<com.example.ecolacteoshuata.data.remote.datasource.SincronizacionRemota> { get<com.example.ecolacteoshuata.data.remote.datasource.SyncRemoteDataSource>() }
    single<com.example.ecolacteoshuata.domain.repository.BorradorVentaYapeRepository> { com.example.ecolacteoshuata.data.repository.BorradorVentaYapeRepositoryImpl(get()) }
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<SyncService> { RealSyncService(get(), get(), get(), get(), get(), get(), get(), get()) }

    single<UsuarioRepository> { UsuarioRepositoryImpl(get(), get()) }
    single<ProveedorRepository> { ProveedorRepositoryImpl(get()) }
    single<AcopiadorRepository> { AcopiadorRepositoryImpl(get()) }
    single<TurnoRepository> { TurnoRepositoryImpl(get()) }
    single<RutaRepository> { RutaRepositoryImpl(get()) }
    single<ProductoRepository> { ProductoRepositoryImpl(get()) }
    single<ClienteRepository> { ClienteRepositoryImpl(get()) }
    single<PrecioLecheRepository> { PrecioLecheRepositoryImpl(get()) }
    single<RecojoRepository> { RecojoRepositoryImpl(get()) }
    single<EntregaPlantaRepository> { EntregaPlantaRepositoryImpl(get()) }
    single<ResultadoLaboratorioRepository> { ResultadoLaboratorioRepositoryImpl(get()) }
    single<VentaRepository> { VentaRepositoryImpl(get()) }
    single<PagoProveedorRepository> { PagoProveedorRepositoryImpl(get()) }
    single<ComunicadoRepository> { ComunicadoRepositoryImpl(get()) }
    single<EmpresaRepository> { EmpresaRepositoryImpl(get()) }
    single<ConexionServidorRepository> { ConexionServidorRepositoryImpl(get(), get()) }
    single<UnidadMedidaRepository> { UnidadMedidaRepositoryImpl(get()) }
    single<InsumoRepository> { InsumoRepositoryImpl(get()) }
    single<RecetaRepository> { RecetaRepositoryImpl(get()) }
    single<CompraRepository> { CompraRepositoryImpl(get()) }
    single<ProduccionRepository> { ProduccionRepositoryImpl(get()) }
    single<TurnoProduccionRepository> { TurnoProduccionRepositoryImpl(get()) }
    single<DescuentoRepository> { DescuentoRepositoryImpl(get()) }
    single<AsistenciaRepository> { AsistenciaRepositoryImpl(get()) }
    single<NotificacionRepository> { NotificacionRepositoryImpl(get()) }
    single<SessionRepository> { SessionRepositoryImpl(get()) }
    single { SeedData(get()) }
}
