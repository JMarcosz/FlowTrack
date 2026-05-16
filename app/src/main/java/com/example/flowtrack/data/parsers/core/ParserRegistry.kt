package com.example.flowtrack.data.parsers.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registro de parsers indexado por [ParserKey].
 *
 * No hay auto-detección: el usuario elige banco, producto y formato antes de
 * importar. El registry solo hace lookup por clave exacta.
 */
@Singleton
class ParserRegistry @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards BankStatementParser>,
) {

    private val index: Map<ParserKey, BankStatementParser> by lazy {
        parsers.associateBy { it.key }
    }

    /** Devuelve el parser para la clave dada, o null si no existe. */
    fun get(key: ParserKey): BankStatementParser? = index[key]

    /** Lista todas las claves de parsers registrados. */
    fun clavesRegistradas(): List<ParserKey> = index.keys.toList()
}
