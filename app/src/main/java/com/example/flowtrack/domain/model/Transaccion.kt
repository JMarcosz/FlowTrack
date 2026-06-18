package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Transaccion(
    val id: String,
    val uidUsuario: String,
    val cuentaId: String,
    val bancoCodigo: String,
    val fecha: Instant,
    val fechaPosteo: Instant?,
    val descripcionCorta: String,
    val descripcionOriginal: String,
    val descripcionNormalizada: String,
    val monto: BigDecimal,
    val tipo: TipoTransaccion,
    val moneda: Moneda,
    val balanceDespues: BigDecimal?,
    val referencia: String?,
    val serial: String?,
    val categoriaId: String?,
    val categoriaAutomatica: Boolean,
    val esDerivada: Boolean = false,
    val transaccionPadreId: String? = null,
    val derivadasIds: List<String> = emptyList(),
    val origen: OrigenTransaccion = OrigenTransaccion.IMPORTACION_ARCHIVO,
    val sourceEventId: String? = null,
    val sourceMessageId: String? = null,
    val sourceTransactionId: String? = null,
    val actualizadoEn: Instant = Instant.now(),
    val estado: EstadoTransaccion = EstadoTransaccion.APROBADA,
    val afectaBalance: Boolean = true,
    val posibleDuplicado: Boolean = false,
    val motivoRechazo: String? = null,
    val cargaId: String,
    val notaUsuario: String? = null,
    val metadataBanco: Map<String, String> = emptyMap(),
    val creadoEn: Instant,
)

val Transaccion.esContabilizable: Boolean
    get() = afectaBalance && estado == EstadoTransaccion.APROBADA && !esDerivada
