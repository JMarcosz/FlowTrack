package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.model.Transaccion
import java.math.BigDecimal
import java.math.RoundingMode

internal fun BigDecimal.aDop(moneda: Moneda, tasa: TasaCambio?): BigDecimal {
    if (moneda != Moneda.USD) return this
    val tasaCompra = tasa?.compra ?: return this
    return multiply(tasaCompra).setScale(2, RoundingMode.HALF_UP)
}

internal fun Transaccion.normalizarMoneda(tasa: TasaCambio?): Transaccion = copy(
    monto = monto.aDop(moneda, tasa),
    balanceDespues = balanceDespues?.aDop(moneda, tasa),
)

internal fun MovimientoTarjeta.normalizarMoneda(tasa: TasaCambio?): MovimientoTarjeta = copy(
    monto = monto.aDop(moneda, tasa),
)
