package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.CategoriaCatalogo
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.model.esContabilizable
import com.example.flowtrack.presentation.model.FiltrosAvanzadosState
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
    val balance: BigDecimal = ingresos - gastos,
) {
    val flujoNeto: BigDecimal get() = ingresos - gastos
}

data class ResumenGeneral(
    val ingresosTotales: BigDecimal,
    val gastosTotales: BigDecimal,
    val balanceNeto: BigDecimal, // Suma de balances finales de las cuentas al cierre del periodo (afectado por filtro de banco)
    val porCategoria: List<ResumenCategoria>,
    val porBanco: List<ResumenBanco>,
    val gastosPorBanco: List<ResumenBanco> // Agregado para compatibilidad con ResumenViewModel (usando porBanco)
) {
    val gastosPorCategoria = porCategoria
}

class ObtenerResumenUseCase @Inject constructor(
    private val flujoUnificadoUseCase: ObtenerFlujoUnificadoUseCase,
    private val transaccionRepository: TransaccionRepository,
    private val cuentaRepository: CuentaRepository,
    private val tasaCambioRepository: TasaCambioRepository,
) {
    suspend fun ejecutar(
        uid: String,
        inicio: Instant,
        fin: Instant,
        filtros: FiltrosAvanzadosState = FiltrosAvanzadosState(),
    ): AppResult<ResumenGeneral> = coroutineScope {
        val flujoDeferred = async { flujoUnificadoUseCase.ejecutar(uid, inicio, fin) }
        val cuentasDeferred = async { cuentaRepository.obtenerCuentas(uid) }
        val tasaDeferred = async { tasaCambioRepository.obtenerTasaDelDia() }

        val resFlujo = flujoDeferred.await()
        if (resFlujo is AppResult.Error) return@coroutineScope AppResult.Error(resFlujo.error)

        val resCuentas = cuentasDeferred.await()
        if (resCuentas is AppResult.Error) return@coroutineScope AppResult.Error(resCuentas.error)

        val tasaCambio = (tasaDeferred.await() as? AppResult.Success)?.data

        val flujo = (resFlujo as AppResult.Success).data
        val transaccionesBase = flujo.transacciones.map { it.normalizarMoneda(tasaCambio) }
        var transacciones = transaccionesBase
        var movimientos = flujo.movimientos.map { it.normalizarMoneda(tasaCambio) }
        var cuentasVisibles = (resCuentas as AppResult.Success).data.filter { it.activa && it.mostrarEnDashboard }

        if (filtros.bancoId != null) {
            transacciones = transacciones.filter { it.bancoCodigo == filtros.bancoId }
            cuentasVisibles = cuentasVisibles.filter { it.bancoCodigo == filtros.bancoId }
        }

        if (filtros.rangoMonto.minimo != null) {
            transacciones = transacciones.filter { it.monto >= filtros.rangoMonto.minimo }
            movimientos = movimientos.filter { it.monto >= filtros.rangoMonto.minimo }
        }

        if (filtros.rangoMonto.maximo != null) {
            transacciones = transacciones.filter { it.monto <= filtros.rangoMonto.maximo }
            movimientos = movimientos.filter { it.monto <= filtros.rangoMonto.maximo }
        }

        fun categoriaNormalizada(id: String?): String? = CategoriaCatalogo.normalizarId(id)

        if (filtros.soloSinCategorizar) {
            transacciones = transacciones.filter {
                categoriaNormalizada(it.categoriaId) == null ||
                    categoriaNormalizada(it.categoriaId) == CategoriaCatalogo.SIN_CATEGORIZAR
            }
            movimientos = movimientos.filter {
                categoriaNormalizada(it.categoriaId) == null ||
                    categoriaNormalizada(it.categoriaId) == CategoriaCatalogo.SIN_CATEGORIZAR
            }
        } else if (filtros.categorias.isNotEmpty()) {
            transacciones = transacciones.filter { categoriaNormalizada(it.categoriaId) in filtros.categorias }
            movimientos = movimientos.filter { categoriaNormalizada(it.categoriaId) in filtros.categorias }
        }

        withContext(Dispatchers.Default) {
            val totales = calcularTotalesFinancieros(transacciones, movimientos)

            val balancesPorCuenta = mutableMapOf<String, BigDecimal>()
            for (cuenta in cuentasVisibles) {
                balancesPorCuenta[cuenta.id] = balanceCuentaAlCierre(
                    uid = uid,
                    cuenta = cuenta,
                    fin = fin,
                    tasaCambio = tasaCambio,
                    transaccionesBase = transaccionesBase,
                )
            }
            val balancesPorBanco = cuentasVisibles.groupBy { it.bancoCodigo }.mapValues { (_, cuentasBanco) ->
                cuentasBanco.fold(BigDecimal.ZERO) { acc, cuenta ->
                    acc + (balancesPorCuenta[cuenta.id] ?: BigDecimal.ZERO)
                }
            }
            val balanceNeto = balancesPorCuenta.values.fold(BigDecimal.ZERO) { acc, balance -> acc + balance }

            val totalFloat = totales.gastos.toFloat().coerceAtLeast(1f)

            val gastosPorCat = mutableMapOf<String, BigDecimal>()
            transacciones.filter { it.tipo == TipoTransaccion.DEBITO && it.esContabilizable }.forEach { tx ->
                val cat = categoriaNormalizada(tx.categoriaId) ?: CategoriaCatalogo.SIN_CATEGORIZAR
                gastosPorCat[cat] = (gastosPorCat[cat] ?: BigDecimal.ZERO) + tx.monto
            }
            movimientos.filter { it.tipoMovimiento.esGastoFinanciero() }.forEach { mov ->
                val cat = categoriaNormalizada(mov.categoriaId) ?: CategoriaCatalogo.SIN_CATEGORIZAR
                gastosPorCat[cat] = (gastosPorCat[cat] ?: BigDecimal.ZERO) + mov.monto
            }
            val porCategoria = gastosPorCat.map { (cat, total) ->
                ResumenCategoria(cat, total, (total.toFloat() / totalFloat) * 100f)
            }.sortedByDescending { it.total }

            val ingresosPorBanco = mutableMapOf<String, BigDecimal>()
            val gastosPorBancoMap = mutableMapOf<String, BigDecimal>()

            transacciones.filter { it.esContabilizable }.forEach { tx ->
                if (tx.tipo == TipoTransaccion.CREDITO) {
                    ingresosPorBanco[tx.bancoCodigo] = (ingresosPorBanco[tx.bancoCodigo] ?: BigDecimal.ZERO) + tx.monto
                } else {
                    gastosPorBancoMap[tx.bancoCodigo] = (gastosPorBancoMap[tx.bancoCodigo] ?: BigDecimal.ZERO) + tx.monto
                }
            }
            movimientos.forEach { mov ->
                if (mov.tipoMovimiento.esIngresoFinanciero()) {
                    ingresosPorBanco[mov.bancoCodigo] = (ingresosPorBanco[mov.bancoCodigo] ?: BigDecimal.ZERO) + mov.monto
                } else if (mov.tipoMovimiento.esGastoFinanciero()) {
                    gastosPorBancoMap[mov.bancoCodigo] = (gastosPorBancoMap[mov.bancoCodigo] ?: BigDecimal.ZERO) + mov.monto
                }
            }

            val todosBancos = (gastosPorBancoMap.keys + ingresosPorBanco.keys + balancesPorBanco.keys).toSet()
            val porBanco = todosBancos.map { banco ->
                val g = gastosPorBancoMap[banco] ?: BigDecimal.ZERO
                val i = ingresosPorBanco[banco] ?: BigDecimal.ZERO
                ResumenBanco(
                    bancoCodigo = banco,
                    gastos = g,
                    ingresos = i,
                    porcentaje = (g.toFloat() / totalFloat) * 100f,
                    balance = balancesPorBanco[banco] ?: BigDecimal.ZERO,
                )
            }.sortedByDescending { it.gastos }

            AppResult.Success(
                ResumenGeneral(
                    ingresosTotales = totales.ingresos,
                    gastosTotales = totales.gastos,
                    balanceNeto = balanceNeto,
                    porCategoria = porCategoria,
                    porBanco = porBanco,
                    gastosPorBanco = porBanco,
                )
            )
        }
    }

    private suspend fun balanceCuentaAlCierre(
        uid: String,
        cuenta: Cuenta,
        fin: Instant,
        tasaCambio: TasaCambio?,
        transaccionesBase: List<Transaccion>,
    ): BigDecimal {
        val balanceDeCuentaActual = cuenta.balanceActual
        val fechaUltimoCorte = cuenta.fechaUltimoCorte
        if (balanceDeCuentaActual != null && fechaUltimoCorte != null && !fechaUltimoCorte.isAfter(fin)) {
            return balanceDeCuentaActual
        }

        val ultimaTxRango = transaccionesBase
            .filter { it.cuentaId == cuenta.id && it.balanceDespues != null && !it.fecha.isAfter(fin) && it.esContabilizable }
            .maxWithOrNull(compareBy<Transaccion> { it.fecha }.thenBy { it.creadoEn }.thenBy { it.id })
        if (ultimaTxRango?.balanceDespues != null) {
            return ultimaTxRango.balanceDespues
        }

        val resPrevia = transaccionRepository.obtenerTransacciones(
            uid = uid,
            inicio = null,
            fin = fin,
            limite = 1,
            cuentaId = cuenta.id,
        )
        if (resPrevia is AppResult.Success) {
            val txPrevia = resPrevia.data.firstOrNull { it.balanceDespues != null && it.esContabilizable }
                ?.normalizarMoneda(tasaCambio)
            if (txPrevia?.balanceDespues != null) return txPrevia.balanceDespues
        }

        return balanceDeCuentaActual ?: BigDecimal.ZERO
    }
}
