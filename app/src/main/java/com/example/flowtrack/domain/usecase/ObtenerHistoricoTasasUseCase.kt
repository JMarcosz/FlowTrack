package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.TasaCambio
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class PuntoHistoricoTasa(
    val fecha: LocalDate,
    val compra: BigDecimal,
    val venta: BigDecimal,
)

data class HistoricoTasas(
    val historico: List<TasaCambio>,
    val serie: List<PuntoHistoricoTasa>,
)

class ObtenerHistoricoTasasUseCase @Inject constructor(
    private val repository: TasaCambioRepository,
) {
    suspend fun ejecutar(diasAtras: Int = 30): AppResult<HistoricoTasas> {
        return when (val res = repository.obtenerHistorico(diasAtras)) {
            is AppResult.Error -> AppResult.Error(res.error)
            is AppResult.Success -> {
                val historico = res.data.sortedBy { it.fecha }
                val serie = historico.takeLast(7).map {
                    PuntoHistoricoTasa(
                        fecha = it.fecha,
                        compra = it.compra,
                        venta = it.venta,
                    )
                }
                AppResult.Success(HistoricoTasas(historico = historico, serie = serie))
            }
        }
    }
}
