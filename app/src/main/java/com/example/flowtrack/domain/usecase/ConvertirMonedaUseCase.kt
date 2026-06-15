package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.TasaCambio
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class ConvertirMonedaUseCase @Inject constructor() {
    fun ejecutar(monto: BigDecimal, direccionDopAUsd: Boolean, tasa: TasaCambio?): BigDecimal {
        val tasaCambio = tasa ?: return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        return if (direccionDopAUsd) {
            if (tasaCambio.venta.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            } else {
                monto.divide(tasaCambio.venta, 2, RoundingMode.HALF_UP)
            }
        } else {
            monto.multiply(tasaCambio.compra).setScale(2, RoundingMode.HALF_UP)
        }
    }
}
