package com.example.flowtrack.domain.usecase

import com.example.flowtrack.data.firestore.repositories.PresupuestoRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.PeriodoPresupuesto
import com.example.flowtrack.domain.model.Presupuesto
import com.example.flowtrack.domain.model.TipoTransaccion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class PresupuestoConGasto(
    val presupuesto: Presupuesto,
    val gastoActual: BigDecimal,
) {
    val porcentaje: Float get() =
        if (presupuesto.montoLimite > BigDecimal.ZERO)
            (gastoActual.toFloat() / presupuesto.montoLimite.toFloat()).coerceIn(0f, 1f)
        else 0f

    val excedido: Boolean get() = gastoActual > presupuesto.montoLimite
}

class ObtenerPresupuestosConGastoUseCase @Inject constructor(
    private val presupuestoRepository: PresupuestoRepository,
    private val transaccionRepository: TransaccionRepository,
) {
    private val zona = ZoneId.of("America/Santo_Domingo")

    fun observar(uid: String): Flow<List<PresupuestoConGasto>> {
        val ahora = LocalDate.now(zona)
        val inicioMes = ahora.withDayOfMonth(1).atStartOfDay(zona).toInstant()
        val finMes = YearMonth.from(ahora).atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()
        val inicioAnio = ahora.withDayOfYear(1).atStartOfDay(zona).toInstant()
        val finAnio = LocalDate.of(ahora.year, 12, 31).atTime(23, 59, 59).atZone(zona).toInstant()

        val txsMensualesFlow = transaccionRepository.observarTransaccionesRecientes(uid, inicioMes, finMes, limite = 500)
        val txsAnualesFlow = transaccionRepository.observarTransaccionesRecientes(uid, inicioAnio, finAnio, limite = 2000)

        return combine(
            presupuestoRepository.observarPresupuestos(uid),
            txsMensualesFlow,
            txsAnualesFlow,
        ) { presupuestos, txsMensuales, txsAnuales ->
            presupuestos.map { presupuesto ->
                val txs = if (presupuesto.periodo == PeriodoPresupuesto.MENSUAL) txsMensuales else txsAnuales
                val gasto = txs
                    .filter { it.categoriaId == presupuesto.categoriaId && it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
                    .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.monto }
                PresupuestoConGasto(presupuesto = presupuesto, gastoActual = gasto)
            }
        }
    }
}
