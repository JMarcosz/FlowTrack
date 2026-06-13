package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Tarjeta
import java.math.BigDecimal
import javax.inject.Inject

data class BalanceNeto(
    val totalActivos: BigDecimal,
    val totalPasivos: BigDecimal,
    val neto: BigDecimal,
    val cuentasConBalance: List<Cuenta>,
    val tarjetasConDeuda: List<Pair<Tarjeta, BigDecimal>>,
)

class ObtenerBalanceNetoUseCase @Inject constructor(
    private val cuentaRepository: CuentaRepository,
    private val tarjetaRepository: TarjetaRepository,
    private val balancesPorCuentaUseCase: ObtenerBalancesPorCuentaUseCase,
) {
    suspend fun ejecutar(uid: String): AppResult<BalanceNeto> {
        val cuentasResult = cuentaRepository.obtenerCuentas(uid)
        if (cuentasResult is AppResult.Error) return AppResult.Error(cuentasResult.error)

        val balancesMap = (balancesPorCuentaUseCase.ejecutar(uid) as? AppResult.Success)?.data
            ?: emptyMap()

        val cuentas = (cuentasResult as AppResult.Success).data
            .filter { it.activa && it.mostrarEnDashboard && it.balanceEfectivo(balancesMap) != null }

        val tarjetasResult = tarjetaRepository.obtenerTarjetas(uid)
        if (tarjetasResult is AppResult.Error) return AppResult.Error(tarjetasResult.error)
        val tarjetas = (tarjetasResult as AppResult.Success).data.filter { it.activa }

        val tarjetasConDeuda = tarjetas.mapNotNull { tarjeta ->
            val snapsResult = tarjetaRepository.obtenerEstadosTarjeta(uid, tarjeta.id)
            val snap = (snapsResult as? AppResult.Success)?.data?.firstOrNull()
            snap?.takeIf { it.balanceAlCorte > BigDecimal.ZERO }?.let { tarjeta to it.balanceAlCorte }
        }

        val totalActivos = cuentas.fold(BigDecimal.ZERO) { acc, c ->
            acc + (c.balanceEfectivo(balancesMap) ?: BigDecimal.ZERO)
        }
        val totalPasivos = tarjetasConDeuda.fold(BigDecimal.ZERO) { acc, (_, b) -> acc + b }

        return AppResult.Success(
            BalanceNeto(
                totalActivos = totalActivos,
                totalPasivos = totalPasivos,
                neto = totalActivos - totalPasivos,
                cuentasConBalance = cuentas,
                tarjetasConDeuda = tarjetasConDeuda,
            )
        )
    }
}
