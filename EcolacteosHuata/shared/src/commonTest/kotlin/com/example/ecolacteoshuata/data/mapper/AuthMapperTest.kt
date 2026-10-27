package com.example.ecolacteoshuata.data.mapper

import com.example.ecolacteoshuata.data.remote.dto.LoginResponseDto
import com.example.ecolacteoshuata.domain.model.Rol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthMapperTest {
    private fun dto(rol: String?) = LoginResponseDto(
        id = "6", usuario = "lucho", nombres = "Luis", apellidos = "Quispe", rol = rol, token = "abc",
    )

    @Test
    fun convierteElRolDeLaravelAlDelDominio() {
        val usuario = dto("personal_planta").toDomain()

        assertEquals(Rol.PERSONAL_PLANTA, usuario?.rol)
        assertEquals("abc", usuario?.token)
    }

    @Test
    fun unRolDesconocidoNoSeMapea() {
        assertNull(dto("supervisor").toDomain())
        assertNull(dto(null).toDomain())
    }
}
