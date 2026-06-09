package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Tarjeta(
    val id: String,                         // hash(uid + bancoCodigo + ultimos4)
    val uidUsuario: String,
    val bancoCodigo: String,
    val ultimos4: String,
    val alias: String,                      // editable, ej: "Qik Visa Clásica"
    val tipoRed: String?,                   // "VISA", "MASTERCARD"
    val limiteCredito: BigDecimal,
    val moneda: Moneda,
    val diaCorte: Int,                      // 1–31
    val diaPago: Int,                       // 1–31
    val tasaInteresAnual: Double,           // ej: 60.0
    val tasaInteresOrigen: OrigenTasa,      // v2: AUTO_EXTRAIDA | MANUAL
    val estado: EstadoTarjeta = EstadoTarjeta.ACTIVO, // v2
    val titular: String,
    val activa: Boolean = true,
    val ultimaSincronizacion: Instant?,
    val creadoEn: Instant,
)

/** Snapshot de un corte de tarjeta. */
data class EstadoTarjetaSnap(
    val id: String,                         // hash(tarjetaId + fechaCorte)
    val uidUsuario: String,
    val tarjetaId: String,
    val fechaCorte: Instant,
    val fechaLimitePago: Instant,
    val periodoInicio: Instant,
    val periodoFin: Instant,
    val balanceAlCorte: BigDecimal,
    val balanceAnterior: BigDecimal?,
    val pagoMinimo: BigDecimal,
    val pagoTotal: BigDecimal,
    val montoVencido: BigDecimal,
    val balancePromedioDiario: BigDecimal?,
    val interesFinanciamiento: BigDecimal?,
    val cashbackGanado: BigDecimal?,
    val moneda: Moneda,
    val cargaId: String,
    val creadoEn: Instant,
)

data class MovimientoTarjeta(
    val id: String,                         // hash determinístico
    val uidUsuario: String,
    val tarjetaId: String,
    val bancoCodigo: String,
    val fechaTransaccion: Instant,
    val fechaPosteo: Instant?,
    val descripcionOriginal: String,
    val descripcionNormalizada: String,
    val monto: BigDecimal,                  // monto en la moneda base (DOP); siempre ≥ 0
    val montoUsd: BigDecimal? = null,       // monto paralelo en USD (Cibao bimoneda); null si no aplica
    val tipoMovimiento: TipoMovimientoTarjeta,
    val moneda: Moneda,
    val numeroAutorizacion: String?,
    val categoriaId: String?,
    val categoriaAutomatica: Boolean,
    val cargaId: String,
    val metadataBanco: Map<String, String> = emptyMap(),
    val creadoEn: Instant,
)


