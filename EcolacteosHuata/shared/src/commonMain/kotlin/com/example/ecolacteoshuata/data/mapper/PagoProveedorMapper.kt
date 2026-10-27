package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.domain.model.Comunicado
import com.example.ecolacteoshuata.domain.model.Empresa
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.PagoProveedor
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.TipoComunicado
import kotlinx.datetime.LocalDate
import com.example.ecolacteoshuata.db.Pago_proveedor as PagoProveedorEntity
import com.example.ecolacteoshuata.db.Comunicado as ComunicadoEntity
import com.example.ecolacteoshuata.db.Empresa as EmpresaEntity

fun PagoProveedorEntity.toDomain(): PagoProveedor = PagoProveedor(
    id = id,
    proveedorId = proveedor_id,
    recojoId = recojo_id,
    fecha = LocalDate.parse(fecha),
    monto = monto,
    metodoPago = MetodoPago.valueOf(metodo_pago),
    periodo = periodo,
)

fun ComunicadoEntity.toDomain(): Comunicado = Comunicado(
    id = id,
    tipo = TipoComunicado.valueOf(tipo),
    titulo = titulo,
    descripcion = descripcion,
    fecha = LocalDate.parse(fecha),
    autorId = autor_id,
    roles = roles?.let { Rol.decodificarLista(it) },
)

fun EmpresaEntity.toDomain(): Empresa = Empresa(
    id = id,
    razonSocial = razon_social,
    ruc = ruc,
    telefono = telefono,
    direccion = direccion,
)
