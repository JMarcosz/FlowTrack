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
    val cargaId: String,
    val notaUsuario: String? = null,
    val metadataBanco: Map<String, String> = emptyMap(),
    val creadoEn: Instant,
)
