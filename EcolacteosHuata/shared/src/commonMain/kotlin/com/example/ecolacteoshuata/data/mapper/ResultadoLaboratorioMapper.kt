package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import kotlinx.datetime.LocalDate
import com.example.ecolacteoshuata.db.Resultado_laboratorio as ResultadoLaboratorioEntity

fun ResultadoLaboratorioEntity.toDomain(): ResultadoLaboratorio = ResultadoLaboratorio(
    id = id,
    proveedorId = proveedor_id,
    fecha = LocalDate.parse(fecha),
    turnoId = turno_id,
    resultado = ResultadoTipo.desde(resultado),
    densidad = densidad,
    acidez = acidez,
    temperatura = temperatura,
    observaciones = observaciones,
    conforme = conforme == 1L,
    litros = litros,
    grasa = grasa,
    sng = sng,
    proteina = proteina,
    lactosa = lactosa,
    sales = sales,
    totalSolidos = total_solidos,
    aguaAnadida = agua_anadida,
    puntoCongelacion = punto_congelacion,
    ph = ph,
    sn = sn,
    modo = modo,
    horaAnalisis = hora_analisis,
    fechaAnalisis = fecha_analisis,
    enviadoProduccion = enviado_produccion == 1L,
)
