package com.example.ecolacteoshuata.di

import com.example.ecolacteoshuata.domain.usecase.AgregarTurnoProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarInsumoUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarRecetaUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarTurnoProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarUnidadMedidaUseCase
import com.example.ecolacteoshuata.domain.usecase.GetInventarioInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarInsumoUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarRecetaUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarUnidadMedidaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveComprasUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveInsumosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveRecetasUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTurnosProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveUnidadesMedidaUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarCompraUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ActualizarProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.PublicarProduccionUseCase
import com.example.ecolacteoshuata.domain.usecase.ActualizarTurnoUseCase
import com.example.ecolacteoshuata.domain.usecase.ActualizarUbicacionFincaUseCase
import com.example.ecolacteoshuata.domain.usecase.ActualizarUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.AgregarTurnoUseCase
import com.example.ecolacteoshuata.domain.usecase.AsignarProveedoresARutaUseCase
import com.example.ecolacteoshuata.domain.usecase.GetProveedoresRecogidosHoyUseCase
import com.example.ecolacteoshuata.domain.usecase.GetProveedoresRecogidosEnElDiaUseCase
import com.example.ecolacteoshuata.domain.usecase.AgregarProveedorARutaUseCase
import com.example.ecolacteoshuata.domain.usecase.QuitarProveedorDeRutaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTodosLosTurnosUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarTrazoRutaUseCase
import com.example.ecolacteoshuata.domain.usecase.MoverParadaUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTodasLasRutasUseCase
import com.example.ecolacteoshuata.domain.usecase.UbicarProveedorEnMapaUseCase
import com.example.ecolacteoshuata.domain.usecase.BloquearProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.CalcularTotalesCarritoUseCase
import com.example.ecolacteoshuata.domain.usecase.AsignarRolesUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.PerfilesPorRol
import com.example.ecolacteoshuata.domain.usecase.GuardarDescuentoUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarUbicacionPropiaUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarClienteUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarComunicadoUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarRutaUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarTurnoUseCase
import com.example.ecolacteoshuata.domain.usecase.EliminarUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.EntregarAPlantaUseCase
import com.example.ecolacteoshuata.domain.usecase.ExportarReportePdfUseCase
import com.example.ecolacteoshuata.domain.usecase.GenerarBoletaPdfUseCase
import com.example.ecolacteoshuata.domain.usecase.GenerarResultadoLabPdfUseCase
import com.example.ecolacteoshuata.domain.usecase.GetDashboardAcopiadorUseCase
import com.example.ecolacteoshuata.domain.usecase.GetDashboardAdminUseCase
import com.example.ecolacteoshuata.domain.usecase.GetDashboardPlantaUseCase
import com.example.ecolacteoshuata.domain.usecase.GetDashboardProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.GetNotificacionesUseCase
import com.example.ecolacteoshuata.domain.usecase.GetPrecioLecheUseCase
import com.example.ecolacteoshuata.domain.usecase.GetUsuarioByIdUseCase
import com.example.ecolacteoshuata.domain.usecase.GetProveedorPorUsuarioUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveAcopiadoresUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProveedoresUseCase
import com.example.ecolacteoshuata.domain.usecase.GetProveedoresPorAcopiadorUseCase
import com.example.ecolacteoshuata.domain.usecase.AsignarAcopiadorAProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.GetReporteUseCase
import com.example.ecolacteoshuata.domain.usecase.GetResumenEntregaUseCase
import com.example.ecolacteoshuata.domain.usecase.GetResumenPagoProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.GetSessionUseCase
import com.example.ecolacteoshuata.domain.usecase.GetUsuariosUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarClienteUseCase
import com.example.ecolacteoshuata.domain.usecase.GuardarRutaUseCase
import com.example.ecolacteoshuata.domain.usecase.LoginUseCase
import com.example.ecolacteoshuata.domain.usecase.LogoutUseCase
import com.example.ecolacteoshuata.domain.usecase.ObservarEstadoSincronizacionUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveClientesUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveComunicadosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObservePrecioLecheUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveProductosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveRutasUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveTurnosUseCase
import com.example.ecolacteoshuata.domain.usecase.ObserveUsuariosUseCase
import com.example.ecolacteoshuata.domain.usecase.PagarProveedorUseCase
import com.example.ecolacteoshuata.domain.usecase.PublicarComunicadoUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarRecojoUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarResultadoLaboratorioUseCase
import com.example.ecolacteoshuata.domain.usecase.RegistrarVentaUseCase
import com.example.ecolacteoshuata.domain.usecase.SetDescuentoClienteUseCase
import com.example.ecolacteoshuata.domain.usecase.SetPrecioLecheUseCase
import com.example.ecolacteoshuata.domain.usecase.SincronizarPendientesUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { LoginUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetSessionUseCase(get()) }
    factory { LogoutUseCase(get()) }

    factory { ObserveUsuariosUseCase(get()) }
    factory { GetUsuariosUseCase(get()) }
    factory { PerfilesPorRol(get(), get()) }
    factory { GuardarDescuentoUseCase(get()) }
    factory { GuardarUbicacionPropiaUseCase(get(), get()) }
    factory { GuardarUsuarioUseCase(get(), get(), get(), get()) }
    factory { com.example.ecolacteoshuata.domain.usecase.AdministrarRolesUseCase(get(), get(), get(), get()) }
    factory { AsignarRolesUseCase(get(), get(), get()) }
    factory { ActualizarUsuarioUseCase(get()) }
    factory { EliminarUsuarioUseCase(get()) }
    factory { GetUsuarioByIdUseCase(get()) }
    factory { GetProveedorPorUsuarioUseCase(get()) }
    factory { ObserveAcopiadoresUseCase(get()) }
    factory { ObserveProveedoresUseCase(get()) }
    factory { GetProveedoresPorAcopiadorUseCase(get()) }
    factory { AsignarAcopiadorAProveedorUseCase(get(), get()) }
    factory { com.example.ecolacteoshuata.domain.usecase.GetAcopiadorPorUsuarioUseCase(get()) }

    factory { ObserveProductosUseCase(get()) }

    factory { ObserveClientesUseCase(get()) }
    factory { GuardarClienteUseCase(get()) }
    factory { EliminarClienteUseCase(get()) }
    factory { SetDescuentoClienteUseCase(get()) }

    factory { ObserveRutasUseCase(get()) }
    factory { GuardarRutaUseCase(get()) }
    factory { EliminarRutaUseCase(get()) }
    factory { AsignarProveedoresARutaUseCase(get()) }
    factory { ObserveTodasLasRutasUseCase(get()) }
    factory { MoverParadaUseCase(get()) }
    factory { GuardarTrazoRutaUseCase(get()) }
    factory { UbicarProveedorEnMapaUseCase(get()) }
    factory { GetProveedoresRecogidosHoyUseCase(get()) }
    factory { GetProveedoresRecogidosEnElDiaUseCase(get()) }
    factory { AgregarProveedorARutaUseCase(get(), get()) }
    factory { QuitarProveedorDeRutaUseCase(get()) }
    factory { ObserveTodosLosTurnosUseCase(get()) }
    factory { ObserveTurnosUseCase(get()) }
    factory { AgregarTurnoUseCase(get()) }
    factory { ActualizarTurnoUseCase(get()) }
    factory { EliminarTurnoUseCase(get()) }

    factory { ObservePrecioLecheUseCase(get()) }
    factory { GetPrecioLecheUseCase(get()) }
    factory { SetPrecioLecheUseCase(get()) }

    factory { CalcularTotalesCarritoUseCase() }
    factory { RegistrarVentaUseCase(get(), get(), get(), get(), get()) }

    factory { RegistrarRecojoUseCase(get(), get(), get()) }
    factory { SincronizarPendientesUseCase(get(), get()) }
    factory { GetResumenEntregaUseCase(get(), get()) }
    factory { EntregarAPlantaUseCase(get(), get(), get()) }

    factory { RegistrarResultadoLaboratorioUseCase(get(), get(), get()) }
    factory { BloquearProveedorUseCase(get()) }

    factory { GetResumenPagoProveedorUseCase(get()) }
    factory { PagarProveedorUseCase(get(), get(), get()) }

    factory { ObserveComunicadosUseCase(get()) }
    factory { PublicarComunicadoUseCase(get()) }
    factory { EliminarComunicadoUseCase(get()) }

    factory { ObserveUnidadesMedidaUseCase(get()) }
    factory { GuardarUnidadMedidaUseCase(get()) }
    factory { EliminarUnidadMedidaUseCase(get(), get()) }

    factory { ObserveInsumosUseCase(get()) }
    factory { GuardarInsumoUseCase(get()) }
    factory { EliminarInsumoUseCase(get()) }

    factory { ObserveRecetasUseCase(get()) }
    factory { GuardarRecetaUseCase(get()) }
    factory { EliminarRecetaUseCase(get()) }

    factory { ObserveComprasUseCase(get()) }
    factory { RegistrarCompraUseCase(get()) }

    factory { ObserveProduccionUseCase(get()) }
    factory { RegistrarProduccionUseCase(get()) }
    factory { ActualizarProduccionUseCase(get()) }
    factory { PublicarProduccionUseCase(get()) }
    factory { EliminarProduccionUseCase(get()) }

    factory { ObserveTurnosProduccionUseCase(get()) }
    factory { AgregarTurnoProduccionUseCase(get()) }
    factory { EliminarTurnoProduccionUseCase(get()) }

    factory { GetInventarioInsumosUseCase(get(), get(), get(), get()) }

    factory { ActualizarUbicacionFincaUseCase(get()) }
    factory { ObservarEstadoSincronizacionUseCase(get()) }
    factory { GetNotificacionesUseCase(get(), get(), get(), get(), get()) }

    factory { GetDashboardAdminUseCase(get(), get(), get()) }
    factory { GetDashboardPlantaUseCase(get(), get(), get(), get()) }
    factory { GetDashboardAcopiadorUseCase(get(), get()) }
    factory { GetDashboardProveedorUseCase(get(), get(), get()) }

    factory { GetReporteUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GenerarBoletaPdfUseCase(get(), get()) }
    factory { GenerarResultadoLabPdfUseCase(get(), get()) }
    factory { ExportarReportePdfUseCase(get(), get()) }
}
