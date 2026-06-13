package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class TasaCambio(
    val compra: BigDecimal,
    val venta: BigDecimal,
    val fecha: LocalDate,
    val fuente: String // "BCRD" o "TasaReal" o "Firebase"
)