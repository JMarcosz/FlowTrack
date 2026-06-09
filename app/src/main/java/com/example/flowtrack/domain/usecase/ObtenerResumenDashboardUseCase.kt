package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// ─────────────────────────────────────────────────────────────────────────────
// Tipos de dominio
// ─────────────────────────────────────────────────────────────────────────────

data class RangoPeriodo(val inicio: Instant, val fin: Instant)

enum class UnidadBucket { DIA, SEMANA, MES }

data class PuntoSerie(
    val etiqueta: String,
    val instanteInicio: Instant,
    val gasto: BigDecimal,
    val ingreso: BigDecimal,
    val balanceAcumulado: BigDecimal,
)

data class DeltaMetrica(
    val actual: BigDecimal,
    val anterior: BigDecimal,
    val porcentaje: BigDecimal?,  // null si anterior == 0 (sin comparación válida)
    val esIncremento: Boolean,
)

data class DatosBancoResumen(
    val bancoCodigo: String,
    val gastos: BigDecimal,
    val ingresos: BigDecimal,
)

data class DatosCategoriaResumen(
    val categoriaId: String,
    val monto: BigDecimal,
)

data class ResumenDashboard(
    val gastoTotal: BigDecimal,
    val ingresoTotal: BigDecimal,
    val balanceNeto: BigDecimal,
    val comparacion: ResultadoComparacion,
    val deltaBalance: DeltaMetrica,
    val serie: List<PuntoSerie>,
    val unidad: UnidadBucket,
    val gastosPorBanco: List<DatosBancoResumen>,
    val gastosPorCategoria: List<DatosCategoriaResumen>,
)

// ─────────────────────────────────────────────────────────────────────────────
// Constantes de predicado para sparkline / breakdown (no comparación)
// ─────────────────────────────────────────────────────────────────────────────

private val TIPOS_GASTO_TARJETA_RES = setOf(
    TipoMovimientoTarjeta.COMPRA,
    TipoMovimientoTarjeta.AVANCE_EFECTIVO,
    TipoMovimientoTarjeta.INTERES,
    TipoMovimientoTarjeta.COMISION,
)

private val TIPOS_INGRESO_TARJETA_RES = setOf(
    TipoMovimientoTarjeta.PAGO,
    TipoMovimientoTarjeta.CASHBACK,
    TipoMovimientoTarjeta.DEVOLUCION,
)

// ─────────────────────────────────────────────────────────────────────────────
// UseCase
// ─────────────────────────────────────────────────────────────────────────────

class ObtenerResumenDashboardUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
    private val comparisonService: FinancialComparisonService,
) {

    suspend fun ejecutar(
        uid: String,
        periodo: String,
    ): AppResult<ResumenDashboard> = coroutineScope {
        val zona  = ZoneId.of("America/Santo_Domingo")
        val ahora = LocalDate.now(zona)

        val rangoActual   = comparisonService.getCurrentComparisonPeriod(periodo, ahora, zona)
        val rangoAnterior = comparisonService.getPreviousEquivalentPeriod(periodo, ahora, zona)
        val unidad        = unidadParaPeriodo(periodo)

        // ── Cargar ambos rangos sin límite ────────────────────────────────────
        val txActualDeferred = async {
            transaccionRepository.obtenerTransacciones(
                uid,
                rangoActual.inicio,
                rangoActual.fin,
                limite = 0,
            )
        }
        val txAnteriorDeferred = async {
            transaccionRepository.obtenerTransacciones(
                uid,
                rangoAnterior.inicio,
                rangoAnterior.fin,
                limite = 0,
            )
        }
        val movActualDeferred = async {
            movimientoTarjetaRepository.obtenerMovimientos(
                uid,
                rangoActual.inicio,
                rangoActual.fin,
            )
        }
        val movAnteriorDeferred = async {
            movimientoTarjetaRepository.obtenerMovimientos(
                uid,
                rangoAnterior.inicio,
                rangoAnterior.fin,
            )
        }

        val resTxActual = txActualDeferred.await()
        if (resTxActual is AppResult.Error) {
            return@coroutineScope AppResult.Error(resTxActual.error)
        }
        val txActuales = (resTxActual as AppResult.Success).data

        val resTxAnterior = txAnteriorDeferred.await()
        if (resTxAnterior is AppResult.Error) {
            return@coroutineScope AppResult.Error(resTxAnterior.error)
        }
        val txAnteriores = (resTxAnterior as AppResult.Success).data

        val resMovActual = movActualDeferred.await()
        if (resMovActual is AppResult.Error) {
            return@coroutineScope AppResult.Error(resMovActual.error)
        }
        val movActuales = (resMovActual as AppResult.Success).data

        val resMovAnterior = movAnteriorDeferred.await()
        if (resMovAnterior is AppResult.Error) {
            return@coroutineScope AppResult.Error(resMovAnterior.error)
        }
        val movAnteriores = (resMovAnterior as AppResult.Success).data

        // ── Comparación MTD via FinancialComparisonService ────────────────────
        val comparacion = comparisonService.calcular(
            periodoAnterior = rangoAnterior,
            txActuales      = txActuales,
            movActuales     = movActuales,
            txAnteriores    = txAnteriores,
            movAnteriores   = movAnteriores,
        )

        val gastoActual   = comparacion.gastoActual
        val ingresoActual = comparacion.ingresoActual
        val balActual     = ingresoActual - gastoActual

        // Balance anterior solo para la métrica de balance (independiente de cobertura)
        val balAnterior = comparacion.ingresoAnterior - comparacion.gastoAnterior
        val deltaBalance = delta(balActual, balAnterior)

        // ── Serie temporal ────────────────────────────────────────────────────
        val serie = construirSerie(rangoActual, zona, unidad, txActuales, movActuales)

        // ── Breakdown por banco ───────────────────────────────────────────────
        val codigosBanco = (txActuales.map { it.bancoCodigo } + movActuales.map { it.bancoCodigo }).distinct()
        val gastosPorBanco = codigosBanco.map { cod ->
            val g = sumaGastos(txActuales.filter { it.bancoCodigo == cod }, movActuales.filter { it.bancoCodigo == cod })
            val i = sumaIngresos(txActuales.filter { it.bancoCodigo == cod }, movActuales.filter { it.bancoCodigo == cod })
            DatosBancoResumen(cod, g, i)
        }.filter { it.gastos > BigDecimal.ZERO || it.ingresos > BigDecimal.ZERO }
            .sortedByDescending { it.gastos + it.ingresos }

        // ── Breakdown por categoría (top 5 gastos) ───────────────────────────
        val catCuenta = txActuales
            .filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
            .groupBy { it.categoriaId ?: "sin_categorizar" }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, tx -> a + tx.monto } }
        val catTarjeta = movActuales
            .filter { it.tipoMovimiento in TIPOS_GASTO_TARJETA_RES }
            .groupBy { it.categoriaId ?: "sin_categorizar" }
            .mapValues { (_, mvs) -> mvs.fold(BigDecimal.ZERO) { a, mv -> a + mv.monto } }
        val gastosPorCategoria = (catCuenta.keys + catTarjeta.keys).distinct()
            .map { id -> DatosCategoriaResumen(id, (catCuenta[id] ?: BigDecimal.ZERO) + (catTarjeta[id] ?: BigDecimal.ZERO)) }
            .sortedByDescending { it.monto }
            .take(5)

        AppResult.Success(
            ResumenDashboard(
                gastoTotal         = gastoActual,
                ingresoTotal       = ingresoActual,
                balanceNeto        = balActual,
                comparacion        = comparacion,
                deltaBalance       = deltaBalance,
                serie              = serie,
                unidad             = unidad,
                gastosPorBanco     = gastosPorBanco,
                gastosPorCategoria = gastosPorCategoria,
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ─────────────────────────────────────────────────────────────────────────

    private fun unidadParaPeriodo(periodo: String) = when (periodo) {
        "Últimos 3 meses" -> UnidadBucket.SEMANA
        "Este año"        -> UnidadBucket.MES
        else              -> UnidadBucket.DIA
    }

    private fun sumaGastos(
        txs: List<com.example.flowtrack.domain.model.Transaccion>,
        movs: List<com.example.flowtrack.domain.model.MovimientoTarjeta>,
    ): BigDecimal =
        txs.filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { a, tx -> a + tx.monto } +
        movs.filter { it.tipoMovimiento in TIPOS_GASTO_TARJETA_RES }
            .fold(BigDecimal.ZERO) { a, mv -> a + mv.monto }

    private fun sumaIngresos(
        txs: List<com.example.flowtrack.domain.model.Transaccion>,
        movs: List<com.example.flowtrack.domain.model.MovimientoTarjeta>,
    ): BigDecimal =
        txs.filter { it.tipo == TipoTransaccion.CREDITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { a, tx -> a + tx.monto } +
        movs.filter { it.tipoMovimiento in TIPOS_INGRESO_TARJETA_RES }
            .fold(BigDecimal.ZERO) { a, mv -> a + mv.monto }

    private fun delta(actual: BigDecimal, anterior: BigDecimal): DeltaMetrica {
        val pct = if (anterior.compareTo(BigDecimal.ZERO) == 0) null
        else (actual - anterior).divide(anterior.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP)
        return DeltaMetrica(
            actual       = actual,
            anterior     = anterior,
            porcentaje   = pct?.abs(),
            esIncremento = actual > anterior,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Serie temporal
    // ─────────────────────────────────────────────────────────────────────────

    private fun construirSerie(
        rango: RangoPeriodo,
        zona: ZoneId,
        unidad: UnidadBucket,
        txs: List<com.example.flowtrack.domain.model.Transaccion>,
        movs: List<com.example.flowtrack.domain.model.MovimientoTarjeta>,
    ): List<PuntoSerie> {
        val buckets = when (unidad) {
            UnidadBucket.DIA    -> diasEnRango(rango, zona)
            UnidadBucket.SEMANA -> semanasEnRango(rango, zona)
            UnidadBucket.MES    -> mesesEnRango(rango, zona)
        }

        var balanceAcumulado = BigDecimal.ZERO
        return buckets.map { (etiqueta, inicio, fin) ->
            val g = sumaGastos(
                txs.filter { !it.fecha.isBefore(inicio) && it.fecha.isBefore(fin) },
                movs.filter { !it.fechaTransaccion.isBefore(inicio) && it.fechaTransaccion.isBefore(fin) },
            )
            val i = sumaIngresos(
                txs.filter { !it.fecha.isBefore(inicio) && it.fecha.isBefore(fin) },
                movs.filter { !it.fechaTransaccion.isBefore(inicio) && it.fechaTransaccion.isBefore(fin) },
            )
            balanceAcumulado += i - g
            PuntoSerie(
                etiqueta         = etiqueta,
                instanteInicio   = inicio,
                gasto            = g,
                ingreso          = i,
                balanceAcumulado = balanceAcumulado,
            )
        }
    }

    private data class Bucket(val etiqueta: String, val inicio: Instant, val fin: Instant)

    private fun diasEnRango(rango: RangoPeriodo, zona: ZoneId): List<Bucket> {
        val inicioLocal = rango.inicio.atZone(zona).toLocalDate()
        val finLocal    = rango.fin.atZone(zona).toLocalDate()
        val resultado   = mutableListOf<Bucket>()
        var dia         = inicioLocal
        while (!dia.isAfter(finLocal)) {
            resultado.add(Bucket(
                etiqueta = dia.dayOfMonth.toString(),
                inicio   = dia.atStartOfDay(zona).toInstant(),
                fin      = dia.plusDays(1).atStartOfDay(zona).toInstant(),
            ))
            dia = dia.plusDays(1)
        }
        return resultado
    }

    private fun semanasEnRango(rango: RangoPeriodo, zona: ZoneId): List<Bucket> {
        val resultado = mutableListOf<Bucket>()
        var semanaNum = 1
        var inicio    = rango.inicio
        while (inicio.isBefore(rango.fin)) {
            val fin = minOf(inicio.plus(7, ChronoUnit.DAYS), rango.fin.plus(1, ChronoUnit.SECONDS))
            resultado.add(Bucket(
                etiqueta = "S$semanaNum",
                inicio   = inicio,
                fin      = fin,
            ))
            inicio = fin
            semanaNum++
        }
        return resultado
    }

    private fun mesesEnRango(rango: RangoPeriodo, zona: ZoneId): List<Bucket> {
        val inicioLocal = rango.inicio.atZone(zona).toLocalDate().withDayOfMonth(1)
        val finLocal    = rango.fin.atZone(zona).toLocalDate()
        val resultado   = mutableListOf<Bucket>()
        val mesesAbrev  = listOf("Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic")
        var mes         = YearMonth.from(inicioLocal)
        val mesFin      = YearMonth.from(finLocal)
        while (!mes.isAfter(mesFin)) {
            resultado.add(Bucket(
                etiqueta = mesesAbrev[mes.monthValue - 1],
                inicio   = mes.atDay(1).atStartOfDay(zona).toInstant(),
                fin      = mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant(),
            ))
            mes = mes.plusMonths(1)
        }
        return resultado
    }
}
