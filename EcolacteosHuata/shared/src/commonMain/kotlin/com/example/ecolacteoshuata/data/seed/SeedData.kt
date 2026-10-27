package com.example.ecolacteoshuata.data.seed

import com.example.ecolacteoshuata.core.util.IdGenerator
import com.example.ecolacteoshuata.core.util.PasswordHasher
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.db.AppDatabase
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.TipoComunicado
import kotlinx.datetime.LocalDate

class SeedData(private val db: AppDatabase) {

    fun sembrarSiVacio() {
        val yaExiste = db.usuarioQueries.countAll().executeAsOne() > 0
        if (yaExiste) return

        db.transaction {
            sembrarEmpresaYPrecio()
            val usuarios = sembrarUsuarios()
            val acopiadores = sembrarAcopiadores(usuarios)
            val turnos = sembrarTurnos(acopiadores)
            val proveedores = sembrarProveedores(usuarios, acopiadores)
            sembrarRutas(acopiadores, proveedores)
            sembrarClientes()
            sembrarRecojos(proveedores, acopiadores, turnos)
            sembrarLaboratorio(proveedores, turnos)
            sembrarComunicados(usuarios)
            sembrarProduccion()
        }
    }

    private fun sembrarEmpresaYPrecio() {
        db.empresaQueries.insert(
            id = IdGenerator.newId(),
            razon_social = "Asociación Ecolácteos Huata",
            ruc = "20601234567",
            telefono = "051 365 210",
            direccion = "Av. Circunvalación 240, Huata",
        )
        db.precioLecheQueries.insert(IdGenerator.newId(), 1.90, todayLocalDate().toString())
    }

    private fun crearUsuario(
        nombres: String,
        apellidos: String,
        documento: String,
        celular: String,
        rol: Rol,
        usuario: String,
    ): String {
        val id = IdGenerator.newId()
        db.usuarioQueries.insert(
            id = id,
            nombres = nombres,
            apellidos = apellidos,
            documento = documento,
            celular = celular,
            rol = rol.name,
            usuario = usuario,
            password_hash = PasswordHasher.hash("1234"),
            activo = 1L,
            creado_en = nowLocalDateTime().toString(),
        )
        return id
    }

    private data class UsuariosSeed(
        val adminId: String,
        val vendedorId: String,
        val produccionId: String,
        val plantaId: String,
        val acopio1Id: String,
        val acopio2Id: String,
        val acopio3Id: String,
        val prov1Id: String,
        val prov2Id: String,
        val prov3Id: String,
        val prov4Id: String,
        val prov5Id: String,
        val prov6Id: String,
    )

    private fun sembrarUsuarios(): UsuariosSeed {
        val adminId = crearUsuario("Nilda", "Huanca Cutipa", "01111111", "951111111", Rol.ADMIN, "admin")
        val vendedorId = crearUsuario("Rosa", "Flores Mamani", "01111222", "951111222", Rol.VENDEDOR, "vendedor")
        val produccionId = crearUsuario("Edwin", "Calcina Turpo", "01111333", "951111333", Rol.PRODUCCION, "produccion")
        val plantaId = crearUsuario("Teodoro", "Cachi Larico", "01222222", "951222222", Rol.PERSONAL_PLANTA, "planta")
        val acopio1Id = crearUsuario("Rufino", "Quispe Mamani", "01333333", "951333333", Rol.ACOPIADOR, "acopio")
        val acopio2Id = crearUsuario("Elsa", "Condori Apaza", "01333334", "951333334", Rol.ACOPIADOR, "acopio2")
        val acopio3Id = crearUsuario("Mario", "Chávez Ticona", "01333335", "951333335", Rol.ACOPIADOR, "acopio3")
        val prov1Id = crearUsuario("Basilia", "Yupanqui Chura", "01444444", "951444444", Rol.PROVEEDOR, "proveedor")
        val prov2Id = crearUsuario("Gregorio", "Ccama Pari", "01444445", "951444445", Rol.PROVEEDOR, "proveedor2")
        val prov3Id = crearUsuario("Martina", "Apaza Quilca", "01444446", "951444446", Rol.PROVEEDOR, "proveedor3")
        val prov4Id = crearUsuario("Isidro", "Cutipa Ticona", "01444447", "951444447", Rol.PROVEEDOR, "proveedor4")
        val prov5Id = crearUsuario("Julia", "Mamani Choque", "01444448", "951444448", Rol.PROVEEDOR, "proveedor5")
        val prov6Id = crearUsuario("Feliciano", "Quilca Roque", "01444449", "951444449", Rol.PROVEEDOR, "proveedor6")
        return UsuariosSeed(adminId, vendedorId, produccionId, plantaId, acopio1Id, acopio2Id, acopio3Id, prov1Id, prov2Id, prov3Id, prov4Id, prov5Id, prov6Id)
    }

    private data class AcopiadoresSeed(val acopio1Id: String, val acopio2Id: String, val acopio3Id: String)

    private fun sembrarAcopiadores(usuarios: UsuariosSeed): AcopiadoresSeed {
        val acopio1Id = IdGenerator.newId()
        db.acopiadorQueries.insert(acopio1Id, usuarios.acopio1Id, "Ruta Huata Centro")
        val acopio2Id = IdGenerator.newId()
        db.acopiadorQueries.insert(acopio2Id, usuarios.acopio2Id, "Ruta Coata")
        val acopio3Id = IdGenerator.newId()
        db.acopiadorQueries.insert(acopio3Id, usuarios.acopio3Id, "Ruta Paucarcolla")
        return AcopiadoresSeed(acopio1Id, acopio2Id, acopio3Id)
    }

    private data class TurnosSeed(val turno1Manana: String, val turno1Tarde: String, val turno2Manana: String, val turno2Tarde: String, val turno3Manana: String)

    private fun sembrarTurnos(acopiadores: AcopiadoresSeed): TurnosSeed {
        val t1m = IdGenerator.newId()
        db.turnoQueries.insert(t1m, acopiadores.acopio1Id, "Mañana", "05:30", "08:00")
        val t1t = IdGenerator.newId()
        db.turnoQueries.insert(t1t, acopiadores.acopio1Id, "Tarde", "16:00", "18:30")
        val t2m = IdGenerator.newId()
        db.turnoQueries.insert(t2m, acopiadores.acopio2Id, "Mañana", "05:00", "07:40")
        val t2t = IdGenerator.newId()
        db.turnoQueries.insert(t2t, acopiadores.acopio2Id, "Tarde", "16:30", "18:00")
        val t3m = IdGenerator.newId()
        db.turnoQueries.insert(t3m, acopiadores.acopio3Id, "Mañana", "05:45", "08:15")
        return TurnosSeed(t1m, t1t, t2m, t2t, t3m)
    }

    private data class ProveedoresSeed(
        val prov1Id: String, val prov2Id: String, val prov3Id: String,
        val prov4Id: String, val prov5Id: String, val prov6Id: String,
    )

    private fun sembrarProveedores(usuarios: UsuariosSeed, acopiadores: AcopiadoresSeed): ProveedoresSeed {
        val prov1Id = IdGenerator.newId()
        db.proveedorQueries.insert(
            prov1Id, usuarios.prov1Id, "Estancia Chocasuyo, Huata", "A 300 m de la escuela 70123",
            -15.7853, -70.0219, acopiadores.acopio1Id, 0L, 0L,
        )
        val prov2Id = IdGenerator.newId()
        db.proveedorQueries.insert(
            prov2Id, usuarios.prov2Id, "Sector Coricancha, Huata", "Frente al canal de riego",
            -15.7901, -70.0154, acopiadores.acopio1Id, 0L, 1L,
        )
        val prov3Id = IdGenerator.newId()
        db.proveedorQueries.insert(
            prov3Id, usuarios.prov3Id, "Comunidad Huancollo, Huata", "Casa de techo rojo, km 4",
            -15.7799, -70.0301, acopiadores.acopio1Id, 0L, 0L,
        )
        val prov4Id = IdGenerator.newId()
        db.proveedorQueries.insert(
            prov4Id, usuarios.prov4Id, "Estancia Jayllihuaya Alta, Coata", "Junto al corral de piedra",
            -15.7412, -70.0532, acopiadores.acopio2Id, 0L, 2L,
        )
        val prov5Id = IdGenerator.newId()
        db.proveedorQueries.insert(
            prov5Id, usuarios.prov5Id, "Sector Chullunquiani, Coata", "Desvío del camino a Capachica",
            -15.7365, -70.0489, acopiadores.acopio2Id, 0L, 0L,
        )
        val prov6Id = IdGenerator.newId()
        db.proveedorQueries.insert(
            prov6Id, usuarios.prov6Id, "Estancia Uros Titino, Paucarcolla", "Detrás de la capilla",
            -15.8102, -69.9987, acopiadores.acopio3Id, 0L, 3L,
        )
        return ProveedoresSeed(prov1Id, prov2Id, prov3Id, prov4Id, prov5Id, prov6Id)
    }

    private fun sembrarRutas(acopiadores: AcopiadoresSeed, proveedores: ProveedoresSeed) {
        val ruta1Id = IdGenerator.newId()
        db.rutaQueries.insert(ruta1Id, acopiadores.acopio1Id, "Ruta Huata Centro", 1L)
        listOf(proveedores.prov1Id, proveedores.prov2Id, proveedores.prov3Id).forEachIndexed { i, id ->
            db.rutaQueries.asignarProveedor(ruta1Id, id, i + 1L)
        }
        val ruta2Id = IdGenerator.newId()
        db.rutaQueries.insert(ruta2Id, acopiadores.acopio2Id, "Ruta Coata", 1L)
        listOf(proveedores.prov4Id, proveedores.prov5Id).forEachIndexed { i, id ->
            db.rutaQueries.asignarProveedor(ruta2Id, id, i + 1L)
        }
        val ruta3Id = IdGenerator.newId()
        db.rutaQueries.insert(ruta3Id, acopiadores.acopio3Id, "Ruta Paucarcolla", 1L)
        db.rutaQueries.asignarProveedor(ruta3Id, proveedores.prov6Id, 1L)
    }

    private fun sembrarClientes() {
        db.clienteQueries.insert(IdGenerator.newId(), "Rosa", "Pacompía Luque", "01223344", 24L, 1L, 12.0, 0L, 0.0)
        db.clienteQueries.insert(IdGenerator.newId(), "Minimarket Bellavista S.A.C.", "", "20487654321", 41L, 1L, 8.0, 1L, 6.0)
        db.clienteQueries.insert(IdGenerator.newId(), "Pizzería Titikaka E.I.R.L.", "", "20512345678", 17L, 0L, 0.0, 1L, 6.0)
        db.clienteQueries.insert(IdGenerator.newId(), "Julián", "Cruz Mamani", "01998877", 3L, 0L, 0.0, 0L, 0.0)
    }

    private fun sembrarRecojos(proveedores: ProveedoresSeed, acopiadores: AcopiadoresSeed, turnos: TurnosSeed) {
        val hoy = todayLocalDate()
        val precio = 1.90

        fun recojo(proveedorId: String, acopiadorId: String, turnoId: String, litros: Double, estado: EstadoLeche, pagado: Boolean, fecha: LocalDate = hoy) {
            db.recojoQueries.insert(
                id = IdGenerator.newId(),
                proveedor_id = proveedorId,
                acopiador_id = acopiadorId,
                turno_id = turnoId,
                fecha = fecha.toString(),
                litros = litros,
                estado_leche = estado.name,
                estado_otro = "",
                precio_litro = precio,
                total = litros * precio,
                pagado = if (pagado) 1L else 0L,
                metodo_pago = if (pagado) MetodoPago.EFECTIVO.name else null,
                entregado = 0L,
                sincronizado = 1L,
            )
        }

        recojo(proveedores.prov1Id, acopiadores.acopio1Id, turnos.turno1Manana, 38.0, EstadoLeche.NORMAL, pagado = false)
        recojo(proveedores.prov2Id, acopiadores.acopio1Id, turnos.turno1Manana, 52.0, EstadoLeche.NORMAL, pagado = true)
        recojo(proveedores.prov4Id, acopiadores.acopio2Id, turnos.turno2Manana, 44.0, EstadoLeche.ACIDIFICADO, pagado = false)
        recojo(proveedores.prov5Id, acopiadores.acopio2Id, turnos.turno2Manana, 61.0, EstadoLeche.NORMAL, pagado = true)
        recojo(proveedores.prov6Id, acopiadores.acopio3Id, turnos.turno3Manana, 29.0, EstadoLeche.SOLEADO, pagado = false)
    }

    private fun sembrarLaboratorio(proveedores: ProveedoresSeed, turnos: TurnosSeed) {
        val hoy = todayLocalDate()

        fun resultado(proveedorId: String, turnoId: String?, resultado: ResultadoTipo, fecha: LocalDate = hoy) {
            db.resultadoLaboratorioQueries.insert(
                id = IdGenerator.newId(),
                proveedor_id = proveedorId,
                fecha = fecha.toString(),
                turno_id = turnoId,
                resultado = resultado.name,
                densidad = 1.030,
                acidez = 16.0,
                temperatura = 4.0,
                observaciones = "",
                conforme = if (resultado == ResultadoTipo.CONFORME) 1L else 0L,
                litros = null,
                grasa = null,
                sng = null,
                proteina = null,
                lactosa = null,
                sales = null,
                total_solidos = null,
                agua_anadida = null,
                punto_congelacion = null,
                ph = null,
                sn = null,
                modo = null,
                hora_analisis = null,
                fecha_analisis = null,
            )
        }

        resultado(proveedores.prov1Id, turnos.turno1Manana, ResultadoTipo.CONFORME, hoy)
        resultado(proveedores.prov1Id, turnos.turno1Manana, ResultadoTipo.CONFORME, hoy)
        resultado(proveedores.prov1Id, turnos.turno1Manana, ResultadoTipo.ACIDIFICADO, hoy)
        resultado(proveedores.prov2Id, turnos.turno1Manana, ResultadoTipo.AGUADO, hoy)
        resultado(proveedores.prov5Id, turnos.turno2Manana, ResultadoTipo.CONFORME, hoy)
        resultado(proveedores.prov6Id, turnos.turno3Manana, ResultadoTipo.ACIDIFICADO, hoy)
    }

    private fun sembrarComunicados(usuarios: UsuariosSeed) {
        val hoy = todayLocalDate()
        db.comunicadoQueries.insert(
            IdGenerator.newId(), TipoComunicado.ASESORAMIENTO.name, "Ordeño limpio: 3 pasos",
            "Lávese las manos, limpie la ubre y use utensilios desinfectados antes de cada ordeño.",
            hoy.toString(), usuarios.plantaId, null,
        )
        db.comunicadoQueries.insert(
            IdGenerator.newId(), TipoComunicado.COMUNICADO.name, "Pago de la segunda quincena",
            "Los pagos de la segunda quincena se realizarán el próximo viernes en la planta.",
            hoy.toString(), usuarios.plantaId, null,
        )
    }

    private fun sembrarProduccion() {
        val hoy = todayLocalDate()

        val kgId = IdGenerator.newId()
        db.unidadMedidaQueries.insert(kgId, "Kilogramo", "kg")
        val lId = IdGenerator.newId()
        db.unidadMedidaQueries.insert(lId, "Litro", "L")

        val cuajoId = IdGenerator.newId()
        db.insumoQueries.insert(cuajoId, "Cuajo", lId, 2.0, 8.5, 10.0, 10.0)
        val salId = IdGenerator.newId()
        db.insumoQueries.insert(salId, "Sal", kgId, 5.0, 3.0, 20.0, 20.0)
        val fermentoId = IdGenerator.newId()
        db.insumoQueries.insert(fermentoId, "Fermento láctico", lId, 1.0, 0.0, 0.0, 0.0)

        val recetaQuesoId = IdGenerator.newId()
        db.recetaQueries.insert(recetaQuesoId, "Queso paria", "Receta base para queso paria de 1 kg", null)
        db.recetaQueries.insertDetalle(IdGenerator.newId(), recetaQuesoId, cuajoId, 0.5)
        db.recetaQueries.insertDetalle(IdGenerator.newId(), recetaQuesoId, salId, 0.3)

        val compraId = IdGenerator.newId()
        db.compraQueries.insert(compraId, hoy.toString(), "Insumos Puno S.R.L.", "Compra mensual de insumos", 145.0)
        db.compraQueries.insertDetalle(IdGenerator.newId(), compraId, cuajoId, 10.0, 8.5, 85.0)
        db.compraQueries.insertDetalle(IdGenerator.newId(), compraId, salId, 20.0, 3.0, 60.0)

        db.produccionQueries.insert(IdGenerator.newId(), hoy.toString(), recetaQuesoId, null, 4.0, 4.0, "Lote de la mañana", null, "Queso paria", "L-DEMO-001", null)

        val turnoProdId = IdGenerator.newId()
        db.turnoProduccionQueries.insert(turnoProdId, "Turno mañana", "06:00", "12:00")
    }
}
