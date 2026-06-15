package com.example.flowtrack.presentation.model

import java.math.BigDecimal

// Enum para estandarizar los períodos compartidos en todas las pantallas
enum class FiltroPeriodo(val label: String) {
    ESTE_MES("Este mes"),
    MES_PASADO("Mes pasado"),
    ULTIMOS_3_MESES("Últimos 3 meses"),
    ESTE_ANIO("Este año");

    companion object {
        fun fromLabel(label: String): FiltroPeriodo? = entries.find { it.label == label }
    }
}

// Objeto que encapsula el estado del dropdown de periodo
data class PeriodoState(
    val seleccionado: FiltroPeriodo = FiltroPeriodo.ESTE_MES
)

// Objeto para manejar los rangos de montos
data class RangoMonto(
    val minimo: BigDecimal? = null,
    val maximo: BigDecimal? = null
)

// Objeto principal para los filtros avanzados
data class FiltrosAvanzadosState(
    val bancoId: String? = null,
    val rangoMonto: RangoMonto = RangoMonto(),
    val categorias: Set<String> = emptySet(),
    val soloSinCategorizar: Boolean = false,
    val bancosDisponibles: List<String> = emptyList() // Datos necesarios para renderizar opciones en UI
) {
    val cantidadActivos: Int
        get() = (if (bancoId != null) 1 else 0) +
                (if (rangoMonto.minimo != null) 1 else 0) +
                (if (rangoMonto.maximo != null) 1 else 0) +
                categorias.size +
                (if (soloSinCategorizar) 1 else 0)
}
