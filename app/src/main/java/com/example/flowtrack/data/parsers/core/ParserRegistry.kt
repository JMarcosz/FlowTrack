package com.example.flowtrack.data.parsers.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registro central de parsers. Detecta qué parser debe procesar un archivo
 * ejecutando puedeManejar() en todos los parsers registrados y ordenando
 * los resultados por confianza descendente.
 *
 * Los parsers se inyectan dinámicamente vía Hilt @IntoSet — no hay listas hardcoded.
 */
@Singleton
class ParserRegistry @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards BankParser>,
) {

    companion object {
        /** Confianza ≥ 0.8: procesar automáticamente sin confirmación del usuario. */
        const val UMBRAL_ALTA_CONFIANZA = 0.8f

        /** Confianza ≥ 0.4: mostrar top 3 candidatos al usuario. */
        const val UMBRAL_MEDIA_CONFIANZA = 0.4f
    }

    /**
     * Evalúa todos los parsers compatibles con la extensión del archivo
     * y devuelve el nivel de confianza más alto detectado.
     *
     * La evaluación es secuencial (no paralela) para mantener el orden predecible.
     * En profiling el overhead es <50ms en el peor caso con 4-5 parsers.
     */
    suspend fun detectar(archivo: ArchivoEntrada): ResultadoDeteccion {
        // Filtrar por extensión primero (barato), luego evaluar contenido
        val candidatos = parsers
            .filter { archivo.extension.lowercase() in it.formatosArchivo }
            .mapNotNull { parser ->
                val deteccion = parser.puedeManejar(archivo)
                if (deteccion.confianza > 0f) parser to deteccion else null
            }
            .sortedByDescending { it.second.confianza }

        if (candidatos.isEmpty()) {
            return ResultadoDeteccion.NoDetectado(
                mensaje = "Ningún parser registrado pudo identificar el archivo '${archivo.nombre}'.",
                bancosDisponibles = parsers.map { it.codigoBanco }.distinct(),
            )
        }

        val (mejorParser, mejorConfianza) = candidatos.first()

        return when {
            mejorConfianza.confianza >= UMBRAL_ALTA_CONFIANZA ->
                ResultadoDeteccion.AltaConfianza(mejorParser, mejorConfianza)

            mejorConfianza.confianza >= UMBRAL_MEDIA_CONFIANZA ->
                ResultadoDeteccion.MediaConfianza(
                    candidatos = candidatos.take(3),
                )

            else ->
                ResultadoDeteccion.BajaConfianza(
                    candidatos = candidatos,
                    bancosDisponibles = parsers.map { it.codigoBanco }.distinct(),
                )
        }
    }

    /** Lista todos los bancos con parser registrado. */
    fun bancosRegistrados(): List<String> = parsers.map { it.codigoBanco }.distinct().sorted()
}

// ─── Resultado de detección ───────────────────────────────────────────────────

sealed class ResultadoDeteccion {

    /** Confianza ≥ 0.8 → procesar automáticamente. */
    data class AltaConfianza(
        val parser: BankParser,
        val confianza: ConfianzaDeteccion,
    ) : ResultadoDeteccion()

    /** Confianza 0.4–0.8 → mostrar top 3 candidatos al usuario. */
    data class MediaConfianza(
        val candidatos: List<Pair<BankParser, ConfianzaDeteccion>>,
    ) : ResultadoDeteccion()

    /** Confianza < 0.4 → mostrar catálogo completo al usuario. */
    data class BajaConfianza(
        val candidatos: List<Pair<BankParser, ConfianzaDeteccion>>,
        val bancosDisponibles: List<String>,
    ) : ResultadoDeteccion()

    /** Ningún parser reconoció el archivo (extensión incompatible o formato desconocido). */
    data class NoDetectado(
        val mensaje: String,
        val bancosDisponibles: List<String>,
    ) : ResultadoDeteccion()
}
