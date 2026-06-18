package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.CategoriaMeta
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.repository.ICuentaRepository
import com.example.flowtrack.domain.repository.IMetaRepository
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class CuentaMetaDisponible(
    val cuenta: Cuenta,
    val saldoTotal: BigDecimal,
    val saldoReservado: BigDecimal,
    val saldoDisponible: BigDecimal,
)

class ObservarMetasUseCase @Inject constructor(
    private val metaRepository: IMetaRepository,
) {
    operator fun invoke(uid: String): Flow<List<Meta>> = metaRepository.observarMetas(uid)
}

class CrearMetaUseCase @Inject constructor(
    private val metaRepository: IMetaRepository,
) {
    suspend operator fun invoke(
        uid: String,
        nombre: String,
        montoObjetivo: BigDecimal,
        categoria: CategoriaMeta,
        cuentaId: String?,
        descripcion: String?,
        fechaObjetivo: Instant?,
        emoji: String,
    ): AppResult<Unit> {
        if (nombre.isBlank()) {
            return AppResult.Error(ErrorApp.Desconocido("El nombre de la meta es obligatorio"))
        }
        if (montoObjetivo <= BigDecimal.ZERO) {
            return AppResult.Error(ErrorApp.Desconocido("El monto objetivo debe ser mayor que cero"))
        }

        val ahora = Instant.now()
        val meta = Meta(
            id = UUID.randomUUID().toString(),
            uidUsuario = uid,
            nombre = nombre.trim(),
            emoji = emoji,
            montoObjetivo = montoObjetivo.normalizarDinero(),
            montoActual = BigDecimal.ZERO.setScale(2),
            fechaLimite = fechaObjetivo,
            activa = true,
            creadoEn = ahora,
            descripcion = descripcion?.trim()?.takeIf { it.isNotBlank() },
            categoria = categoria,
            cuentaId = cuentaId,
            fechaObjetivo = fechaObjetivo,
            actualizadaEn = ahora,
        )

        return metaRepository.guardarMeta(meta)
    }
}

class ObtenerCuentasDisponiblesParaMetasUseCase @Inject constructor(
    private val cuentaRepository: ICuentaRepository,
    private val metaRepository: IMetaRepository,
    private val balancesPorCuentaUseCase: ObtenerBalancesPorCuentaUseCase,
) {
    suspend operator fun invoke(uid: String): AppResult<List<CuentaMetaDisponible>> {
        val cuentasResult = cuentaRepository.obtenerCuentas(uid)
        if (cuentasResult is AppResult.Error) return AppResult.Error(cuentasResult.error)

        val metasResult = metaRepository.obtenerMetas(uid)
        if (metasResult is AppResult.Error) return AppResult.Error(metasResult.error)

        val balancesResult = balancesPorCuentaUseCase.ejecutar(uid)
        if (balancesResult is AppResult.Error) return AppResult.Error(balancesResult.error)

        val cuentas = (cuentasResult as AppResult.Success).data
        val metas = (metasResult as AppResult.Success).data
        val balances = (balancesResult as AppResult.Success).data
        val reservadoPorCuenta = metas
            .filter { it.activa && it.cuentaId != null }
            .groupBy { it.cuentaId!! }
            .mapValues { (_, metasCuenta) ->
                metasCuenta.fold(BigDecimal.ZERO.setScale(2)) { acc, meta -> acc + meta.montoActual }
            }

        return AppResult.Success(
            cuentas
                .filter { it.activa }
                .mapNotNull { cuenta ->
                    val saldoTotal = cuenta.balanceEfectivo(balances) ?: return@mapNotNull null
                    val saldoReservado = reservadoPorCuenta[cuenta.id] ?: BigDecimal.ZERO.setScale(2)
                    CuentaMetaDisponible(
                        cuenta = cuenta,
                        saldoTotal = saldoTotal,
                        saldoReservado = saldoReservado,
                        saldoDisponible = (saldoTotal - saldoReservado).max(BigDecimal.ZERO).normalizarDinero(),
                    )
                }
        )
    }
}

class DepositarEnMetaUseCase @Inject constructor(
    private val metaRepository: IMetaRepository,
    private val cuentasDisponiblesUseCase: ObtenerCuentasDisponiblesParaMetasUseCase,
) {
    suspend operator fun invoke(
        uid: String,
        meta: Meta,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String = UUID.randomUUID().toString(),
    ): AppResult<Meta> {
        if (monto <= BigDecimal.ZERO) {
            return AppResult.Error(ErrorApp.Desconocido("El monto debe ser mayor que cero"))
        }
        if (meta.cuentaId != null && meta.cuentaId != cuentaId) {
            return AppResult.Error(ErrorApp.Desconocido("La meta pertenece a otra cuenta"))
        }

        val cuentasResult = cuentasDisponiblesUseCase(uid)
        if (cuentasResult is AppResult.Error) return AppResult.Error(cuentasResult.error)

        val cuenta = (cuentasResult as AppResult.Success).data.firstOrNull { it.cuenta.id == cuentaId }
            ?: return AppResult.Error(ErrorApp.Desconocido("Cuenta no disponible"))
        if (monto > cuenta.saldoDisponible) {
            return AppResult.Error(ErrorApp.Desconocido("No tienes saldo disponible suficiente en la cuenta seleccionada"))
        }

        return metaRepository.depositar(uid, meta.id, cuentaId, monto.normalizarDinero(), requestId)
    }
}

class RetirarDeMetaUseCase @Inject constructor(
    private val metaRepository: IMetaRepository,
) {
    suspend operator fun invoke(
        uid: String,
        meta: Meta,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String = UUID.randomUUID().toString(),
    ): AppResult<Meta> {
        if (monto <= BigDecimal.ZERO) {
            return AppResult.Error(ErrorApp.Desconocido("El monto debe ser mayor que cero"))
        }
        if (monto > meta.montoActual) {
            return AppResult.Error(ErrorApp.Desconocido("La meta no tiene fondos suficientes"))
        }
        if (meta.cuentaId != null && meta.cuentaId != cuentaId) {
            return AppResult.Error(ErrorApp.Desconocido("La meta pertenece a otra cuenta"))
        }

        return metaRepository.retirar(uid, meta.id, cuentaId, monto.normalizarDinero(), requestId)
    }
}

class CancelarMetaUseCase @Inject constructor(
    private val metaRepository: IMetaRepository,
) {
    suspend operator fun invoke(uid: String, metaId: String): AppResult<Unit> =
        metaRepository.cancelarMeta(uid, metaId)
}

private fun BigDecimal.normalizarDinero(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
