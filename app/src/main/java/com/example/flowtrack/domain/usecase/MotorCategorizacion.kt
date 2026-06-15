package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.domain.model.CategoriaCatalogo
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion

/**
 * Motor de categorizacion automatica de transacciones.
 *
 * Orden de precedencia:
 *   1. Reglas del usuario (uidUsuario != null), ordenadas por prioridad DESC
 *   2. Reglas del sistema (uidUsuario == null), ordenadas por prioridad DESC
 *   3. Keywords especificos de comercio contra descripcionNormalizada
 *   4. Tipo generico de transaccion contra descripcionCorta
 *   5. null si ninguna regla aplica
 */
object MotorCategorizacion {

    fun categorizar(
        transaccion: Transaccion,
        reglas: List<ReglaCategoria>,
    ): Pair<String?, Boolean> {
        val descripcion = transaccion.descripcionNormalizada

        val reglaOrdenada = reglas
            .filter { it.activa }
            .sortedWith(
                compareByDescending<ReglaCategoria> { it.uidUsuario != null }
                    .thenByDescending { it.prioridad }
            )

        for (regla in reglaOrdenada) {
            if (matchea(descripcion, regla)) return Pair(regla.categoriaId, true)
        }

        val categoriaInferida = inferirPorDescripcion(
            descripcionNormalizada = transaccion.descripcionNormalizada,
            descripcionCorta = transaccion.descripcionCorta,
            tipo = transaccion.tipo,
        )
        return Pair(categoriaInferida, categoriaInferida != null)
    }

    fun categorizarLote(
        transacciones: List<Transaccion>,
        reglas: List<ReglaCategoria>,
    ): List<Transaccion> = transacciones.map { tx ->
        val (catId, esAuto) = categorizar(tx, reglas)
        if (catId != null && tx.categoriaId == null) tx.copy(categoriaId = catId, categoriaAutomatica = esAuto)
        else tx
    }

    fun categorizarMovimiento(
        movimiento: MovimientoTarjeta,
        reglas: List<ReglaCategoria>,
    ): Pair<String?, Boolean> {
        val descripcion = movimiento.descripcionNormalizada
        val reglaOrdenada = reglas
            .filter { it.activa }
            .sortedWith(
                compareByDescending<ReglaCategoria> { it.uidUsuario != null }
                    .thenByDescending { it.prioridad }
            )
        for (regla in reglaOrdenada) {
            if (matchea(descripcion, regla)) return Pair(regla.categoriaId, true)
        }
        val tipoTx = when (movimiento.tipoMovimiento) {
            TipoMovimientoTarjeta.PAGO, TipoMovimientoTarjeta.CASHBACK -> TipoTransaccion.CREDITO
            else -> TipoTransaccion.DEBITO
        }
        val categoriaInferida = inferirPorDescripcion(
            descripcionNormalizada = movimiento.descripcionNormalizada,
            descripcionCorta = movimiento.descripcionNormalizada.take(40),
            tipo = tipoTx,
        )
        return Pair(categoriaInferida, categoriaInferida != null)
    }

    fun categorizarLoteMovimientos(
        movimientos: List<MovimientoTarjeta>,
        reglas: List<ReglaCategoria>,
    ): List<MovimientoTarjeta> = movimientos.map { mov ->
        val (catId, esAuto) = categorizarMovimiento(mov, reglas)
        if (catId != null && mov.categoriaId == null) mov.copy(categoriaId = catId, categoriaAutomatica = esAuto)
        else mov
    }

    internal fun matchea(descripcionNormalizada: String, regla: ReglaCategoria): Boolean {
        val desc = descripcionNormalizada.normalizarDescripcion()
        return when (regla.tipoMatch) {
            TipoMatch.EXACTO      -> desc == regla.patron.normalizarDescripcion()
            TipoMatch.CONTIENE    -> desc.contains(regla.patron.normalizarDescripcion())
            TipoMatch.EMPIEZA_CON -> desc.startsWith(regla.patron.normalizarDescripcion())
            TipoMatch.REGEX       -> runCatching { Regex(regla.patron).containsMatchIn(desc) }.getOrDefault(false)
        }
    }

    internal fun inferirPorDescripcion(
        descripcionNormalizada: String,
        descripcionCorta: String,
        tipo: TipoTransaccion,
    ): String? {
        val desc = descripcionNormalizada.normalizarDescripcion()
        val corta = descripcionCorta.normalizarDescripcion()

        if (desc.contains("UBER EATS")) return CategoriaCatalogo.ALIMENTACION
        if (desc.contains("HELADOS BON") ||
            desc.contains("PEDIDOSYA")  ||
            desc.contains("RICO HOTDOG") ||
            desc.contains("KFC")         ||
            desc.contains("TACO BELL")   ||
            desc.contains("CHICKEN ROOMING")) return CategoriaCatalogo.ALIMENTACION

        if (desc.contains("UBER RIDES") ||
            desc.contains("UBER")       ||
            desc.contains("DIDI")       ||
            desc.contains("OPRET")      ||
            desc.contains("INDRIVE"))    return CategoriaCatalogo.TRANSPORTE

        if (desc.contains("BRAVO")      ||
            desc.contains("JUMBO")      ||
            desc.contains("SIRENA")     ||
            desc.contains("CARREFOUR")  ||
            desc.contains("MINIMARKET")) return CategoriaCatalogo.COMPRAS

        if (desc.contains("FARMA EXTRA") ||
            desc.contains("MEDICAR GBC") ||
            desc.contains("FCIA"))        return CategoriaCatalogo.SALUD

        if (desc.contains("ALTICE") ||
            desc.contains("CLARO"))       return CategoriaCatalogo.SERVICIOS

        if (desc.contains("AMAZON PRIME") ||
            desc.contains("LARAVEL CLOUD")) return CategoriaCatalogo.SUSCRIPCIONES

        if (desc.contains("CHUCK E CHEESE") ||
            desc.contains("MEGAPLEX")       ||
            desc.contains("BATH"))           return CategoriaCatalogo.ENTRETENIMIENTO

        if (desc.contains("PAGO IMPUESTO") ||
            desc.contains("DGII"))           return CategoriaCatalogo.IMPUESTOS
        if (desc.contains("COMISIONES") ||
            desc.contains("CARGO MENSUAL") ||
            desc.contains("CARGO RETIRO"))   return CategoriaCatalogo.INTERESES_COMISIONES

        if (desc.contains("MB A ") ||
            desc.contains("COD CASH") ||
            desc.contains("PAGO ACH") ||
            desc.contains("AUT PAGO"))       return CategoriaCatalogo.TRANSFERENCIA_ENVIADA
        if (desc.contains("RET DE CHK"))     return CategoriaCatalogo.ATM

        if (desc.contains("MB DESDE"))       return CategoriaCatalogo.TRANSFERENCIA_RECIBIDA
        if (desc.contains("DEPOSITO") && tipo == TipoTransaccion.CREDITO) return CategoriaCatalogo.DEPOSITO

        if (desc.contains("LBTR")) return if (tipo == TipoTransaccion.CREDITO) {
            CategoriaCatalogo.TRANSFERENCIA_RECIBIDA
        } else {
            CategoriaCatalogo.TRANSFERENCIA_ENVIADA
        }

        return when {
            corta.contains("NOMINA") || corta.contains("SALARIO")               -> CategoriaCatalogo.SALARIO
            tipo == TipoTransaccion.CREDITO && (
                corta.contains("TRANSFERENCIA RECIBIDA") ||
                corta.contains("TRANSFERENCIA ENTRANTE") ||
                corta.contains("TRANSFERENCIA PROPIA"))                          -> CategoriaCatalogo.TRANSFERENCIA_RECIBIDA
            tipo == TipoTransaccion.CREDITO && corta.contains("CASHBACK")        -> CategoriaCatalogo.CASHBACK
            tipo == TipoTransaccion.CREDITO && corta.contains("DEPOSITO")        -> CategoriaCatalogo.DEPOSITO
            corta.contains("CONSUMO POS")                                        -> CategoriaCatalogo.COMPRAS
            corta.contains("RETIRO ATM") || corta.contains("RETIRO SUCURSAL")   -> CategoriaCatalogo.ATM
            corta.contains("RETIRO")                                             -> CategoriaCatalogo.ATM
            corta.contains("TRANSFERENCIA ENVIADA") ||
                corta.contains("TRANSFERENCIA SALIENTE") ||
                corta.contains("TRANSFERENCIA LBTR")    ||
                corta.contains("TRANSFERENCIA ACH")                              -> CategoriaCatalogo.TRANSFERENCIA_ENVIADA
            corta.contains("IMPUESTO DGII") || corta.contains("IMPUESTO")       -> CategoriaCatalogo.IMPUESTOS
            corta.contains("COMISION ATM") ||
                corta.contains("COMISION")  ||
                corta.contains("CARGO MENSUAL") ||
                corta.contains("CARGO")                                          -> CategoriaCatalogo.INTERESES_COMISIONES
            corta.contains("PAGO SERVICIO") || corta.contains("PAGO DEBITADO") ||
                corta.contains("CARGO PENDIENTE") || corta.contains("PAGO CHEQUE") -> CategoriaCatalogo.SERVICIOS
            else -> null
        }
    }

    internal fun inferirPorDescripcionCorta(
        descripcionCorta: String,
        tipo: TipoTransaccion,
    ): String? = inferirPorDescripcion(
        descripcionNormalizada = descripcionCorta,
        descripcionCorta = descripcionCorta,
        tipo = tipo,
    )
}
