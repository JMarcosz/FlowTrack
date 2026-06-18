package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.model.esContabilizable
import java.math.BigDecimal

internal fun TipoMovimientoTarjeta.esGastoFinanciero(): Boolean =
    this in setOf(
        TipoMovimientoTarjeta.COMPRA,
        TipoMovimientoTarjeta.AVANCE_EFECTIVO,
        TipoMovimientoTarjeta.INTERES,
        TipoMovimientoTarjeta.COMISION
    )

internal fun TipoMovimientoTarjeta.esIngresoFinanciero(): Boolean =
    this in setOf(
        TipoMovimientoTarjeta.PAGO,
        TipoMovimientoTarjeta.CASHBACK,
        TipoMovimientoTarjeta.DEVOLUCION
    )

internal data class TotalesFinancieros(
    val gastos: BigDecimal,
    val ingresos: BigDecimal,
)

internal fun calcularTotalesFinancieros(
    transacciones: List<Transaccion>,
    movimientos: List<MovimientoTarjeta>,
): TotalesFinancieros {
    val gastosCuenta = transacciones
        .filter { it.tipo == TipoTransaccion.DEBITO && it.esContabilizable }
        .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.monto }

    val gastosTarjeta = movimientos
        .filter { it.tipoMovimiento.esGastoFinanciero() }
        .fold(BigDecimal.ZERO) { acc, mv -> acc + mv.monto }

    val ingresosCuenta = transacciones
        .filter { it.tipo == TipoTransaccion.CREDITO && it.esContabilizable }
        .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.monto }

    val ingresosTarjeta = movimientos
        .filter { it.tipoMovimiento.esIngresoFinanciero() }
        .fold(BigDecimal.ZERO) { acc, mv -> acc + mv.monto }

    return TotalesFinancieros(
        gastos = gastosCuenta + gastosTarjeta,
        ingresos = ingresosCuenta + ingresosTarjeta
    )
}
