package com.example.ecolacteoshuata.presentation.common.filtros

import com.example.ecolacteoshuata.domain.model.*
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class FiltrosLocalesTest {
    private fun usuario(id: String, nombre: String, activo: Boolean, roles: List<RolAsignado>) = Usuario(
        id, nombre, "Prueba", "12345678", "51999999999", Rol.VENDEDOR, "usuario$id", "hash-local", activo,
        LocalDateTime.parse("2026-10-27T10:00:00"), autorizacion = AutorizacionUsuario(id, version = "prueba", roles = roles),
    )
    private val vendedor = RolAsignado("vendedor", "Vendedor")
    private val nuevo = RolAsignado("supervisor_nuevo", "Supervisor nuevo")
    private val usuarios = listOf(usuario("1", "Flora", true, listOf(vendedor, nuevo)), usuario("2", "Flora", false, listOf(nuevo)), usuario("3", "Otra", true, emptyList()))

    @Test fun combinaTextoRolDinamicoYEstadoSinConexion() {
        assertEquals(listOf("1"), filtrarUsuarios(usuarios, "flora", nuevo.id, "1").map { it.id })
        assertEquals(listOf("1", "2"), filtrarUsuarios(usuarios, "Flora", nuevo.id).map { it.id })
        assertEquals(usuarios, filtrarUsuarios(usuarios, ""))
    }
    @Test fun variosRolesYSinRolesRespetanCatalogoDinamico() {
        assertEquals(listOf("1"), filtrarUsuarios(usuarios, "", cantidad = "varios").map { it.id })
        assertEquals(listOf("3"), filtrarUsuarios(usuarios, "", cantidad = "ninguno").map { it.id })
        assertTrue(usuarios[2].rolesParaFiltro().isEmpty())
    }
    @Test fun busquedaVaciaYAccionConservanIdentidadYDatosLocales() {
        val antes = usuarios.toList()
        assertTrue(filtrarUsuarios(usuarios, "inexistente").isEmpty())
        assertSame(usuarios[1], filtrarUsuarios(usuarios, "", estado = "0").single())
        assertEquals(antes, usuarios)
        assertEquals(listOf("supervisor_nuevo"), usuarios[1].rolesParaFiltro().map { it.first })
    }
    @Test fun disponibilidadYFechasSeCombinanSinCambiarStock() {
        assertTrue(coincideStock(2.0, "bajo"))
        assertFalse(coincideStock(0.0, "bajo"))
        assertTrue(coincideStock(0.0, "agotado"))
        assertFalse(coincideStock(0.0, "disponible"))
        assertTrue(coincideFecha("2026-10-27", "2026-10-01", "2026-10-31"))
        assertFalse(coincideFecha("2026-09-30", "2026-10-01", "2026-10-31"))
    }
}
