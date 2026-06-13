package com.example.flowtrack.data.parsers.core

/**
 * Contrato de todos los parsers de estado de cuenta bancario.
 *
 * El banco, tipo de producto y formato de archivo están declarados en [key].
 * El registro de parsers usa [key] para el lookup. El banco siempre es elegido
 * manualmente por el usuario antes de importar — nunca hay auto-detección.
 */
interface BankStatementParser {

    /** Identificador único del parser. */
    val key: ParserKey

    /** Versión del parser. Se incrementa cuando cambia el formato del banco. */
    val version: Int

    /**
     * Parsea el estado de cuenta descrito en [request] y devuelve el resultado normalizado.
     * Las excepciones internas se convierten en resultados tipados para que la UI pueda
     * distinguir archivos inválidos de documentos cifrados.
     */
    suspend fun parse(request: ImportRequest): ParseResult
}
