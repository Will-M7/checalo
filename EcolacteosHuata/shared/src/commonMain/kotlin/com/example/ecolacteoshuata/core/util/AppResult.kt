package com.example.ecolacteoshuata.core.util

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    /** [sinConexion] = el servidor no se pudo alcanzar (no es un rechazo de credenciales). */
    data class Error(val message: String, val sinConexion: Boolean = false) : AppResult<Nothing>()
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (String) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(message)
    return this
}
