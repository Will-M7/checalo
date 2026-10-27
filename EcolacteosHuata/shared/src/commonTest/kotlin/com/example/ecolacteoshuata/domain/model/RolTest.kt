package com.example.ecolacteoshuata.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RolTest {

    @Test
    fun fromStoredNameLeeElNombreActualDelEnum() {
        assertEquals(Rol.ADMIN, Rol.fromStoredName("ADMIN"))
        assertEquals(Rol.PERSONAL_PLANTA, Rol.fromStoredName("PERSONAL_PLANTA"))
    }

    @Test
    fun fromStoredNameToleraElAliasViejoPlantaSinCrashear() {
        assertEquals(Rol.PERSONAL_PLANTA, Rol.fromStoredName("PLANTA"))
    }

    @Test
    fun fromStoredNameSigueFallandoAnteUnValorRealmenteDesconocido() {
        assertFailsWith<IllegalArgumentException> { Rol.fromStoredName("ROL_INEXISTENTE") }
    }

    @Test
    fun fromServerValueMapeaLosRolesDeLaravel() {
        assertEquals(Rol.ADMIN, Rol.fromServerValue("administrador"))
        assertEquals(Rol.PERSONAL_PLANTA, Rol.fromServerValue("personal_planta"))
        assertNull(Rol.fromServerValue("rol_que_no_existe"))
    }
}
