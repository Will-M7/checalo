package com.example.ecolacteoshuata.di

import com.example.ecolacteoshuata.domain.model.TipoReporte
import com.example.ecolacteoshuata.presentation.common.asistencia.TomarAsistenciaViewModel
import com.example.ecolacteoshuata.presentation.common.conexion.AvisoConexionViewModel
import com.example.ecolacteoshuata.presentation.common.NotificacionesViewModel
import com.example.ecolacteoshuata.presentation.common.asistencia.MiAsistenciaViewModel
import com.example.ecolacteoshuata.presentation.acopiador.AcopiadorRootViewModel
import com.example.ecolacteoshuata.presentation.acopiador.dashboard.AcopiadorDashboardViewModel
import com.example.ecolacteoshuata.presentation.acopiador.entrega.AcopiadorEntregaViewModel
import com.example.ecolacteoshuata.presentation.acopiador.recojo.AcopiadorRecojoViewModel
import com.example.ecolacteoshuata.presentation.admin.AdminRootViewModel
import com.example.ecolacteoshuata.presentation.admin.clientes.AdminClientesViewModel
import com.example.ecolacteoshuata.presentation.admin.config.AdminConfiguracionViewModel
import com.example.ecolacteoshuata.presentation.admin.dashboard.AdminDashboardViewModel
import com.example.ecolacteoshuata.presentation.admin.precios.AdminPreciosViewModel
import com.example.ecolacteoshuata.presentation.admin.descuentos.AdminDescuentosViewModel
import com.example.ecolacteoshuata.presentation.common.UbicacionFincaViewModel
import com.example.ecolacteoshuata.presentation.admin.roles.AdminAsignarRolesViewModel
import com.example.ecolacteoshuata.presentation.admin.rutas.AdminRutasViewModel
import com.example.ecolacteoshuata.presentation.admin.turnos.AdminTurnosViewModel
import com.example.ecolacteoshuata.presentation.admin.usuarios.AdminUsuarioFormViewModel
import com.example.ecolacteoshuata.presentation.admin.usuarios.AdminUsuariosViewModel
import com.example.ecolacteoshuata.presentation.common.conexion.ConexionSyncViewModel
import com.example.ecolacteoshuata.presentation.common.mapa.MapaRutaViewModel
import com.example.ecolacteoshuata.presentation.common.reportes.ReportesViewModel
import com.example.ecolacteoshuata.presentation.login.PrimeraDescargaViewModel
import com.example.ecolacteoshuata.presentation.login.LoginViewModel
import com.example.ecolacteoshuata.presentation.planta.PlantaRootViewModel
import com.example.ecolacteoshuata.presentation.planta.comunicados.PlantaComunicadosViewModel
import com.example.ecolacteoshuata.presentation.planta.dashboard.PlantaDashboardViewModel
import com.example.ecolacteoshuata.presentation.planta.laboratorio.PlantaLaboratorioRegistroViewModel
import com.example.ecolacteoshuata.presentation.planta.laboratorio.PlantaLaboratorioViewModel
import com.example.ecolacteoshuata.presentation.planta.pagos.PlantaPagosViewModel
import com.example.ecolacteoshuata.presentation.planta.recepcion.PlantaRecepcionViewModel
import com.example.ecolacteoshuata.presentation.produccion.ProduccionRootViewModel
import com.example.ecolacteoshuata.presentation.produccion.compras.ProduccionComprasViewModel
import com.example.ecolacteoshuata.presentation.produccion.dashboard.ProduccionDashboardViewModel
import com.example.ecolacteoshuata.presentation.produccion.insumos.ProduccionInsumosViewModel
import com.example.ecolacteoshuata.presentation.produccion.inventario.ProduccionInventarioViewModel
import com.example.ecolacteoshuata.presentation.produccion.recetas.ProduccionRecetasViewModel
import com.example.ecolacteoshuata.presentation.produccion.registros.ProduccionRegistrosViewModel
import com.example.ecolacteoshuata.presentation.produccion.unidades.ProduccionUnidadesViewModel
import com.example.ecolacteoshuata.presentation.proveedor.ProveedorRootViewModel
import com.example.ecolacteoshuata.presentation.proveedor.comunicados.ProveedorComunicadosViewModel
import com.example.ecolacteoshuata.presentation.proveedor.dashboard.ProveedorDashboardViewModel
import com.example.ecolacteoshuata.presentation.proveedor.entregas.ProveedorEntregasViewModel
import com.example.ecolacteoshuata.presentation.proveedor.laboratorio.ProveedorLaboratorioViewModel
import com.example.ecolacteoshuata.presentation.vendedor.VendedorRootViewModel
import com.example.ecolacteoshuata.presentation.vendedor.dashboard.VendedorDashboardViewModel
import com.example.ecolacteoshuata.presentation.vendedor.historial.VendedorHistorialViewModel
import com.example.ecolacteoshuata.presentation.vendedor.venta.VendedorVentaViewModel
import com.example.ecolacteoshuata.presentation.acopiador.inventario.AcopiadorInventarioViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get(), get()) }

    viewModel { AdminRootViewModel(get(), get()) }
    viewModel { AdminDashboardViewModel(get()) }
    viewModel { AdminUsuariosViewModel(get(), get()) }
    viewModel { params -> AdminUsuarioFormViewModel(params.getOrNull(), get(), get(), get(), get(), get()) }
    viewModel { AdminClientesViewModel(get(), get(), get(), get()) }
    viewModel { AdminRutasViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AdminPreciosViewModel(get(), get(), get()) }
    viewModel { AdminDescuentosViewModel(get(), get()) }
    viewModel { params -> UbicacionFincaViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { AdminConfiguracionViewModel(get()) }
    viewModel { AdminAsignarRolesViewModel(get(), get(), get(), get(), get()) }
    viewModel { com.example.ecolacteoshuata.presentation.admin.roles.AdminRolesViewModel(get()) }
    viewModel { com.example.ecolacteoshuata.presentation.admin.roles.DistribucionesViewModel(get()) }
    viewModel { AdminTurnosViewModel(get(), get(), get(), get(), get(), get()) }

    viewModel { VendedorRootViewModel(get(), get()) }
    viewModel { VendedorDashboardViewModel(get(), get(), get(), get(), get()) }
    viewModel { params -> VendedorVentaViewModel(params.getOrNull<String>(), get(), get(), get(), get(), get(), get()) }
    viewModel { VendedorHistorialViewModel(get(), get(), get(), get()) }

    viewModel { ProduccionRootViewModel(get(), get()) }
    viewModel { ProduccionDashboardViewModel(get(), get(), get(), get()) }
    viewModel { ProduccionUnidadesViewModel(get(), get(), get()) }
    viewModel { ProduccionInsumosViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { ProduccionRecetasViewModel(get(), get(), get(), get(), get()) }
    viewModel { ProduccionComprasViewModel(get(), get(), get()) }
    viewModel { com.example.ecolacteoshuata.presentation.produccion.productos.ProduccionProductosViewModel(get(), get(), get()) }
    viewModel { ProduccionRegistrosViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProduccionInventarioViewModel(get()) }

    viewModel { PlantaRootViewModel(get(), get()) }
    viewModel { PlantaDashboardViewModel(get()) }
    viewModel { PlantaRecepcionViewModel(get(), get()) }
    viewModel { PlantaLaboratorioViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { params -> PlantaLaboratorioRegistroViewModel(params.get<String>(0), params.get<String>(1).ifBlank { null }, get(), get(), get(), get(), get(), get()) }
    viewModel { PlantaPagosViewModel(get(), get(), get()) }
    viewModel { params -> PlantaComunicadosViewModel(params.get(), get(), get(), get()) }
    viewModel { AvisoConexionViewModel(get(), get()) }
    viewModel { NotificacionesViewModel(get(), get()) }
    viewModel { params -> TomarAsistenciaViewModel(params.get(), get(), get()) }
    viewModel { params -> MiAsistenciaViewModel(params.get<String>(0), params.get<Boolean>(1), get()) }

    viewModel { params -> AcopiadorRootViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> AcopiadorDashboardViewModel(params.get(), get()) }
    viewModel { params -> AcopiadorRecojoViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> AcopiadorEntregaViewModel(params.get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> AcopiadorInventarioViewModel(params.get(), get(), get()) }

    viewModel { params -> ProveedorRootViewModel(params.get(), get(), get(), get()) }
    viewModel { params ->
        ProveedorDashboardViewModel(params.get<String>(0), get(), get(), get(), get())
    }
    viewModel { params -> ProveedorLaboratorioViewModel(params.get(), get(), get()) }
    viewModel { params -> ProveedorEntregasViewModel(params.get(), get(), get()) }
    viewModel { ProveedorComunicadosViewModel(get()) }

    viewModel { params ->
        MapaRutaViewModel(
            editable = params.get<Boolean>(0),
            acopiadorId = params.get<String?>(1),
            observeRutas = get(),
            observeProveedores = get(),
            observeAcopiadores = get(),
            getRecogidosHoy = get(),
            moverParadaUseCase = get(),
            guardarTrazoUseCase = get(),
            ubicarProveedorUseCase = get(),
        )
    }
    viewModel { PrimeraDescargaViewModel(get(), get()) }
    viewModel { ConexionSyncViewModel(get(), get(), get(), get()) }

    viewModel { params ->
        ReportesViewModel(
            tiposDisponibles = params.get<List<TipoReporte>>(0),
            acopiadorId = params.get<String?>(1),
            proveedorId = params.get<String?>(2),
            getReporteUseCase = get(),
            exportarReportePdfUseCase = get(),
        )
    }
}
