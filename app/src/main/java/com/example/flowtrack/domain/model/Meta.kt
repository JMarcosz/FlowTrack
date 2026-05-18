package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Meta(
    val id: String,
    val uidUsuario: String,
    val nombre: String,
    val emoji: String,
    val montoObjetivo: BigDecimal,
    val montoActual: BigDecimal,
    val fechaLimite: Instant?,
    val activa: Boolean = true,
    val creadoEn: Instant,
) {
    val porcentaje: Float get() =
        if (montoObjetivo > BigDecimal.ZERO)
            (montoActual.toFloat() / montoObjetivo.toFloat()).coerceIn(0f, 1f)
        else 0f

    val completada: Boolean get() = montoActual >= montoObjetivo
}
