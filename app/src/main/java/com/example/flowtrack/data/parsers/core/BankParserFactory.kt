package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fachada que resuelve el [BankStatementParser] correcto para una combinación de
 * banco + producto + formato. El banco siempre es elegido por el usuario antes de
 * llamar a este factory — nunca hay auto-detección.
 */
@Singleton
class BankParserFactory @Inject constructor(
    private val registry: ParserRegistry,
) {
    /**
     * Devuelve el parser si existe, o un [ErrorApp] tipado si el banco no tiene
     * implementación (distinguiendo "próximamente" de "desconocido").
     */
    fun obtenerParser(
        bancoCodigo: String,
        productoTipo: ProductoTipo,
        formato: FileFormat,
    ): Result<BankStatementParser> {
        val parser = registry.get(ParserKey(bancoCodigo, productoTipo, formato))
        return if (parser != null) {
            Result.success(parser)
        } else {
            Result.failure(
                ParserNoDisponibleException(
                    ErrorApp.ParserNoDisponible(bancoCodigo)
                )
            )
        }
    }
}

class ParserNoDisponibleException(val error: ErrorApp.ParserNoDisponible) :
    Exception(
        "No hay parser disponible para ${error.bancoCodigo}."
    )
