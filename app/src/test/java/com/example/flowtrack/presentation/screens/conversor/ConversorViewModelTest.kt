package com.example.flowtrack.presentation.screens.conversor

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.usecase.ConvertirMonedaUseCase
import com.example.flowtrack.domain.usecase.ObtenerHistoricoTasasUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ConversorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: TasaCambioRepository
    private lateinit var historicoUseCase: ObtenerHistoricoTasasUseCase
    private lateinit var viewModel: ConversorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        runBlocking {
            whenever(repository.obtenerTasaDelDia()).thenReturn(
                AppResult.Success(
                    TasaCambio(
                        compra = BigDecimal("58.50"),
                        venta = BigDecimal("59.10"),
                        fecha = LocalDate.of(2026, 1, 1),
                        fuente = "Mock",
                    )
                )
            )
            whenever(repository.obtenerHistorico(30)).thenReturn(AppResult.Success(emptyList()))
        }
        historicoUseCase = ObtenerHistoricoTasasUseCase(repository)
        viewModel = ConversorViewModel(repository, ConvertirMonedaUseCase(), historicoUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calcularResultado convierte usando la tasa cargada`() = runTest {
        advanceUntilIdle()
        viewModel.setMonto("591")

        assertEquals(BigDecimal("10.00"), viewModel.calcularResultado())
    }
}
