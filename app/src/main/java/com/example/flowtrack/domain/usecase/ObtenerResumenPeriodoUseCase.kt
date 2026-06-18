package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.model.esContabilizable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

/** Granularidad del agrupamiento de resúmenes. */
enum class TipoPeriodo { DIA, SEMANA, MES }

/** Un bucket de resumen (un día, una semana o un mes). */
data class BucketResumen(
    val etiqueta: String,
    val inicio: LocalDate,
    val ingresos: BigDecimal,
    val gastos: BigDecimal,
    val balance: BigDecimal, // Ahora representa el balance final al cierre del bucket
)

data class ResumenPorPeriodo(
    val tipo: TipoPeriodo,
    val buckets: List<BucketResumen>,
    val totalIngresos: BigDecimal,
    val totalGastos: BigDecimal,
    val balanceFinal: BigDecimal, // Balance real al final de todo el rango
)

/**
 * Agrupa transacciones y movimientos de tarjeta por período (día/semana/mes) dentro de un rango.
 */
class ObtenerResumenPeriodoUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
    private val cuentaRepository: CuentaRepository,
) {
    private val zona = ZoneId.of("America/Santo_Domingo")

    suspend fun ejecutar(
        uid: String,
        inicio: Instant,
        fin: Instant,
        tipo: TipoPeriodo,
    ): AppResult<ResumenPorPeriodo> = coroutineScope {
        val resTxDeferred = async { transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 0) }
        val resMovDeferred = async { movimientoTarjetaRepository.obtenerMovimientos(uid, inicio, fin) }
        val resCuentasDeferred = async { cuentaRepository.obtenerCuentas(uid) }

        val resTx = resTxDeferred.await()
        if (resTx is AppResult.Error) return@coroutineScope AppResult.Error(resTx.error)
        val resMov = resMovDeferred.await()
        if (resMov is AppResult.Error) return@coroutineScope AppResult.Error(resMov.error)
        val resCuentas = resCuentasDeferred.await()
        if (resCuentas is AppResult.Error) return@coroutineScope AppResult.Error(resCuentas.error)

        val transacciones = (resTx as AppResult.Success).data
        val movimientos = (resMov as AppResult.Success).data
        val cuentasVisibles = (resCuentas as AppResult.Success).data.filter { it.activa && it.mostrarEnDashboard }

        // Obtener balances iniciales (antes del periodo)
        val resTodasTx = transaccionRepository.obtenerTransacciones(uid, null, null, 0)
        val todasTxs = if (resTodasTx is AppResult.Success) resTodasTx.data else emptyList()

        withContext(Dispatchers.Default) {
            val balanceInicialTotal = calcularBalanceADate(todasTxs, cuentasVisibles, inicio)
            AppResult.Success(calcular(transacciones, movimientos, tipo, balanceInicialTotal, todasTxs, cuentasVisibles, fin))
        }
    }

    private fun calcularBalanceADate(
        todasTxs: List<Transaccion>,
        cuentas: List<com.example.flowtrack.domain.model.Cuenta>,
        fechaCorte: Instant
    ): BigDecimal {
        var total = BigDecimal.ZERO
        cuentas.forEach { cuenta ->
            val ultimaPrevia = todasTxs
                .filter { it.cuentaId == cuenta.id && !it.fecha.isAfter(fechaCorte) && it.esContabilizable }
                .maxByOrNull { it.fecha }
            total += (ultimaPrevia?.balanceDespues ?: BigDecimal.ZERO)
        }
        return total
    }

    fun calcular(
        transacciones: List<Transaccion>,
        movimientos: List<MovimientoTarjeta>,
        tipo: TipoPeriodo,
        balanceInicial: BigDecimal,
        todasTxs: List<Transaccion>,
        cuentas: List<com.example.flowtrack.domain.model.Cuenta>,
        finRango: Instant
    ): ResumenPorPeriodo {
        val ingresosPorBucket = mutableMapOf<LocalDate, BigDecimal>()
        val gastosPorBucket = mutableMapOf<LocalDate, BigDecimal>()

        fun sumar(mapa: MutableMap<LocalDate, BigDecimal>, fecha: Instant, monto: BigDecimal) {
            val k = claveBucket(fecha.atZone(zona).toLocalDate(), tipo)
            mapa[k] = (mapa[k] ?: BigDecimal.ZERO) + monto
        }

        transacciones.filter { it.esContabilizable }.forEach { tx ->
            when (tx.tipo) {
                TipoTransaccion.CREDITO -> sumar(ingresosPorBucket, tx.fecha, tx.monto)
                TipoTransaccion.DEBITO  -> sumar(gastosPorBucket, tx.fecha, tx.monto)
            }
        }
        movimientos.forEach { mov ->
            when {
                mov.tipoMovimiento.esGastoFinanciero() ->
                    sumar(gastosPorBucket, mov.fechaTransaccion, mov.monto)
                mov.tipoMovimiento.esIngresoFinanciero() ->
                    sumar(ingresosPorBucket, mov.fechaTransaccion, mov.monto)
            }
        }

        val claves = (ingresosPorBucket.keys + gastosPorBucket.keys).toSortedSet()
        
        var balanceRastreado = balanceInicial
        val buckets = claves.map { k ->
            val ing = ingresosPorBucket[k] ?: BigDecimal.ZERO
            val gas = gastosPorBucket[k] ?: BigDecimal.ZERO
            balanceRastreado += (ing - gas)
            
            BucketResumen(
                etiqueta = etiqueta(k, tipo),
                inicio = k,
                ingresos = ing,
                gastos = gas,
                balance = balanceRastreado,
            )
        }

        val balanceFinalReal = calcularBalanceADate(todasTxs, cuentas, finRango)

        return ResumenPorPeriodo(
            tipo = tipo,
            buckets = buckets,
            totalIngresos = ingresosPorBucket.values.fold(BigDecimal.ZERO, BigDecimal::add),
            totalGastos = gastosPorBucket.values.fold(BigDecimal.ZERO, BigDecimal::add),
            balanceFinal = balanceFinalReal
        )
    }

    private fun claveBucket(fecha: LocalDate, tipo: TipoPeriodo): LocalDate = when (tipo) {
        TipoPeriodo.DIA    -> fecha
        TipoPeriodo.SEMANA -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TipoPeriodo.MES    -> fecha.withDayOfMonth(1)
    }

    private val fmtDia = DateTimeFormatter.ofPattern("dd/MM", Locale("es", "DO"))
    private val fmtMes = DateTimeFormatter.ofPattern("MMM yyyy", Locale("es", "DO"))

    private fun etiqueta(inicio: LocalDate, tipo: TipoPeriodo): String = when (tipo) {
        TipoPeriodo.DIA    -> inicio.format(fmtDia)
        TipoPeriodo.SEMANA -> "Sem ${inicio.format(fmtDia)}"
        TipoPeriodo.MES    -> inicio.format(fmtMes).replaceFirstChar { it.uppercase() }
    }
}
