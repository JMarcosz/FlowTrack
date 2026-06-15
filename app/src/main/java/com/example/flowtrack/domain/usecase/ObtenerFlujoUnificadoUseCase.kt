package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import java.time.Instant

data class FlujoUnificado(
    val transacciones: List<Transaccion>,
    val movimientos: List<MovimientoTarjeta>,
)

class ObtenerFlujoUnificadoUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
) {

    suspend fun ejecutar(
        uid: String,
        inicio: Instant,
        fin: Instant,
    ): AppResult<FlujoUnificado> = coroutineScope {
        val txDeferred = async { transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 0) }
        val movDeferred = async { movimientoTarjetaRepository.obtenerMovimientos(uid, inicio, fin) }

        val resTx = txDeferred.await()
        if (resTx is AppResult.Error) return@coroutineScope AppResult.Error(resTx.error)

        val resMov = movDeferred.await()
        if (resMov is AppResult.Error) return@coroutineScope AppResult.Error(resMov.error)

        AppResult.Success(
            FlujoUnificado(
                transacciones = (resTx as AppResult.Success).data,
                movimientos = (resMov as AppResult.Success).data,
            )
        )
    }
}
