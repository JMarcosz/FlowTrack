package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoTransaccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

data class ResumenCategoria(val categoriaId: String, val total: BigDecimal, val porcentaje: Float)
data class ResumenBanco(
    val bancoCodigo: String,
    val gastos: BigDecimal,
    val ingresos: BigDecimal,
    val porcentaje: Float,
) {
    val balance: BigDecimal get() = ingresos - gastos
}

data class ResumenGeneral(
    val ingresosTotales: BigDecimal,
    val gastosTotales: BigDecimal,
    val porCategoria: List<ResumenCategoria>,
    val porBanco: List<ResumenBanco>
)

class ObtenerResumenUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
) {
    suspend fun ejecutar(uid: String, inicio: Instant, fin: Instant): AppResult<ResumenGeneral> {
        val resTx = transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 0)
        if (resTx is AppResult.Error) return AppResult.Error(resTx.error)

        val resMov = movimientoTarjetaRepository.obtenerMovimientos(uid, inicio, fin)
        if (resMov is AppResult.Error) return AppResult.Error(resMov.error)

        val transacciones = (resTx as AppResult.Success).data
        val movimientos = (resMov as AppResult.Success).data

        return withContext(Dispatchers.Default) { calcular(transacciones, movimientos) }
    }

    private fun calcular(
        transacciones: List<com.example.flowtrack.domain.model.Transaccion>,
        movimientos: List<com.example.flowtrack.domain.model.MovimientoTarjeta>,
    ): AppResult<ResumenGeneral> {
        val totales = calcularTotalesFinancieros(transacciones, movimientos)
        val ingresosTotales = totales.ingresos
        val gastosTotales = totales.gastos

        val totalFloat = gastosTotales.toFloat().coerceAtLeast(1f)

        // Agrupación por categoría (cuenta + tarjeta combinados)
        val gastosCuenta = transacciones.filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
        val gastosTarjeta = movimientos.filter { it.tipoMovimiento.esGastoFinanciero() }

        val gastosPorCat = mutableMapOf<String, BigDecimal>()
        gastosCuenta.forEach { tx ->
            val cat = tx.categoriaId ?: "Sin Categorizar"
            gastosPorCat[cat] = (gastosPorCat[cat] ?: BigDecimal.ZERO) + tx.monto
        }
        gastosTarjeta.forEach { mov ->
            val cat = mov.categoriaId ?: "Sin Categorizar"
            gastosPorCat[cat] = (gastosPorCat[cat] ?: BigDecimal.ZERO) + mov.monto
        }
        val porCategoria = gastosPorCat.map { (cat, total) ->
            ResumenCategoria(cat, total, (total.toFloat() / totalFloat) * 100f)
        }.sortedByDescending { it.total }

        // Agrupación por banco (cuenta + tarjeta combinados)
        val gastosPorBanco = mutableMapOf<String, BigDecimal>()
        gastosCuenta.forEach { tx ->
            gastosPorBanco[tx.bancoCodigo] = (gastosPorBanco[tx.bancoCodigo] ?: BigDecimal.ZERO) + tx.monto
        }
        gastosTarjeta.forEach { mov ->
            gastosPorBanco[mov.bancoCodigo] = (gastosPorBanco[mov.bancoCodigo] ?: BigDecimal.ZERO) + mov.monto
        }

        val ingresosPorBanco = mutableMapOf<String, BigDecimal>()
        transacciones.filter { it.tipo == TipoTransaccion.CREDITO && !it.esDerivada }.forEach { tx ->
            ingresosPorBanco[tx.bancoCodigo] = (ingresosPorBanco[tx.bancoCodigo] ?: BigDecimal.ZERO) + tx.monto
        }
        movimientos.filter { it.tipoMovimiento.esIngresoFinanciero() }.forEach { mov ->
            ingresosPorBanco[mov.bancoCodigo] = (ingresosPorBanco[mov.bancoCodigo] ?: BigDecimal.ZERO) + mov.monto
        }

        val todosBancos = (gastosPorBanco.keys + ingresosPorBanco.keys).toSet()
        val porBanco = todosBancos.map { banco ->
            val g = gastosPorBanco[banco] ?: BigDecimal.ZERO
            val i = ingresosPorBanco[banco] ?: BigDecimal.ZERO
            ResumenBanco(banco, g, i, (g.toFloat() / totalFloat) * 100f)
        }.sortedByDescending { it.gastos }

        return AppResult.Success(
            ResumenGeneral(
                ingresosTotales = ingresosTotales,
                gastosTotales = gastosTotales,
                porCategoria = porCategoria,
                porBanco = porBanco,
            )
        )
    }
}