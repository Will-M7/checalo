package com.example.ecolacteoshuata.domain.model

enum class Rol {
    ADMIN, VENDEDOR, PRODUCCION, PERSONAL_PLANTA, ACOPIADOR, PROVEEDOR;

    /** Nombre para mostrar en pantalla (en vez de "PERSONAL_PLANTA"). */
    val etiqueta: String
        get() = when (this) {
            ADMIN -> "Administrador del sistema"
            VENDEDOR -> "Vendedor"
            PRODUCCION -> "Producción"
            PERSONAL_PLANTA -> "Personal de planta"
            ACOPIADOR -> "Acopiador"
            PROVEEDOR -> "Proveedor"
        }

    /** Valor del enum `App\Enums\Rol` de Laravel (inverso de [fromServerValue]). */
    val valorServidor: String
        get() = when (this) {
            ADMIN -> "administrador"
            VENDEDOR -> "vendedor"
            PRODUCCION -> "produccion"
            PERSONAL_PLANTA -> "personal_planta"
            ACOPIADOR -> "acopiador"
            PROVEEDOR -> "proveedor"
        }

    companion object {
        /** Mismo orden que `Rol::cases()` de Laravel, para listar los roles en pantalla. */
        val ordenLaravel: List<Rol> = listOf(ADMIN, PERSONAL_PLANTA, ACOPIADOR, PROVEEDOR, VENDEDOR, PRODUCCION)

        /** Lista de roles guardada en SQLite como nombres separados por coma ("ACOPIADOR,PROVEEDOR"). */
        fun codificarLista(roles: Collection<Rol>): String = roles.distinct().joinToString(",") { it.name }

        /** Inverso de [codificarLista]; ignora valores vacíos o desconocidos. */
        fun decodificarLista(texto: String?): Set<Rol> =
            texto.orEmpty().split(",").mapNotNull { v -> v.trim().takeIf { it.isNotEmpty() }?.let { runCatching { fromStoredName(it) }.getOrNull() } }.toSet()

        /** Convierte el valor del enum `App\Enums\Rol` de Laravel (ej. "administrador") al rol local. */
        fun fromServerValue(valor: String?): Rol? = when (valor) {
            "administrador" -> ADMIN
            "vendedor" -> VENDEDOR
            "produccion" -> PRODUCCION
            "personal_planta" -> PERSONAL_PLANTA
            "acopiador" -> ACOPIADOR
            "proveedor" -> PROVEEDOR
            else -> null
        }

        /**
         * Convierte el nombre guardado en SQLite. Tolera alias viejos de antes de un rename
         * (ej. "PLANTA" → PERSONAL_PLANTA) para que una fila con un valor legado no crashee la
         * app entera; la migración `3.sqm` ya corrige el dato, esto es una segunda red de seguridad.
         */
        fun fromStoredName(valor: String): Rol = runCatching { valueOf(valor) }.getOrElse {
            when (valor) {
                "PLANTA" -> PERSONAL_PLANTA
                else -> throw it
            }
        }
    }
}

enum class TurnoTipo {
    RUTA, PRODUCCION
}

enum class EstadoLeche {
    NORMAL, SOLEADO, ACIDIFICADO, AGUADO, OTRO
}

enum class MetodoPago {
    EFECTIVO, YAPE, PLIN, TARJETA, OTRO
}

enum class TipoComunicado {
    COMUNICADO, ASESORAMIENTO
}

enum class ResultadoTipo {
    CONFORME, SOLEADO, ACIDIFICADO, AGUADO, OTRO,

    /** Lo que guarda Laravel cuando se apaga "Resultado conforme" ("no conforme"). */
    NO_CONFORME;

    companion object {
        /** Acepta el valor local ("NO_CONFORME") y el de Laravel ("no conforme"); lo desconocido es OTRO. */
        fun desde(valor: String): ResultadoTipo =
            entries.firstOrNull { it.name == valor.trim().uppercase().replace(' ', '_') } ?: OTRO
    }
}

enum class DescuentoTipo {
    NINGUNO, FAVORITO, MAYORISTA
}

enum class EstadoSincronizacion {
    SIN_SENAL, SINCRONIZANDO, SINCRONIZADO,
    /** No hay servidor Laravel configurado: la app trabaja solo con sus datos locales. */
    MODO_LOCAL,
}

enum class Periodo {
    TURNO, DIA, SEMANA, MES, ANIO
}
