package com.example.flowtrack.domain.model

import java.time.Instant

/** Registro inmutable de una importación de estado de cuenta. */
data class Carga(
    val id: String,
    val uidUsuario: String,
    val nombreArchivo: String,
    val tamanioBytes: Long,
    val mimeType: String?,
    val bancoCodigo: String,
    val bancoDetectadoAutomaticamente: Boolean,
    val confianzaDeteccion: Float,
    val parserVersion: Int,
    val tipoDocumento: TipoDocumento,
    val cuentaId: String?,
    val tarjetaId: String?,
    val periodoInicio: Instant?,
    val periodoFin: Instant?,
    val transaccionesInsertadas: Int,
    val transaccionesDuplicadas: Int,
    val advertencias: List<String> = emptyList(),
    val estado: EstadoCarga,
    val procesadoEn: Instant,
)
