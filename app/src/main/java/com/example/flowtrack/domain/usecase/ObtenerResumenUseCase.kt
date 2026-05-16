package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

data class ResumenCategoria(val categoriaId: String, val total: BigDecimal, val porcentaje: Float)
data class ResumenBanco(val bancoCodigo: String, val total: BigDecimal, val porcentaje: Float)

data class ResumenGeneral(
    val ingresosTotales: BigDecimal,
    val gastosTotales: BigDecimal,
    val porCategoria: List<ResumenCategoria>,
    val porBanco: List<ResumenBanco>
)

/** Tipos de movimiento de tarjeta que suman a gastosTotales. */
private val TIPOS_GASTO_TARJETA = setOf(
    TipoMovimientoTarjeta.COMPRA,
    TipoMovimientoTarjeta.AVANCE_EFECTIVO,
    TipoMovimientoTarjeta.INTERES,
    TipoMovimientoTarjeta.COMISION,
)

/** Tipos de movimiento de tarjeta que reducen gastosTotales (pagos/devoluciones). */
private val TIPOS_CREDITO_TARJETA = setOf(
    TipoMovimientoTarjeta.PAGO,
    TipoMovimientoTarjeta.CASHBACK,
    TipoMovimientoTarjeta.DEVOLUCION,
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

        // Ingresos: créditos de cuenta + pagos/devoluciones/cashback de tarjeta
        val ingresosCuenta = transacciones
            .filter { it.tipo == TipoTransaccion.CREDITO && !it.esDerivada }
            .sumOf { it.monto }
        val ingresosIngresosNetos = movimientos
            .filter { it.tipoMovimiento in TIPOS_CREDITO_TARJETA }
            .sumOf { it.monto }
        val ingresosTotales = ingresosCuenta + ingresosIngresosNetos

        // Gastos: débitos de cuenta + compras/avances/intereses/comisiones de tarjeta
        val gastosCuenta = transacciones
            .filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
        val gastosTarjeta = movimientos
            .filter { it.tipoMovimiento in TIPOS_GASTO_TARJETA }

        val gastosCuentaTotal = gastosCuenta.sumOf { it.monto }
        val gastosTarjetaTotal = gastosTarjeta.sumOf { it.monto }
        val gastosTotales = gastosCuentaTotal + gastosTarjetaTotal

        val totalFloat = gastosTotales.toFloat().coerceAtLeast(1f)

        // Agrupación por categoría (cuenta + tarjeta combinados)
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
        val porBanco = gastosPorBanco.map { (banco, total) ->
            ResumenBanco(banco, total, (total.toFloat() / totalFloat) * 100f)
        }.sortedByDescending { it.total }

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
