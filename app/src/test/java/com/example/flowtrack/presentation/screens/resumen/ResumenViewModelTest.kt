package com.example.flowtrack.presentation.screens.resumen

import androidx.lifecycle.SavedStateHandle
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.usecase.BalanceNeto
import com.example.flowtrack.domain.usecase.ObtenerBalanceNetoUseCase
import com.example.flowtrack.domain.usecase.ObtenerResumenUseCase
import com.example.flowtrack.domain.usecase.ResumenGeneral
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.model.FiltrosAvanzadosState
import com.example.flowtrack.presentation.model.RangoMonto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var auth: FirebaseAuth
    private lateinit var store: AppDataStore
    private lateinit var resumenUseCase: ObtenerResumenUseCase
    private lateinit var balanceNetoUseCase: ObtenerBalanceNetoUseCase
    private lateinit var transaccionRepository: TransaccionRepository
    private lateinit var viewModel: ResumenViewModel
    private lateinit var revisionFlow: MutableStateFlow<Long>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val user: FirebaseUser = mock { on { uid } doReturn "uid-123" }
        auth = mock { on { currentUser } doReturn user }
        store = mock {
            on { cuentas } doReturn MutableStateFlow(emptyList())
            on { balancesPorCuenta } doReturn MutableStateFlow(emptyMap())
        }
        revisionFlow = MutableStateFlow(0L)
        transaccionRepository = mock { on { observarRevisionLocal() } doReturn revisionFlow }
        resumenUseCase = mock {
            onBlocking { ejecutar(any(), any(), any(), any()) } doReturn AppResult.Success(
                ResumenGeneral(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList(), emptyList())
            )
        }
        balanceNetoUseCase = mock {
            onBlocking { ejecutar(any()) } doReturn AppResult.Success(BalanceNeto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList()))
        }
        viewModel = ResumenViewModel(auth, store, resumenUseCase, balanceNetoUseCase, transaccionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `seleccionarPeriodo(ESTE_MES) actualiza estado correctamente`() = runTest {
        viewModel.seleccionarPeriodo(FiltroPeriodo.ESTE_MES)
        advanceUntilIdle()
        assertEquals(FiltroPeriodo.ESTE_MES, viewModel.state.value.periodo.seleccionado)
    }

    @Test
    fun `seleccionarPeriodo(MES_PASADO) actualiza estado correctamente`() = runTest {
        viewModel.seleccionarPeriodo(FiltroPeriodo.MES_PASADO)
        advanceUntilIdle()
        assertEquals(FiltroPeriodo.MES_PASADO, viewModel.state.value.periodo.seleccionado)
    }

    @Test
    fun `seleccionarPeriodo(ULTIMOS_3_MESES) calcula rango correctamente`() = runTest {
        viewModel.seleccionarPeriodo(FiltroPeriodo.ULTIMOS_3_MESES)
        advanceUntilIdle()
        assertEquals(FiltroPeriodo.ULTIMOS_3_MESES, viewModel.state.value.periodo.seleccionado)
    }

    @Test
    fun `seleccionarPeriodo(ESTE_ANIO) calcula rango correctamente`() = runTest {
        viewModel.seleccionarPeriodo(FiltroPeriodo.ESTE_ANIO)
        advanceUntilIdle()
        assertEquals(FiltroPeriodo.ESTE_ANIO, viewModel.state.value.periodo.seleccionado)
    }

    @Test
    fun `seleccionarPeriodo con el mismo periodo actual no dispara llamadas duplicadas`() = runTest {
        viewModel.seleccionarPeriodo(FiltroPeriodo.ESTE_MES)
        advanceUntilIdle()
        val currentState = viewModel.state.value
        viewModel.seleccionarPeriodo(FiltroPeriodo.ESTE_MES)
        advanceUntilIdle()
        assertEquals(currentState, viewModel.state.value)
    }

    @Test
    fun `Aplicar filtro de banco actualiza el estado y dispara recarga`() = runTest {
        val filtros = FiltrosAvanzadosState(bancoId = "POPULAR")
        viewModel.aplicarFiltros(filtros)
        advanceUntilIdle()
        assertEquals("POPULAR", viewModel.state.value.filtros.bancoId)
        assertNotNull(viewModel.state.value.resumen)
    }

    @Test
    fun `cambio en revision local recarga el resumen`() = runTest {
        advanceUntilIdle()
        revisionFlow.value = 1L
        advanceUntilIdle()
        verify(resumenUseCase, times(2)).ejecutar(any(), any(), any(), any())
    }

    @Test
    fun `Aplicar filtros de monto actualiza estado y dispara recarga`() = runTest {
        val filtros = FiltrosAvanzadosState(rangoMonto = RangoMonto(BigDecimal("100"), BigDecimal("500")))
        viewModel.aplicarFiltros(filtros)
        advanceUntilIdle()
        assertEquals(BigDecimal("100"), viewModel.state.value.filtros.rangoMonto.minimo)
        assertNotNull(viewModel.state.value.resumen)
    }

    @Test
    fun `Aplicar filtro de categorias actualiza estado y dispara recarga`() = runTest {
        val filtros = FiltrosAvanzadosState(categorias = setOf("cat-1"))
        viewModel.aplicarFiltros(filtros)
        advanceUntilIdle()
        assertEquals(setOf("cat-1"), viewModel.state.value.filtros.categorias)
        assertNotNull(viewModel.state.value.resumen)
    }

    @Test
    fun `limpiarFiltros resetea todos los filtros avanzados`() = runTest {
        viewModel.aplicarFiltros(FiltrosAvanzadosState(bancoId = "POPULAR", soloSinCategorizar = true))
        advanceUntilIdle()
        viewModel.limpiarFiltrosAvanzados()
        advanceUntilIdle()
        val filtros = viewModel.state.value.filtros
        assertEquals(null, filtros.bancoId)
        assertEquals(false, filtros.soloSinCategorizar)
        assertEquals(RangoMonto(), filtros.rangoMonto)
    }
}
