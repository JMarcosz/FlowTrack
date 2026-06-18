package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.CategoriaCatalogo
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
// UseCase
// ─────────────────────────────────────────────────────────────────────────────

class ObtenerResumenDashboardUseCase @Inject constructor(
    private val flujoUnificadoUseCase: ObtenerFlujoUnificadoUseCase,
    private val transaccionRepository: TransaccionRepository,
    private val cuentaRepository: CuentaRepository,
    private val tasaCambioRepository: TasaCambioRepository,
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
        val tasaDeferred  = async { tasaCambioRepository.obtenerTasaDelDia() }

        // ── Cargar datos en paralelo ──────────────────────────────────────────
        val actualDeferred = async { flujoUnificadoUseCase.ejecutar(uid, rangoActual.inicio, rangoActual.fin) }
        val anteriorDeferred = async { flujoUnificadoUseCase.ejecutar(uid, rangoAnterior.inicio, rangoAnterior.fin) }
        val cuentasDeferred = async { cuentaRepository.obtenerCuentas(uid) }

        val resActual = actualDeferred.await()
        if (resActual is AppResult.Error) return@coroutineScope AppResult.Error(resActual.error)
        val flujoActual = (resActual as AppResult.Success).data
        val tasaCambio = (tasaDeferred.await() as? AppResult.Success)?.data
        val txActuales = flujoActual.transacciones.map { it.normalizarMoneda(tasaCambio) }
        val movActuales = flujoActual.movimientos.map { it.normalizarMoneda(tasaCambio) }

        val resAnterior = anteriorDeferred.await()
        if (resAnterior is AppResult.Error) return@coroutineScope AppResult.Error(resAnterior.error)
        val flujoAnterior = (resAnterior as AppResult.Success).data
        val txAnteriores = flujoAnterior.transacciones.map { it.normalizarMoneda(tasaCambio) }
        val movAnteriores = flujoAnterior.movimientos.map { it.normalizarMoneda(tasaCambio) }

        val resCuentas = cuentasDeferred.await()
        if (resCuentas is AppResult.Error) return@coroutineScope AppResult.Error(resCuentas.error)
        val cuentasVisibles = (resCuentas as AppResult.Success).data.filter { it.activa && it.mostrarEnDashboard }

        // ── Comparación MTD ───────────────────────────────────────────────────
        val comparacion = comparisonService.calcular(
            periodoAnterior = rangoAnterior,
            txActuales      = txActuales,
            movActuales     = movActuales,
            txAnteriores    = txAnteriores,
            movAnteriores   = movAnteriores,
            requiereCoberturaCompleta = periodo == "Este mes",
        )

        // ── Balance Neto Real (Suma de balances finales de cuentas) ──────────
        // Para cada cuenta activa:
        // 1. Tomamos el balance de la ultima transaccion en el rango.
        // 2. Si no hay, buscamos la ultima transaccion antes del rango.
        var totalBalanceFinal = BigDecimal.ZERO
        cuentasVisibles.forEach { cuenta ->
            val ultimaTxRango = txActuales
                .filter { it.cuentaId == cuenta.id && it.balanceDespues != null }
                .maxByOrNull { it.fecha }
            if (ultimaTxRango != null) {
                totalBalanceFinal += ultimaTxRango.balanceDespues!!
            } else {
                // Consultar la ultima transaccion historica previa por cuenta, no global.
                val resPrevia = transaccionRepository.obtenerTransacciones(
                    uid = uid,
                    inicio = null,
                    fin = rangoActual.inicio,
                    limite = 1,
                    cuentaId = cuenta.id,
                )
                val balanceFallback = cuenta.balanceActual ?: BigDecimal.ZERO
                if (resPrevia is AppResult.Success) {
                    val txPrevia = resPrevia.data.firstOrNull { it.balanceDespues != null }?.normalizarMoneda(tasaCambio)
                    totalBalanceFinal += (txPrevia?.balanceDespues ?: balanceFallback)
                } else {
                    totalBalanceFinal += balanceFallback
                }
            }
        }

        val balActual     = totalBalanceFinal
        val balAnterior   = balActual - (comparacion.ingresoActual - comparacion.gastoActual)
        val deltaBalance  = delta(balActual, balAnterior)

        // ── Serie temporal ────────────────────────────────────────────────────
        val balanceInicialPeriodo = balActual - (comparacion.ingresoActual - comparacion.gastoActual)
        val serie = construirSerie(rangoActual, zona, unidad, txActuales, movActuales, balanceInicialPeriodo)

        // ── Breakdown por banco ───────────────────────────────────────────────
        val codigosBanco = (txActuales.map { it.bancoCodigo } + movActuales.map { it.bancoCodigo }).distinct()
        val gastosPorBanco = codigosBanco.map { cod ->
            val txBanco = txActuales.filter { it.bancoCodigo == cod }
            val movBanco = movActuales.filter { it.bancoCodigo == cod }
            val totales = calcularTotalesFinancieros(txBanco, movBanco)
            DatosBancoResumen(cod, totales.gastos, totales.ingresos)
        }.filter { it.gastos > BigDecimal.ZERO || it.ingresos > BigDecimal.ZERO }
            .sortedByDescending { it.gastos + it.ingresos }

        // ── Breakdown por categoría (top 5 gastos) ───────────────────────────
        val catCuenta = txActuales
            .filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
            .groupBy { CategoriaCatalogo.normalizarId(it.categoriaId) ?: CategoriaCatalogo.SIN_CATEGORIZAR }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, tx -> a + tx.monto } }
        val catTarjeta = movActuales
            .filter { it.tipoMovimiento.esGastoFinanciero() }
            .groupBy { CategoriaCatalogo.normalizarId(it.categoriaId) ?: CategoriaCatalogo.SIN_CATEGORIZAR }
            .mapValues { (_, mvs) -> mvs.fold(BigDecimal.ZERO) { a, mv -> a + mv.monto } }
        val gastosPorCategoria = (catCuenta.keys + catTarjeta.keys).distinct()
            .map { id -> DatosCategoriaResumen(id, (catCuenta[id] ?: BigDecimal.ZERO) + (catTarjeta[id] ?: BigDecimal.ZERO)) }
            .sortedByDescending { it.monto }
            .take(5)

        AppResult.Success(
            ResumenDashboard(
                gastoTotal         = comparacion.gastoActual,
                ingresoTotal       = comparacion.ingresoActual,
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

    private fun unidadParaPeriodo(periodo: String) = when (periodo) {
        "Últimos 3 meses" -> UnidadBucket.SEMANA
        "Este año"        -> UnidadBucket.MES
        else              -> UnidadBucket.DIA
    }

    private fun delta(actual: BigDecimal, anterior: BigDecimal): DeltaMetrica {
        val pct = if (anterior.compareTo(BigDecimal.ZERO) == 0) null
        else (actual - anterior).divide(anterior.abs().coerceAtLeast(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP)
        return DeltaMetrica(
            actual       = actual,
            anterior     = anterior,
            porcentaje   = pct?.abs(),
            esIncremento = actual > anterior,
        )
    }

    private fun construirSerie(
        rango: RangoPeriodo,
        zona: ZoneId,
        unidad: UnidadBucket,
        txs: List<com.example.flowtrack.domain.model.Transaccion>,
        movs: List<com.example.flowtrack.domain.model.MovimientoTarjeta>,
        balanceBase: BigDecimal
    ): List<PuntoSerie> {
        val buckets = when (unidad) {
            UnidadBucket.DIA    -> diasEnRango(rango, zona)
            UnidadBucket.SEMANA -> semanasEnRango(rango, zona)
            UnidadBucket.MES    -> mesesEnRango(rango, zona)
        }

        var balanceAcumulado = balanceBase
        return buckets.map { (etiqueta, inicio, fin) ->
            val txBucket = txs.filter { !it.fecha.isBefore(inicio) && it.fecha.isBefore(fin) }
            val movBucket = movs.filter { !it.fechaTransaccion.isBefore(inicio) && it.fechaTransaccion.isBefore(fin) }
            val totales = calcularTotalesFinancieros(txBucket, movBucket)
            balanceAcumulado += totales.ingresos - totales.gastos
            PuntoSerie(
                etiqueta         = etiqueta,
                instanteInicio   = inicio,
                gasto            = totales.gastos,
                ingreso          = totales.ingresos,
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
