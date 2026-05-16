package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion

/**
 * Motor de categorización automática de transacciones.
 *
 * Responsabilidad única: dado un listado de [ReglaCategoria] y una [Transaccion],
 * determinar qué categoría se debe asignar, si existe alguna regla que aplique.
 *
 * Orden de precedencia:
 *   1. Reglas del usuario (uidUsuario != null), ordenadas por prioridad DESC
 *   2. Reglas del sistema (uidUsuario == null), ordenadas por prioridad DESC
 *   3. Categoría por defecto inferida de [descripcionCorta] (tabla de la sección §3.1 del plan)
 *   4. null si ninguna regla y ningún default aplica
 *
 * No produce efectos secundarios: es una función pura que devuelve el [categoriaId] calculado.
 * La persistencia del resultado queda en manos del caller.
 */
object MotorCategorizacion {

    /**
     * Evalúa las reglas contra la descripción normalizada de la transacción.
     *
     * @param transaccion  Transacción a categorizar
     * @param reglas       Reglas cargadas desde Firestore (personales + globales)
     * @return Par (categoriaId, esAutomatica). Si no hay match: (null, false)
     */
    fun categorizar(
        transaccion: Transaccion,
        reglas: List<ReglaCategoria>,
    ): Pair<String?, Boolean> {
        val descripcion = transaccion.descripcionNormalizada

        // 1. Evaluar reglas del usuario, luego del sistema — ordenadas por prioridad DESC
        val reglaOrdenada = reglas
            .filter { it.activa }
            .sortedWith(
                compareByDescending<ReglaCategoria> { it.uidUsuario != null }  // usuario > sistema
                    .thenByDescending { it.prioridad }
            )

        for (regla in reglaOrdenada) {
            if (matchea(descripcion, regla)) {
                return Pair(regla.categoriaId, true)
            }
        }

        // 2. Inferencia por descripcionCorta (fallback hardcoded cuando no hay reglas)
        val categoriaInferida = inferirPorDescripcionCorta(
            descripcionCorta = transaccion.descripcionCorta,
            tipo = transaccion.tipo,
        )
        if (categoriaInferida != null) {
            return Pair(categoriaInferida, true)
        }

        return Pair(null, false)
    }

    /**
     * Versión por lote: categoriza una lista de transacciones con las mismas reglas.
     * Retorna la lista actualizada (con [Transaccion.categoriaId] asignado cuando aplique).
     */
    fun categorizarLote(
        transacciones: List<Transaccion>,
        reglas: List<ReglaCategoria>,
    ): List<Transaccion> = transacciones.map { tx ->
        val (catId, esAuto) = categorizar(tx, reglas)
        if (catId != null && tx.categoriaId == null) {
            tx.copy(categoriaId = catId, categoriaAutomatica = esAuto)
        } else {
            tx
        }
    }

    /**
     * Categoriza un [MovimientoTarjeta] usando las mismas reglas que para transacciones.
     * Retorna par (categoriaId, esAutomatica).
     */
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
        val categoriaInferida = inferirPorDescripcionCorta(
            descripcionCorta = descripcion.take(40),
            tipo = tipoTx,
        )
        return Pair(categoriaInferida, categoriaInferida != null)
    }

    /** Versión por lote para [MovimientoTarjeta]. */
    fun categorizarLoteMovimientos(
        movimientos: List<MovimientoTarjeta>,
        reglas: List<ReglaCategoria>,
    ): List<MovimientoTarjeta> = movimientos.map { mov ->
        val (catId, esAuto) = categorizarMovimiento(mov, reglas)
        if (catId != null && mov.categoriaId == null) {
            mov.copy(categoriaId = catId, categoriaAutomatica = esAuto)
        } else {
            mov
        }
    }

    // ─── Evaluación de una regla individual ──────────────────────────────────

    internal fun matchea(descripcionNormalizada: String, regla: ReglaCategoria): Boolean {
        val desc = descripcionNormalizada.normalizarDescripcion()
        return when (regla.tipoMatch) {
            TipoMatch.EXACTO       -> desc == regla.patron.normalizarDescripcion()
            TipoMatch.CONTIENE     -> desc.contains(regla.patron.normalizarDescripcion())
            TipoMatch.EMPIEZA_CON  -> desc.startsWith(regla.patron.normalizarDescripcion())
            // REGEX: el patrón NO se normaliza (normalizarDescripcion destruiría metacaracteres como \s, \b, \d)
            TipoMatch.REGEX        -> runCatching { Regex(regla.patron).containsMatchIn(desc) }.getOrDefault(false)
        }
    }

    // ─── Tabla de inferencia por descripción corta (§3.1 plan maestro) ───────

    /**
     * Mapea [descripcionCorta] a un [categoriaId] del [categoriaRegistry].
     * Solo se invoca si no hay ninguna regla que haga match.
     */
    internal fun inferirPorDescripcionCorta(
        descripcionCorta: String,
        tipo: TipoTransaccion,
    ): String? {
        val upper = descripcionCorta.normalizarDescripcion()
        return when {
            // Ingresos
            upper.contains("NOMINA") || upper.contains("SALARIO") -> "salario"
            tipo == TipoTransaccion.CREDITO && upper.contains("TRANSFERENCIA ENTRANTE") -> "transferencia_recibida"
            tipo == TipoTransaccion.CREDITO && upper.contains("TRANSFERENCIA PROPIA")  -> "transferencia_recibida"
            tipo == TipoTransaccion.CREDITO && upper.contains("DEPOSITO")              -> "deposito"
            tipo == TipoTransaccion.CREDITO && upper.contains("CASHBACK")              -> "cashback"

            // Gastos
            upper.contains("CONSUMO POS")           -> "compras"
            upper.contains("RETIRO ATM")            -> "atm"
            upper.contains("RETIRO SUCURSAL")       -> "atm"
            upper.contains("TRANSFERENCIA SALIENTE") -> "transferencia_enviada"
            upper.contains("TRANSFERENCIA LBTR")    -> "transferencia_enviada"
            upper.contains("TRANSFERENCIA ACH")     -> "transferencia_enviada"
            upper.contains("IMPUESTO DGII")         -> "impuestos"
            upper.contains("COMISION ATM")          -> "intereses_comisiones"
            upper.contains("COMISION")              -> "intereses_comisiones"
            upper.contains("PAGO SERVICIO")         -> "servicios"
            upper.contains("PAGO DEBITADO")         -> "servicios"
            upper.contains("CARGO PENDIENTE")       -> "servicios"
            upper.contains("PAGO CHEQUE")           -> "servicios"

            else -> null
        }
    }
}
