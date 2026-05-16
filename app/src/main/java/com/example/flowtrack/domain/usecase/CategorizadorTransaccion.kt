package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch

/**
 * @deprecated Usar [MotorCategorizacion] directamente.
 */
@Deprecated("Usar MotorCategorizacion.categorizar / categorizarLote")
class CategorizadorTransaccion(
    reglas: List<ReglaCategoria>,
) {
    // Reglas activas ordenadas por prioridad descendente — orden fijo al construir
    private val reglasSorted: List<ReglaCategoria> =
        reglas.filter { it.activa }.sortedByDescending { it.prioridad }

    /**
     * Clasifica una descripción de transacción.
     * @param descripcion texto crudo (se normaliza internamente)
     * @return categoriaId de la primera regla que hace match, o null si ninguna aplica
     */
    fun clasificar(descripcion: String): String? {
        if (descripcion.isBlank()) return null
        val normalizada = descripcion.normalizarDescripcion()

        for (regla in reglasSorted) {
            if (regla.patron.isBlank()) continue
            val patronNorm = regla.patron.normalizarDescripcion()
            val match = when (regla.tipoMatch) {
                TipoMatch.EXACTO -> normalizada == patronNorm
                TipoMatch.CONTIENE -> normalizada.contains(patronNorm)
                TipoMatch.EMPIEZA_CON -> normalizada.startsWith(patronNorm)
                TipoMatch.REGEX -> {
                    try {
                        Regex(regla.patron, RegexOption.IGNORE_CASE).containsMatchIn(descripcion)
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            if (match) return regla.categoriaId
        }
        return null
    }

    /**
     * Clasifica una descripción y retorna la categoriaId junto con la regla que hizo match.
     * Útil para logging y para incrementar el contador de confianza en la regla.
     */
    fun clasificarConRegla(descripcion: String): Pair<String?, ReglaCategoria?> {
        if (descripcion.isBlank()) return Pair(null, null)
        val normalizada = descripcion.normalizarDescripcion()

        for (regla in reglasSorted) {
            if (regla.patron.isBlank()) continue
            val patronNorm = regla.patron.normalizarDescripcion()
            val match = when (regla.tipoMatch) {
                TipoMatch.EXACTO -> normalizada == patronNorm
                TipoMatch.CONTIENE -> normalizada.contains(patronNorm)
                TipoMatch.EMPIEZA_CON -> normalizada.startsWith(patronNorm)
                TipoMatch.REGEX -> {
                    try {
                        Regex(regla.patron, RegexOption.IGNORE_CASE).containsMatchIn(descripcion)
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            if (match) return Pair(regla.categoriaId, regla)
        }
        return Pair(null, null)
    }
}
