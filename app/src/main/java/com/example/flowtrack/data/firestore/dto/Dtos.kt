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
    val monto: String = "0.00",            // BigDecimal serializado como String canónico
    val tipo: String = "",                  // TipoTransaccion.name
    val moneda: String = "",               // Moneda.name
    val balanceDespues: String? = null,
    val referencia: String? = null,
    val serial: String? = null,
    val categoriaId: String? = null,
    val categoriaAutomatica: Boolean = false,
    val esDerivada: Boolean = false,
    val transaccionPadreId: String? = null,
    val derivadasIds: List<String> = emptyList(),
    val origen: String = "IMPORTACION_ARCHIVO",
    val sourceEventId: String? = null,
    val sourceMessageId: String? = null,
    val sourceTransactionId: String? = null,
    val actualizadoEn: Timestamp? = null,
    val estado: String = "APROBADA",
    val afectaBalance: Boolean = true,
    val posibleDuplicado: Boolean = false,
    val motivoRechazo: String? = null,
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
    val balanceActual: String? = null,
    val balanceAlCorte: String? = null,
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
    val montoObjetivo: String = "0.00",
    val montoActual: String = "0.00",
    val fechaLimite: Timestamp? = null,
    val activa: Boolean = true,
    val creadoEn: Timestamp? = null,
    val descripcion: String? = null,
    val categoria: String = "OTRO",
    val cuentaId: String? = null,
    val fechaObjetivo: Timestamp? = null,
    val estado: String = "ACTIVA",
    val actualizadaEn: Timestamp? = null,
)

/** DTO de movimiento de meta para Firestore. */
data class MovimientoMetaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val metaId: String = "",
    val cuentaId: String? = null,
    val tipo: String = "",
    val monto: String = "0.00",
    val balanceAntes: String = "0.00",
    val balanceDespues: String = "0.00",
    val metaDestinoId: String? = null,
    val requestId: String = "",
    val creadoEn: Timestamp? = null,
)

/** DTO de Presupuesto para Firestore. */
data class PresupuestoDto(
    val id: String = "",
    val uidUsuario: String = "",
    val categoriaId: String = "",
    val montoLimite: String = "0.00",
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
    val limiteCredito: String = "0.00",
    val moneda: String = "DOP",
    val diaCorte: Int = 1,
    val diaPago: Int = 1,
    val tasaInteresAnual: String = "0.00",
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
    val monto: String = "0.00",
    val montoUsd: String? = null,
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
    val balanceAlCorte: String = "0.00",
    val balanceAnterior: String? = null,
    val pagoMinimo: String = "0.00",
    val pagoTotal: String = "0.00",
    val montoVencido: String = "0.00",
    val balancePromedioDiario: String? = null,
    val interesFinanciamiento: String? = null,
    val cashbackGanado: String? = null,
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

/** DTO de ConfiguracionUsuario para Firestore. */
data class ConfiguracionUsuarioDto(
    val uidUsuario: String = "",
    val idioma: String = "es-DO",
    val formatoFecha: String = "dd/MM/yyyy",
    val formatoMoneda: String = "RD$ 0.00",
    val monedaPredeterminada: String = "DOP",
    val temaOscuro: Boolean = false,
    val ultimoBackup: Timestamp? = null,
)

/** DTO de NotificacionConfig para Firestore. */
data class NotificacionConfigDto(
    val uidUsuario: String = "",
    val activa: Boolean = true,
    val pago7dias: Boolean = true,
    val pago3dias: Boolean = true,
    val pago1dia: Boolean = true,
    val pagoMismoDia: Boolean = true,
    val resumenMensual: Boolean = true,
    val alertasGastosAltos: Boolean = false,
    val umbralGastoAlto: Any? = null, // Supports String or Number for retrocompatibility
    val horaNotificacion: String = "08:00",
    val zonaHoraria: String = "America/Santo_Domingo",
)

/** DTO de ReglaCategoria para Firestore. */
data class ReglaCategoriaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val patron: String = "",
    val tipoMatch: String = "CONTIENE",
    val categoriaId: String = "",
    val prioridad: Int = 10,
    val confianza: Int = 1,
    val activa: Boolean = true,
    val creadoPor: String = "",
    val creadoEn: Timestamp? = null,
)

/** DTO de ReglaSugerida para Firestore. */
data class ReglaSugeridaDto(
    val id: String = "",
    val uidUsuario: String = "",
    val patronDetectado: String = "",
    val categoriaSugerida: String = "sin_categorizar",
    val muestras: List<String> = emptyList(),
    val confianzaCluster: Float = 0f,
    val creadaEn: Timestamp? = null,
    val aceptada: Boolean? = null,
    val resueltaEn: Timestamp? = null,
)

/** DTO de DispositivoPush para Firestore. */
data class DispositivoPushDto(
    val id: String = "",
    val uidUsuario: String = "",
    val tokenFcm: String = "",
    val activo: Boolean = true,
    val actualizadoEn: Timestamp? = null,
    val ultimoUsuarioUid: String? = null,
    val versionApp: String? = null,
    val modeloDispositivo: String? = null,
)
