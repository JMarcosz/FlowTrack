package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.TasaCambio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class ObtenerHistoricoTasasUseCaseTest {

    @Test
    fun `convierte historico en serie grafica de siete puntos`() = runBlocking {
        val repository = mock<TasaCambioRepository>()
        whenever(repository.obtenerHistorico(30)).thenReturn(
            AppResult.Success(
                listOf(
                    TasaCambio(BigDecimal("58.0"), BigDecimal("59.0"), LocalDate.of(2026, 1, 1), "Mock"),
                    TasaCambio(BigDecimal("58.1"), BigDecimal("59.1"), LocalDate.of(2026, 1, 2), "Mock"),
                    TasaCambio(BigDecimal("58.2"), BigDecimal("59.2"), LocalDate.of(2026, 1, 3), "Mock"),
                    TasaCambio(BigDecimal("58.3"), BigDecimal("59.3"), LocalDate.of(2026, 1, 4), "Mock"),
                    TasaCambio(BigDecimal("58.4"), BigDecimal("59.4"), LocalDate.of(2026, 1, 5), "Mock"),
                    TasaCambio(BigDecimal("58.5"), BigDecimal("59.5"), LocalDate.of(2026, 1, 6), "Mock"),
                    TasaCambio(BigDecimal("58.6"), BigDecimal("59.6"), LocalDate.of(2026, 1, 7), "Mock"),
                    TasaCambio(BigDecimal("58.7"), BigDecimal("59.7"), LocalDate.of(2026, 1, 8), "Mock"),
                )
            )
        )
        

        val useCase = ObtenerHistoricoTasasUseCase(repository)
        val result = useCase.ejecutar()

        assert(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(8, data.historico.size)
        assertEquals(7, data.serie.size)
        assertEquals(LocalDate.of(2026, 1, 2), data.serie.first().fecha)
        assertEquals(LocalDate.of(2026, 1, 8), data.serie.last().fecha)
    }
}
