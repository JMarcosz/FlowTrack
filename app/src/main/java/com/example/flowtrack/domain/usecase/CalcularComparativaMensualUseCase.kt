package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private val TIPOS_GASTO_TARJETA_COMP = setOf(
    TipoMovimientoTarjeta.COMPRA,
    TipoMovimientoTarjeta.AVANCE_EFECTIVO,
    TipoMovimientoTarjeta.INTERES,
    TipoMovimientoTarjeta.COMISION,
)

data class ComparativaMensual(
    val gastoActual: BigDecimal,
    val gastoAnterior: BigDecimal,
    val porcentaje: BigDecimal?, // null si gastoAnterior es 0
    val esIncremento: Boolean,
)

class CalcularComparativaMensualUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
) {
    suspend fun ejecutar(uid: String): AppResult<ComparativaMensual> {
        val zona = ZoneId.of("America/Santo_Domingo")
        val ahora = LocalDate.now(zona)

        val inicioMesActual = ahora.withDayOfMonth(1).atStartOfDay(zona).toInstant()
        val finMesActual = ahora.atTime(23, 59, 59).atZone(zona).toInstant()

        val inicioMesAnterior = ahora.minusMonths(1).withDayOfMonth(1).atStartOfDay(zona).toInstant()
        val finMesAnterior = ahora.withDayOfMonth(1).minusDays(1).atTime(23, 59, 59).atZone(zona).toInstant()

        val resActual = transaccionRepository.obtenerTransacciones(uid, inicioMesActual, finMesActual, limite = 0)
        if (resActual is AppResult.Error) return AppResult.Error(resActual.error)

        val resAnterior = transaccionRepository.obtenerTransacciones(uid, inicioMesAnterior, finMesAnterior, limite = 0)
        if (resAnterior is AppResult.Error) return AppResult.Error(resAnterior.error)

        val resMovActual = movimientoTarjetaRepository.obtenerMovimientos(uid, inicioMesActual, finMesActual)
        if (resMovActual is AppResult.Error) return AppResult.Error(resMovActual.error)

        val resMovAnterior = movimientoTarjetaRepository.obtenerMovimientos(uid, inicioMesAnterior, finMesAnterior)
        if (resMovAnterior is AppResult.Error) return AppResult.Error(resMovAnterior.error)

        val gastosCuentaActual = (resActual as AppResult.Success).data
            .filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.monto }

        val gastosTarjetaActual = (resMovActual as AppResult.Success).data
            .filter { it.tipoMovimiento in TIPOS_GASTO_TARJETA_COMP }
            .fold(BigDecimal.ZERO) { acc, mov -> acc + mov.monto }

        val gastosActuales = gastosCuentaActual + gastosTarjetaActual

        val gastosCuentaAnterior = (resAnterior as AppResult.Success).data
            .filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.monto }

        val gastosTarjetaAnterior = (resMovAnterior as AppResult.Success).data
            .filter { it.tipoMovimiento in TIPOS_GASTO_TARJETA_COMP }
            .fold(BigDecimal.ZERO) { acc, mov -> acc + mov.monto }

        val gastosAnteriores = gastosCuentaAnterior + gastosTarjetaAnterior

        val (porcentaje, esIncremento) = if (gastosAnteriores.compareTo(BigDecimal.ZERO) == 0) {
            null to false
        } else {
            val delta = (gastosActuales - gastosAnteriores).divide(gastosAnteriores, 4, RoundingMode.HALF_UP) * BigDecimal("100")
            delta.abs() to (delta > BigDecimal.ZERO)
        }

        return AppResult.Success(
            ComparativaMensual(
                gastoActual = gastosActuales,
                gastoAnterior = gastosAnteriores,
                porcentaje = porcentaje,
                esIncremento = esIncremento
            )
        )
    }
}
