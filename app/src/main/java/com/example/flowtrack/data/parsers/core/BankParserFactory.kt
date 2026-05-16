package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import javax.inject.Inject
import javax.inject.Singleton

/** Bancos que existen en la UI pero no tienen parser todavía. */
private val BANCOS_PROXIMAMENTE = setOf("BHD")

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
            val proximamente = bancoCodigo.uppercase() in BANCOS_PROXIMAMENTE
            Result.failure(
                ParserNoDisponibleException(
                    ErrorApp.ParserNoDisponible(bancoCodigo, proximamente)
                )
            )
        }
    }
}

class ParserNoDisponibleException(val error: ErrorApp.ParserNoDisponible) :
    Exception(
        if (error.proximamente) "${error.bancoCodigo} estará disponible próximamente."
        else "No hay parser disponible para ${error.bancoCodigo}."
    )
