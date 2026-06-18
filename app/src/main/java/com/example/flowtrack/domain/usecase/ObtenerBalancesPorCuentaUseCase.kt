package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.esContabilizable
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Devuelve, para cada cuenta del usuario, el balanceDespues de su transacción más reciente
 * (por fecha) que tenga balanceDespues != null y NO sea derivada.
 *
 * Una sola query Firestore sin límite; el agrupamiento ocurre en memoria.
 * Las cuentas sin transacciones con balanceDespues (ej. Qik/Cibao) no aparecen en el mapa
 * — el consumidor debe caer en Cuenta.balanceActual como fallback.
 */
@Singleton
class ObtenerBalancesPorCuentaUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
) {
    suspend fun ejecutar(uid: String): AppResult<Map<String, BigDecimal>> {
        val result = transaccionRepository.obtenerTransacciones(uid, inicio = null, fin = null, limite = 0)
        if (result is AppResult.Error) return AppResult.Error(result.error)
        val transacciones = (result as AppResult.Success).data

        val mapa = transacciones
            .filter { it.esContabilizable && it.balanceDespues != null }
            .groupBy { it.cuentaId }
            .mapValues { (_, txs) ->
                txs.maxByOrNull { it.fecha }!!.balanceDespues!!
            }

        return AppResult.Success(mapa)
    }
}

/**
 * Retorna el balance derivado de transacciones (más confiable), con fallback a
 * Cuenta.balanceActual si la cuenta no tiene transacciones con balanceDespues.
 */
fun Cuenta.balanceEfectivo(balancesPorCuenta: Map<String, BigDecimal>): BigDecimal? =
    balancesPorCuenta[id] ?: balanceActual
