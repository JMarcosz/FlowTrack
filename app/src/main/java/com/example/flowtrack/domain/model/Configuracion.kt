package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

data class ConfiguracionUsuario(
    val uidUsuario: String,
    val idioma: String = "es-DO",               // es-DO | en-US
    val formatoFecha: String = "dd/MM/yyyy",
    val formatoMoneda: String = "RD$ 0.00",
    val monedaPredeterminada: Moneda = Moneda.DOP,
    val temaOscuro: Boolean = false,
    val ultimoBackup: Instant? = null,
)

data class NotificacionConfig(
    val uidUsuario: String,
    val activa: Boolean = true,
    val pago7dias: Boolean = true,
    val pago3dias: Boolean = true,
    val pago1dia: Boolean = true,
    val pagoMismoDia: Boolean = true,
    val resumenMensual: Boolean = true,
    val alertasGastosAltos: Boolean = false,
    val umbralGastoAlto: BigDecimal = BigDecimal("5000"),
    val horaNotificacion: LocalTime = LocalTime.of(8, 0),
    val zonaHoraria: String = "America/Santo_Domingo",
)
