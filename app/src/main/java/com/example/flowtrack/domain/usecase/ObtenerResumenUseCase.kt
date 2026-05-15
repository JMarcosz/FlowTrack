package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
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

class ObtenerResumenUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository
) {
    suspend fun ejecutar(uid: String, inicio: Instant, fin: Instant): AppResult<ResumenGeneral> {
        val result = transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 5000)
        
        if (result is AppResult.Error) return AppResult.Error(result.error)
        
        val transacciones = (result as AppResult.Success).data
        
        val ingresos = transacciones.filter { it.tipo == TipoTransaccion.CREDITO }.sumOf { it.monto }
        val gastos = transacciones.filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
        
        val gastosTotales = gastos.sumOf { it.monto }
        val totalFloat = gastosTotales.toFloat().coerceAtLeast(1f)
        
        val porCategoria = gastos.groupBy { it.categoriaId ?: "Sin Categorizar" }
            .map { (cat, txs) -> 
                val totalCat = txs.sumOf { it.monto }
                ResumenCategoria(cat, totalCat, (totalCat.toFloat() / totalFloat) * 100f)
            }
            .sortedByDescending { it.total }

        val porBanco = gastos.groupBy { it.bancoCodigo }
            .map { (banco, txs) ->
                val totalBanco = txs.sumOf { it.monto }
                ResumenBanco(banco, totalBanco, (totalBanco.toFloat() / totalFloat) * 100f)
            }
            .sortedByDescending { it.total }

        return AppResult.Success(
            ResumenGeneral(
                ingresosTotales = ingresos,
                gastosTotales = gastosTotales,
                porCategoria = porCategoria,
                porBanco = porBanco
            )
        )
    }
}
