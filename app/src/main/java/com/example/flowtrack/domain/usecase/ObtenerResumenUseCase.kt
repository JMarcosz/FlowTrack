package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.domain.model.TipoTransaccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val balanceNeto: BigDecimal, // Suma de balances finales de las cuentas al cierre del periodo
    val porCategoria: List<ResumenCategoria>,
    val porBanco: List<ResumenBanco>
)

class ObtenerResumenUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
    private val cuentaRepository: CuentaRepository,
) {
    suspend fun ejecutar(uid: String, inicio: Instant, fin: Instant): AppResult<ResumenGeneral> = coroutineScope {
        val txDeferred = async { transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 0) }
        val movDeferred = async { movimientoTarjetaRepository.obtenerMovimientos(uid, inicio, fin) }
        val cuentasDeferred = async { cuentaRepository.obtenerCuentas(uid) }

        val resTx = txDeferred.await()
        if (resTx is AppResult.Error) return@coroutineScope AppResult.Error(resTx.error)

        val resMov = movDeferred.await()
        if (resMov is AppResult.Error) return@coroutineScope AppResult.Error(resMov.error)

        val resCuentas = cuentasDeferred.await()
        if (resCuentas is AppResult.Error) return@coroutineScope AppResult.Error(resCuentas.error)

        val transacciones = (resTx as AppResult.Success).data
        val movimientos = (resMov as AppResult.Success).data
        val cuentasVisibles = (resCuentas as AppResult.Success).data.filter { it.activa && it.mostrarEnDashboard }

        withContext(Dispatchers.Default) {
            val totales = calcularTotalesFinancieros(transacciones, movimientos)
            
            // Calcular Balance Neto Real (Suma de balances finales)
            var balanceNeto = BigDecimal.ZERO
            cuentasVisibles.forEach { cuenta ->
                val ultimaTxRango = transacciones.filter { it.cuentaId == cuenta.id }.maxByOrNull { it.fecha }
                if (ultimaTxRango != null) {
                    balanceNeto += (ultimaTxRango.balanceDespues ?: BigDecimal.ZERO)
                } else {
                    // Buscar ultima transaccion historica previa al fin del periodo
                    val resPrevia = transaccionRepository.obtenerTransacciones(uid, null, fin, 1, cuenta.id)
                    if (resPrevia is AppResult.Success) {
                        val txPrevia = resPrevia.data.firstOrNull()
                        balanceNeto += (txPrevia?.balanceDespues ?: BigDecimal.ZERO)
                    }
                }
            }

            val totalFloat = totales.gastos.toFloat().coerceAtLeast(1f)

            // Agrupación por categoría
            val gastosPorCat = mutableMapOf<String, BigDecimal>()
            transacciones.filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }.forEach { tx ->
                val cat = tx.categoriaId ?: "sin_categorizar"
                gastosPorCat[cat] = (gastosPorCat[cat] ?: BigDecimal.ZERO) + tx.monto
            }
            movimientos.filter { it.tipoMovimiento.esGastoFinanciero() }.forEach { mov ->
                val cat = mov.categoriaId ?: "sin_categorizar"
                gastosPorCat[cat] = (gastosPorCat[cat] ?: BigDecimal.ZERO) + mov.monto
            }
            val porCategoria = gastosPorCat.map { (cat, total) ->
                ResumenCategoria(cat, total, (total.toFloat() / totalFloat) * 100f)
            }.sortedByDescending { it.total }

            // Agrupación por banco
            val ingresosPorBanco = mutableMapOf<String, BigDecimal>()
            val gastosPorBanco = mutableMapOf<String, BigDecimal>()
            
            transacciones.filter { !it.esDerivada }.forEach { tx ->
                if (tx.tipo == TipoTransaccion.CREDITO) {
                    ingresosPorBanco[tx.bancoCodigo] = (ingresosPorBanco[tx.bancoCodigo] ?: BigDecimal.ZERO) + tx.monto
                } else {
                    gastosPorBanco[tx.bancoCodigo] = (gastosPorBanco[tx.bancoCodigo] ?: BigDecimal.ZERO) + tx.monto
                }
            }
            movimientos.forEach { mov ->
                if (mov.tipoMovimiento.esIngresoFinanciero()) {
                    ingresosPorBanco[mov.bancoCodigo] = (ingresosPorBanco[mov.bancoCodigo] ?: BigDecimal.ZERO) + mov.monto
                } else if (mov.tipoMovimiento.esGastoFinanciero()) {
                    gastosPorBanco[mov.bancoCodigo] = (gastosPorBanco[mov.bancoCodigo] ?: BigDecimal.ZERO) + mov.monto
                }
            }

            val todosBancos = (gastosPorBanco.keys + ingresosPorBanco.keys).toSet()
            val porBanco = todosBancos.map { banco ->
                val g = gastosPorBanco[banco] ?: BigDecimal.ZERO
                val i = ingresosPorBanco[banco] ?: BigDecimal.ZERO
                ResumenBanco(banco, g, i, (g.toFloat() / totalFloat) * 100f)
            }.sortedByDescending { it.gastos }

            AppResult.Success(
                ResumenGeneral(
                    ingresosTotales = totales.ingresos,
                    gastosTotales = totales.gastos,
                    balanceNeto = balanceNeto,
                    porCategoria = porCategoria,
                    porBanco = porBanco,
                )
            )
        }
    }
}
