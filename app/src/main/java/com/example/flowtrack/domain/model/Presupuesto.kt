package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Presupuesto(
    val id: String,
    val uidUsuario: String,
    val categoriaId: String,
    val montoLimite: BigDecimal,
    val periodo: PeriodoPresupuesto,
    val activo: Boolean = true,
    val creadoEn: Instant,
)

enum class PeriodoPresupuesto { MENSUAL, ANUAL }
