package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

// Cuántos días de desfase se toleran al inicio del período anterior antes de marcar cobertura insuficiente.
private const val COVERAGE_THRESHOLD_DAYS = 3L

data class ResultadoComparacion(
    val comparisonAvailable: Boolean,
    val coverageWarning: Boolean,
    val reason: String?,
    val gastoActual: BigDecimal,
    val ingresoActual: BigDecimal,
    val gastoAnterior: BigDecimal,
    val ingresoAnterior: BigDecimal,
    // null cuando no es computable (anterior==0 y actual>0, o cobertura insuficiente)
    val expenseChangePercentage: BigDecimal?,
    val incomeChangePercentage: BigDecimal?,
    val expenseIsIncrement: Boolean,
    val incomeIsIncrement: Boolean,
)

@Singleton
class FinancialComparisonService @Inject constructor() {

    private val tiposGasto = setOf(
        TipoMovimientoTarjeta.COMPRA,
        TipoMovimientoTarjeta.AVANCE_EFECTIVO,
        TipoMovimientoTarjeta.INTERES,
        TipoMovimientoTarjeta.COMISION,
    )

    private val tiposIngreso = setOf(
        TipoMovimientoTarjeta.PAGO,
        TipoMovimientoTarjeta.CASHBACK,
        TipoMovimientoTarjeta.DEVOLUCION,
    )

    /** Período actual según el selector (p.ej. "Este mes" → 01/05 – hoy). */
    fun getCurrentComparisonPeriod(periodo: String, ahora: LocalDate, zona: ZoneId): RangoPeriodo =
        when (periodo) {
            "Mes pasado" -> {
                val ym = YearMonth.from(ahora).minusMonths(1)
                RangoPeriodo(
                    ym.atDay(1).atStartOfDay(zona).toInstant(),
                    ym.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant(),
                )
            }
            "Últimos 3 meses" -> RangoPeriodo(
                ahora.minusDays(89).atStartOfDay(zona).toInstant(),
                ahora.atTime(23, 59, 59).atZone(zona).toInstant(),
            )
            "Este año" -> RangoPeriodo(
                ahora.withDayOfYear(1).atStartOfDay(zona).toInstant(),
                ahora.atTime(23, 59, 59).atZone(zona).toInstant(),
            )
            else -> { // "Este mes"
                val ym = YearMonth.from(ahora)
                RangoPeriodo(
                    ym.atDay(1).atStartOfDay(zona).toInstant(),
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant(),
                )
            }
        }

    /**
     * Período anterior equivalente (MTD, no ventana deslizante).
     * "Este mes" día N → mes anterior del día 1 al día N.
     */
    fun getPreviousEquivalentPeriod(periodo: String, ahora: LocalDate, zona: ZoneId): RangoPeriodo =
        when (periodo) {
            "Mes pasado" -> {
                val ym = YearMonth.from(ahora).minusMonths(2)
                RangoPeriodo(
                    ym.atDay(1).atStartOfDay(zona).toInstant(),
                    ym.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant(),
                )
            }
            "Últimos 3 meses" -> RangoPeriodo(
                ahora.minusDays(179).atStartOfDay(zona).toInstant(),
                ahora.minusDays(90).atTime(23, 59, 59).atZone(zona).toInstant(),
            )
            "Este año" -> RangoPeriodo(
                ahora.minusYears(1).withDayOfYear(1).atStartOfDay(zona).toInstant(),
                ahora.minusYears(1).atTime(23, 59, 59).atZone(zona).toInstant(),
            )
            else -> { // "Este mes"
                val ymAnterior = YearMonth.from(ahora).minusMonths(1)
                val diaCorte = minOf(ahora.dayOfMonth, ymAnterior.lengthOfMonth())
                RangoPeriodo(
                    ymAnterior.atDay(1).atStartOfDay(zona).toInstant(),
                    ymAnterior.atDay(diaCorte).atTime(23, 59, 59).atZone(zona).toInstant(),
                )
            }
        }

    /**
     * Devuelve true si los datos cubren correctamente el inicio del período anterior.
     * Si la transacción más antigua está más de COVERAGE_THRESHOLD_DAYS después del inicio,
     * la cobertura es insuficiente.
     */
    fun validatePeriodCoverage(
        periodoAnterior: RangoPeriodo,
        txAnteriores: List<Transaccion>,
        movAnteriores: List<MovimientoTarjeta>,
    ): Boolean {
        val todasFechas = txAnteriores.map { it.fecha } + movAnteriores.map { it.fechaTransaccion }
        if (todasFechas.isEmpty()) return false
        val earliest = todasFechas.min()
        val threshold = periodoAnterior.inicio.plusSeconds(COVERAGE_THRESHOLD_DAYS * 86_400L)
        return earliest.isBefore(threshold)
    }

    /**
     * Agrega gastos e ingresos del conjunto de transacciones y movimientos normalizados.
     * Devuelve Pair(gastos, ingresos).
     */
    fun calculateIncomeExpenseTotals(
        txs: List<Transaccion>,
        movs: List<MovimientoTarjeta>,
    ): Pair<BigDecimal, BigDecimal> {
        val gastos = txs.filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { a, tx -> a + tx.monto } +
            movs.filter { it.tipoMovimiento in tiposGasto }
            .fold(BigDecimal.ZERO) { a, mv -> a + mv.monto }
        val ingresos = txs.filter { it.tipo == TipoTransaccion.CREDITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { a, tx -> a + tx.monto } +
            movs.filter { it.tipoMovimiento in tiposIngreso }
            .fold(BigDecimal.ZERO) { a, mv -> a + mv.monto }
        return Pair(gastos, ingresos)
    }

    /**
     * ((actual - anterior) / |anterior|) × 100.
     * null cuando anterior>0 y no es computable (actual>0, anterior==0).
     * BigDecimal.ZERO cuando ambos son cero (sin actividad en ambos períodos).
     */
    fun calculatePercentageChange(actual: BigDecimal, anterior: BigDecimal): BigDecimal? {
        if (anterior.compareTo(BigDecimal.ZERO) == 0) {
            return if (actual.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else null
        }
        return (actual - anterior)
            .divide(anterior.abs(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(1, RoundingMode.HALF_UP)
    }

    /**
     * Punto de entrada principal. Calcula un ResultadoComparacion completo a partir de
     * los datos ya cargados de ambos períodos.
     */
    fun calcular(
        periodoAnterior: RangoPeriodo,
        txActuales: List<Transaccion>,
        movActuales: List<MovimientoTarjeta>,
        txAnteriores: List<Transaccion>,
        movAnteriores: List<MovimientoTarjeta>,
    ): ResultadoComparacion {
        val (gastoActual, ingresoActual) = calculateIncomeExpenseTotals(txActuales, movActuales)
        val (gastoAnterior, ingresoAnterior) = calculateIncomeExpenseTotals(txAnteriores, movAnteriores)

        val hayDatosAnteriores = txAnteriores.isNotEmpty() || movAnteriores.isNotEmpty()

        if (!hayDatosAnteriores) {
            return ResultadoComparacion(
                comparisonAvailable   = false,
                coverageWarning       = false,
                reason                = "Sin datos del período anterior",
                gastoActual           = gastoActual,
                ingresoActual         = ingresoActual,
                gastoAnterior         = BigDecimal.ZERO,
                ingresoAnterior       = BigDecimal.ZERO,
                expenseChangePercentage = null,
                incomeChangePercentage  = null,
                expenseIsIncrement    = false,
                incomeIsIncrement     = false,
            )
        }

        val coberturaOk = validatePeriodCoverage(periodoAnterior, txAnteriores, movAnteriores)
        if (!coberturaOk) {
            return ResultadoComparacion(
                comparisonAvailable   = false,
                coverageWarning       = true,
                reason                = "Faltan datos del periodo anterior para comparar correctamente",
                gastoActual           = gastoActual,
                ingresoActual         = ingresoActual,
                gastoAnterior         = gastoAnterior,
                ingresoAnterior       = ingresoAnterior,
                expenseChangePercentage = null,
                incomeChangePercentage  = null,
                expenseIsIncrement    = false,
                incomeIsIncrement     = false,
            )
        }

        val expensePct = calculatePercentageChange(gastoActual, gastoAnterior)
        val incomePct  = calculatePercentageChange(ingresoActual, ingresoAnterior)

        return ResultadoComparacion(
            comparisonAvailable   = true,
            coverageWarning       = false,
            reason                = null,
            gastoActual           = gastoActual,
            ingresoActual         = ingresoActual,
            gastoAnterior         = gastoAnterior,
            ingresoAnterior       = ingresoAnterior,
            expenseChangePercentage = expensePct?.abs(),
            incomeChangePercentage  = incomePct?.abs(),
            expenseIsIncrement    = gastoActual > gastoAnterior,
            incomeIsIncrement     = ingresoActual > ingresoAnterior,
        )
    }
}
