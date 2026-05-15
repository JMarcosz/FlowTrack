package com.example.flowtrack.core.result

/**
 * Tipo resultado de dominio — reemplaza excepciones en la capa de dominio.
 * Las excepciones se atrapan en la capa data/ y se convierten en ErrorApp.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val error: ErrorApp) : AppResult<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error

    fun getOrNull(): T? = if (this is Success) data else null

    inline fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onError(block: (ErrorApp) -> Unit): AppResult<T> {
        if (this is Error) block(error)
        return this
    }

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
}

/** Errores de la app, tipados para el manejo en UI. */
sealed class ErrorApp {

    // Parser errors
    data class ParseError(val mensaje: String, val causa: Throwable? = null) : ErrorApp()
    data class FormatoIncompatible(val mensaje: String) : ErrorApp()
    data class ArchivoMuyGrande(val tamanioBytes: Long, val limiteBytes: Long) : ErrorApp()

    // Network / Firestore errors
    data class FirestoreError(val mensaje: String, val causa: Throwable? = null) : ErrorApp()
    data class SinConexion(val mensaje: String = "Sin conexión a Internet") : ErrorApp()

    // Auth errors
    data class NoAutenticado(val mensaje: String = "Sesión no iniciada") : ErrorApp()

    // Generic
    data class Desconocido(val mensaje: String, val causa: Throwable? = null) : ErrorApp()

    fun toMensajeUsuario(): String = when (this) {
        is ParseError -> "Error al procesar el archivo: $mensaje"
        is FormatoIncompatible -> "Formato de archivo no compatible: $mensaje"
        is ArchivoMuyGrande -> "El archivo es demasiado grande (${tamanioBytes / 1_048_576} MB). Límite: ${limiteBytes / 1_048_576} MB"
        is FirestoreError -> "Error de sincronización: $mensaje"
        is SinConexion -> mensaje
        is NoAutenticado -> mensaje
        is Desconocido -> "Error inesperado: $mensaje"
    }
}
