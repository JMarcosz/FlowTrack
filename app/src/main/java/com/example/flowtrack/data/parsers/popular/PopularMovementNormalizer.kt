package com.example.flowtrack.data.parsers.popular

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.data.parsers.core.TipoMovimiento

internal data class MovimientoPopularClasificado(
    val tipo: TipoMovimiento,
    val descripcionCorta: String,
    val descripcionNormalizada: String,
)

internal object PopularMovementNormalizer {

    fun clasificar(
        descripcionCorta: String,
        descripcionLarga: String,
    ): MovimientoPopularClasificado {
        val corta = descripcionCorta.uppercase().normalizarDescripcion()
        val larga = descripcionLarga.uppercase().normalizarDescripcion()

        val tipo = when {
            corta.contains("CREDITO") -> TipoMovimiento.INGRESO
            corta.contains("ATM") || larga.contains("RET DE CHK") -> TipoMovimiento.RETIRO_ATM
            corta.contains("DEBITO") -> clasificarDebito(larga)
            corta.isBlank() -> clasificarSinTipo(larga)
            else -> TipoMovimiento.GASTO
        }

        return MovimientoPopularClasificado(
            tipo = tipo,
            descripcionCorta = normalizarConcepto(corta, larga),
            descripcionNormalizada = larga.ifBlank { corta },
        )
    }

    private fun clasificarDebito(descripcion: String): TipoMovimiento = when {
        descripcion.contains("COMISION") || descripcion.contains("CARGO MENSUAL") ->
            TipoMovimiento.COMISION
        descripcion.contains("LBTR") || descripcion.contains("MB A ") ||
            descripcion.contains("PAGO ACH") || descripcion.contains("ACH ") ->
            TipoMovimiento.TRANSFERENCIA
        else -> TipoMovimiento.GASTO
    }

    private fun clasificarSinTipo(descripcion: String): TipoMovimiento = when {
        descripcion.contains("PAGO IMPUESTO") || descripcion.contains("DGII") ->
            TipoMovimiento.COMISION
        descripcion.contains("CARGO") || descripcion.contains("COMISION") ->
            TipoMovimiento.COMISION
        else -> TipoMovimiento.GASTO
    }

    private fun normalizarConcepto(corta: String, larga: String): String = when {
        corta.contains("CREDITO") -> when {
            larga.contains("MB DESDE") -> "TRANSFERENCIA RECIBIDA"
            larga.contains("DEPOSITO CHEQUE") || larga.contains("DEPOSITO EN EFECTIVO") ->
                "DEPOSITO"
            larga.contains("DEPOSITO EN SUBAGENTE") || larga.contains("DEPOSITO SUBAGENTE") ->
                "DEPOSITO SUBAGENTE"
            larga.contains("LBTR") -> "TRANSFERENCIA LBTR RECIBIDA"
            larga.contains("COD CASH") -> "DEPOSITO CODIGO"
            else -> "CREDITO"
        }
        corta.contains("ATM") || larga.contains("RET DE CHK") -> "RETIRO ATM"
        larga.contains("LBTR") -> "TRANSFERENCIA LBTR"
        larga.contains("MB A ") -> "TRANSFERENCIA ENVIADA"
        larga.contains("PAGO ACH") || larga.contains("ACH ") -> "TRANSFERENCIA ACH"
        larga.contains("PAGO IMPUESTO") || larga.contains("DGII") -> "IMPUESTO DGII"
        larga.contains("CARGO MENSUAL") -> "CARGO MENSUAL TD"
        larga.contains("PAG ") || larga.contains("PAGO ") -> {
            val servicio = Regex("""PAG\s+(\w+)""").find(larga)?.groupValues?.getOrNull(1)
                ?: Regex("""PAGO\s+(\w+)""").find(larga)?.groupValues?.getOrNull(1)
                ?: "SERVICIO"
            "PAGO $servicio".take(40)
        }
        else -> larga.take(40).ifBlank { corta.take(40) }
    }
}
