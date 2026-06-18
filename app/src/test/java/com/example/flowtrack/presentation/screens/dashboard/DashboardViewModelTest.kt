package com.example.flowtrack.presentation.screens.dashboard

import androidx.lifecycle.SavedStateHandle
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.usecase.DeltaMetrica
import com.example.flowtrack.domain.usecase.ObservarRevisionLocalUseCase
import com.example.flowtrack.domain.usecase.ObtenerResumenDashboardUseCase
import com.example.flowtrack.domain.usecase.ResultadoComparacion
import com.example.flowtrack.domain.usecase.ResumenDashboard
import com.example.flowtrack.domain.usecase.UnidadBucket
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.timeout
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var auth: FirebaseAuth
    private lateinit var resumenUseCase: ObtenerResumenDashboardUseCase
    private lateinit var observarRevisionLocalUseCase: ObservarRevisionLocalUseCase
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: DashboardViewModel

    private val resumenMock = ResumenDashboard(
        gastoTotal = BigDecimal.ZERO,
        ingresoTotal = BigDecimal.ZERO,
        balanceNeto = BigDecimal.ZERO,
        gastosPorCategoria = emptyList(),
        gastosPorBanco = emptyList(),
        serie = emptyList(),
        unidad = UnidadBucket.DIA,
        deltaBalance = DeltaMetrica(BigDecimal.ZERO, BigDecimal.ZERO, null, true),
        comparacion = ResultadoComparacion(
            comparisonAvailable = false,
            coverageWarning = false,
            reason = null,
            gastoActual = BigDecimal.ZERO,
            ingresoActual = BigDecimal.ZERO,
            gastoAnterior = BigDecimal.ZERO,
            ingresoAnterior = BigDecimal.ZERO,
            expenseChangePercentage = null,
            incomeChangePercentage = null,
            expenseIsIncrement = false,
            incomeIsIncrement = false
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val user: FirebaseUser = mock { on { uid } doReturn "uid-123" }
        auth = mock { on { currentUser } doReturn user }
        observarRevisionLocalUseCase = mock {
            on { invoke() } doReturn flowOf(0L, 1L)
        }
        resumenUseCase = mock {
            onBlocking { ejecutar(any(), any()) } doReturn AppResult.Success(resumenMock)
        }
        savedStateHandle = SavedStateHandle()
        viewModel = DashboardViewModel(auth, resumenUseCase, observarRevisionLocalUseCase, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `seleccionarPeriodo(Mes pasado) actualiza el periodo en el ViewModel`() = runTest {
        val collectJob = launch { viewModel.estado.collect {} }
        val collectJob2 = launch { viewModel.periodo.collect {} }
        advanceUntilIdle()

        viewModel.seleccionarPeriodo(FiltroPeriodo.MES_PASADO)
        advanceUntilIdle()
        
        assertEquals(FiltroPeriodo.MES_PASADO, viewModel.periodo.value.seleccionado)
        
        collectJob.cancel()
        collectJob2.cancel()
    }

    @Test
    fun `Seleccion de un periodo distinto actualiza la preferencia guardada`() = runTest {
        viewModel.seleccionarPeriodo(FiltroPeriodo.ESTE_ANIO)
        advanceUntilIdle()
        assertEquals(FiltroPeriodo.ESTE_ANIO.label, savedStateHandle.get<String>("dashboard_periodo"))
    }

    @Test
    fun `cambio en revision local recarga el resumen del dashboard`() = runTest {
        val collectJob = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        verify(resumenUseCase, timeout(1000).times(2)).ejecutar(any(), any())
        collectJob.cancel()
    }
}
