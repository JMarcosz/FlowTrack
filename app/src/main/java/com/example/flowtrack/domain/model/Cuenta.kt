package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Cuenta(
    val id: String,                         // hash(uid + bancoCodigo + numeroCuenta)
    val uidUsuario: String,
    val bancoCodigo: String,
    val numeroCuenta: String,               // últimos 4-10 dígitos visibles
    val numeroCuentaCompleto: String?,      // IBAN si está disponible
    val alias: String,                      // editable por usuario, ej: "Cuenta nómina"
    val tipoCuenta: TipoCuenta,
    val moneda: Moneda,
    val balanceActual: BigDecimal?,         // último balance conocido
    val balanceAlCorte: BigDecimal?,
    val fechaUltimoCorte: Instant? = null,  // fecha de fin del estado más reciente importado
    val titular: String,
    val activa: Boolean = true,
    val mostrarEnDashboard: Boolean = true, // v2: opcional ocultar en dashboard
    val ultimaSincronizacion: Instant?,
    val creadoEn: Instant,
)
