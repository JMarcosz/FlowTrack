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
    val titular: String = "",
    val activa: Boolean = true,
    val mostrarEnDashboard: Boolean = true,
    val ultimaSincronizacion: Timestamp? = null,
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
    val bancoDetectadoAutomaticamente: Boolean = false,
    val confianzaDeteccion: Float = 0f,
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
)
