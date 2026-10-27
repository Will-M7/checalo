package com.example.ecolacteoshuata.core.util

object Validators {
    fun esDocumentoValido(documento: String): Boolean {
        val limpio = documento.trim()
        return limpio.length in 8..11 && limpio.all { it.isDigit() }
    }

    fun esCelularValido(celular: String): Boolean {
        val limpio = celular.trim()
        return limpio.length == 9 && limpio.all { it.isDigit() }
    }

    fun noVacio(texto: String): Boolean = texto.trim().isNotEmpty()

    fun esPrecioValido(precio: Double): Boolean = precio > 0.0
}
