package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.Dispatchers
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
) {
    val balance: BigDecimal get() = ingresos - gastos
}

data class ResumenPorPeriodo(
    val tipo: TipoPeriodo,
    val buckets: List<BucketResumen>,
    val totalIngresos: BigDecimal,
    val totalGastos: BigDecimal,
) {
    val balanceTotal: BigDecimal get() = totalIngresos - totalGastos
}

/**
 * Agrupa transacciones y movimientos de tarjeta por período (día/semana/mes) dentro de un rango,
 * para los Resúmenes diario/semanal/mensual in-app (issue #2, bloque D).
 *
 * La clasificación ingreso/gasto es consistente con [ObtenerResumenUseCase].
 */
class ObtenerResumenPeriodoUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
) {
    private val zona = ZoneId.of("America/Santo_Domingo")

    /** Tipos de movimiento de tarjeta que cuentan como gasto. */
    private val tiposGastoTarjeta = setOf(
        TipoMovimientoTarjeta.COMPRA,
        TipoMovimientoTarjeta.AVANCE_EFECTIVO,
        TipoMovimientoTarjeta.INTERES,
        TipoMovimientoTarjeta.COMISION,
    )

    /** Tipos de movimiento de tarjeta que cuentan como ingreso (pagos/devoluciones/cashback). */
    private val tiposCreditoTarjeta = setOf(
        TipoMovimientoTarjeta.PAGO,
        TipoMovimientoTarjeta.CASHBACK,
        TipoMovimientoTarjeta.DEVOLUCION,
    )

    suspend fun ejecutar(
        uid: String,
        inicio: Instant,
        fin: Instant,
        tipo: TipoPeriodo,
    ): AppResult<ResumenPorPeriodo> {
        val resTx = transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 0)
        if (resTx is AppResult.Error) return AppResult.Error(resTx.error)
        val resMov = movimientoTarjetaRepository.obtenerMovimientos(uid, inicio, fin)
        if (resMov is AppResult.Error) return AppResult.Error(resMov.error)

        val transacciones = (resTx as AppResult.Success).data
        val movimientos = (resMov as AppResult.Success).data

        return withContext(Dispatchers.Default) {
            AppResult.Success(calcular(transacciones, movimientos, tipo))
        }
    }

    /** Núcleo puro y testeable: agrupa en buckets según [tipo]. */
    fun calcular(
        transacciones: List<Transaccion>,
        movimientos: List<MovimientoTarjeta>,
        tipo: TipoPeriodo,
    ): ResumenPorPeriodo {
        val ingresosPorBucket = mutableMapOf<LocalDate, BigDecimal>()
        val gastosPorBucket = mutableMapOf<LocalDate, BigDecimal>()

        fun sumar(mapa: MutableMap<LocalDate, BigDecimal>, fecha: Instant, monto: BigDecimal) {
            val k = claveBucket(fecha.atZone(zona).toLocalDate(), tipo)
            mapa[k] = (mapa[k] ?: BigDecimal.ZERO) + monto
        }

        transacciones.filter { !it.esDerivada }.forEach { tx ->
            when (tx.tipo) {
                TipoTransaccion.CREDITO -> sumar(ingresosPorBucket, tx.fecha, tx.monto)
                TipoTransaccion.DEBITO  -> sumar(gastosPorBucket, tx.fecha, tx.monto)
            }
        }
        movimientos.forEach { mov ->
            when (mov.tipoMovimiento) {
                in tiposGastoTarjeta   -> sumar(gastosPorBucket, mov.fechaTransaccion, mov.monto)
                in tiposCreditoTarjeta -> sumar(ingresosPorBucket, mov.fechaTransaccion, mov.monto)
                else -> Unit
            }
        }

        val claves = (ingresosPorBucket.keys + gastosPorBucket.keys).toSortedSet()
        val buckets = claves.map { k ->
            BucketResumen(
                etiqueta = etiqueta(k, tipo),
                inicio = k,
                ingresos = ingresosPorBucket[k] ?: BigDecimal.ZERO,
                gastos = gastosPorBucket[k] ?: BigDecimal.ZERO,
            )
        }

        return ResumenPorPeriodo(
            tipo = tipo,
            buckets = buckets,
            totalIngresos = ingresosPorBucket.values.fold(BigDecimal.ZERO, BigDecimal::add),
            totalGastos = gastosPorBucket.values.fold(BigDecimal.ZERO, BigDecimal::add),
        )
    }

    /** Fecha canónica del bucket al que pertenece [fecha]. */
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
