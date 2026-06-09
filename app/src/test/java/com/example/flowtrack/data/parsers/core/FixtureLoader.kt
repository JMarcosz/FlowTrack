package com.example.flowtrack.data.parsers.core

import java.io.File

/**
 * Localizador de fixtures para tests de parsers.
 *
 * Estrategia en dos pasos:
 *   1. Fixture sintético en el classpath (`app/src/test/resources/fixtures/<recursoSintetico>`).
 *      Es el que usa CI: datos falsos, mismo formato, commiteable.
 *   2. Fixture real local en `docs/03-fixtures/` (gitignored, datos bancarios reales del usuario).
 *      Se busca por *patrón de substring* (case-insensitive) en vez de nombre exacto, porque los
 *      exports reales de banco traen nombres arbitrarios (ej. "Asociacion Cibao.xls",
 *      "Banco Popular Dominicano 026.csv").
 *
 * El working dir de los tests JVM es el módulo `app/`, por eso `../docs/03-fixtures`.
 */
object FixtureLoader {

    private val DIR_REAL = File("../docs/03-fixtures")

    /**
     * Devuelve los bytes del fixture, o `null` si no se encuentra ni el sintético ni el real.
     *
     * @param recursoSintetico nombre del archivo en `test/resources/fixtures/` (ej. "cibao_v1.xls")
     * @param patronesReales substrings a buscar en los nombres de `docs/03-fixtures/`
     *                       (ej. "cibao"); el primero que matchee gana.
     */
    fun cargar(recursoSintetico: String, vararg patronesReales: String): ByteArray? {
        FixtureLoader::class.java.classLoader
            ?.getResourceAsStream("fixtures/$recursoSintetico")
            ?.use { return it.readBytes() }

        val archivoReal = DIR_REAL
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.firstOrNull { f ->
                f.isFile && patronesReales.any { p -> f.name.contains(p, ignoreCase = true) }
            }
        return archivoReal?.readBytes()
    }

    /** True si existe al menos un fixture real local que matchee alguno de los patrones. */
    fun existeReal(vararg patronesReales: String): Boolean =
        DIR_REAL.takeIf { it.isDirectory }
            ?.listFiles()
            ?.any { f -> f.isFile && patronesReales.any { p -> f.name.contains(p, ignoreCase = true) } }
            ?: false
}
