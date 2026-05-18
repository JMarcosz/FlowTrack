package com.example.flowtrack.data.firestore.dto

import com.google.firebase.Timestamp

/** DTO de Transaccion para serialización/deserialización con Firestore. */
data class TransaccionDto(
    val id: String = "",
    val uidUsuario: String = "",
    val cuentaId: String = "",
    val bancoCodigo: String = "",
    val fecha: Timestamp? = null,
    val fechaPosteo: Timestamp? = null,
    val descripcionCorta: String = "",
    val descripcionOriginal: String = "",
    val descripcionNormalizada: String = "",
    val monto: Double = 0.0,               // BigDecimal → Double (2 decimales, uso personal)
    val tipo: String = "",                  // TipoTransaccion.name
    val moneda: String = "",               // Moneda.name
    val balanceDespues: Double? = null,
    val referencia: String? = null,
    val serial: String? = null,
    val categoriaId: String? = null,
    val categoriaAutomatica: Boolean = false,
    val esDerivada: Boolean = false,
    val transaccionPadreId: String? = null,
    val derivadasIds: List<String> = emptyList(),
    val cargaId: String = "",
    val notaUsuario: String? = null,
    val metadataBanco: Map<String, String> = emptyMap(),
    val creadoEn: Timestamp? = null,
)

/** DTO de Cuenta para Firestore. */
data class CuentaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val bancoCodigo: String = "",
    val numeroCuenta: String = "",
    val numeroCuentaCompleto: String? = null,
    val alias: String = "",
    val tipoCuenta: String = "",           // TipoCuenta.name
    val moneda: String = "",               // Moneda.name
    val balanceActual: Double? = null,
    val balanceAlCorte: Double? = null,
    val fechaUltimoCorte: Timestamp? = null,
    val titular: String = "",
    val activa: Boolean = true,
    val mostrarEnDashboard: Boolean = true,
    val ultimaSincronizacion: Timestamp? = null,
    val creadoEn: Timestamp? = null,
)

/** DTO de Meta de ahorro para Firestore. */
data class MetaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val nombre: String = "",
    val emoji: String = "",
    val montoObjetivo: Double = 0.0,
    val montoActual: Double = 0.0,
    val fechaLimite: Timestamp? = null,
    val activa: Boolean = true,
    val creadoEn: Timestamp? = null,
)

/** DTO de Presupuesto para Firestore. */
data class PresupuestoDto(
    val id: String = "",
    val uidUsuario: String = "",
    val categoriaId: String = "",
    val montoLimite: Double = 0.0,
    val periodo: String = "MENSUAL",
    val activo: Boolean = true,
    val creadoEn: Timestamp? = null,
)

/** DTO de Tarjeta para Firestore. */
data class TarjetaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val bancoCodigo: String = "",
    val ultimos4: String = "",
    val alias: String = "",
    val tipoRed: String? = null,
    val limiteCredito: Double = 0.0,
    val moneda: String = "DOP",
    val diaCorte: Int = 1,
    val diaPago: Int = 1,
    val tasaInteresAnual: Double = 0.0,
    val tasaInteresOrigen: String = "AUTO_EXTRAIDA",
    val estado: String = "ACTIVO",
    val titular: String = "",
    val activa: Boolean = true,
    val ultimaSincronizacion: Timestamp? = null,
    val creadoEn: Timestamp? = null,
)

/** DTO de MovimientoTarjeta para Firestore. */
data class MovimientoTarjetaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val tarjetaId: String = "",
    val bancoCodigo: String = "",
    val fechaTransaccion: Timestamp? = null,
    val fechaPosteo: Timestamp? = null,
    val descripcionOriginal: String = "",
    val descripcionNormalizada: String = "",
    val monto: Double = 0.0,
    val tipoMovimiento: String = "",
    val moneda: String = "DOP",
    val numeroAutorizacion: String? = null,
    val categoriaId: String? = null,
    val categoriaAutomatica: Boolean = false,
    val cargaId: String = "",
    val metadataBanco: Map<String, String> = emptyMap(),
    val creadoEn: Timestamp? = null,
)

/** DTO de EstadoTarjetaSnap para Firestore. */
data class EstadoTarjetaSnapDto(
    val id: String = "",
    val uidUsuario: String = "",
    val tarjetaId: String = "",
    val fechaCorte: Timestamp? = null,
    val fechaLimitePago: Timestamp? = null,
    val periodoInicio: Timestamp? = null,
    val periodoFin: Timestamp? = null,
    val balanceAlCorte: Double = 0.0,
    val balanceAnterior: Double? = null,
    val pagoMinimo: Double = 0.0,
    val pagoTotal: Double = 0.0,
    val montoVencido: Double = 0.0,
    val balancePromedioDiario: Double? = null,
    val interesFinanciamiento: Double? = null,
    val cashbackGanado: Double? = null,
    val moneda: String = "DOP",
    val cargaId: String = "",
    val creadoEn: Timestamp? = null,
)

/** DTO de Carga para Firestore. */
data class CargaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val nombreArchivo: String = "",
    val tamanioBytes: Long = 0L,
    val mimeType: String? = null,
    val bancoCodigo: String = "",
    val parserVersion: Int = 0,
    val tipoDocumento: String = "",        // TipoDocumento.name
    val cuentaId: String? = null,
    val tarjetaId: String? = null,
    val periodoInicio: Timestamp? = null,
    val periodoFin: Timestamp? = null,
    val transaccionesInsertadas: Int = 0,
    val transaccionesDuplicadas: Int = 0,
    val advertencias: List<String> = emptyList(),
    val estado: String = "",               // EstadoCarga.name
    val procesadoEn: Timestamp? = null,
    val eliminadoEn: Timestamp? = null,
)
