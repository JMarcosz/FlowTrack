package com.example.flowtrack.presentation.screens.transacciones

import androidx.lifecycle.SavedStateHandle
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.ReglaCategoriaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionesPage
import com.example.flowtrack.data.local.TransaccionesCursor
import com.example.flowtrack.domain.model.CategoriaCatalogo
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TransaccionesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: TransaccionRepository
    private lateinit var movimientoRepository: MovimientoTarjetaRepository
    private lateinit var reglaRepository: ReglaCategoriaRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: TransaccionesViewModel
    private lateinit var revisionFlow: MutableStateFlow<Long>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val user: FirebaseUser = mock { on { uid } doReturn "uid-123" }
        auth = mock { on { currentUser } doReturn user }
        revisionFlow = MutableStateFlow(0L)
        repository = mock {
            on { observarRevisionLocal() } doReturn revisionFlow
        }
        movimientoRepository = mock()
        reglaRepository = mock()
        savedStateHandle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `carga inicial usa TransaccionRepository y expone sus datos en la pantalla`() = runTest {
        val tx = transaccion(
            id = "tx-1",
            cuentaId = "cuenta-1",
            bancoCodigo = "BANRESERVAS",
            fecha = Instant.parse("2026-01-15T12:00:00Z"),
            monto = BigDecimal("250.00"),
            tipo = TipoTransaccion.DEBITO,
        )

        whenever(
            repository.obtenerTransaccionesPage(
                eq("uid-123"),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
            )
        ).thenReturn(
            AppResult.Success(
                TransaccionesPage(
                    transacciones = listOf(tx),
                    lastVisible = null,
                    hasMore = false,
                )
            )
        )
        val movimiento = movimiento(
            id = "mov-1",
            tarjetaId = "tarjeta-1",
            bancoCodigo = "QIK",
            fecha = Instant.parse("2026-01-14T15:00:00Z"),
            monto = BigDecimal("1200.00"),
            tipoMovimiento = TipoMovimientoTarjeta.COMPRA,
        )
        whenever(
            movimientoRepository.obtenerMovimientos(
                eq("uid-123"),
                any(),
                anyOrNull(),
            )
        ).thenReturn(AppResult.Success(listOf(movimiento)))

        viewModel = TransaccionesViewModel(
            auth = auth,
            repository = repository,
            movimientoRepository = movimientoRepository,
            reglaRepository = reglaRepository,
            savedStateHandle = savedStateHandle,
        )

        advanceUntilIdle()

        val estado = viewModel.state.value
        assertTrue(estado.transacciones.isNotEmpty())
        assertEquals(tx.id, estado.transacciones.first().id)
        assertTrue(estado.movimientosTarjeta.isNotEmpty())
        assertEquals(movimiento.id, estado.movimientosTarjeta.first().id)
    }

    @Test
    fun `filtro de tipo tambien aplica a movimientos de tarjeta`() = runTest {
        val tx = transaccion(
            id = "tx-1",
            cuentaId = "cuenta-1",
            bancoCodigo = "BANRESERVAS",
            fecha = Instant.parse("2026-01-15T12:00:00Z"),
            monto = BigDecimal("250.00"),
            tipo = TipoTransaccion.DEBITO,
        )
        val movimiento = movimiento(
            id = "mov-1",
            tarjetaId = "tarjeta-1",
            bancoCodigo = "QIK",
            fecha = Instant.parse("2026-01-14T15:00:00Z"),
            monto = BigDecimal("1200.00"),
            tipoMovimiento = TipoMovimientoTarjeta.PAGO,
        )

        whenever(
            repository.obtenerTransaccionesPage(
                eq("uid-123"),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
            )
        ).thenReturn(
            AppResult.Success(
                TransaccionesPage(
                    transacciones = listOf(tx),
                    lastVisible = null,
                    hasMore = false,
                )
            )
        )
        whenever(
            movimientoRepository.obtenerMovimientos(
                eq("uid-123"),
                any(),
                anyOrNull(),
            )
        ).thenReturn(AppResult.Success(listOf(movimiento)))

        viewModel = TransaccionesViewModel(
            auth = auth,
            repository = repository,
            movimientoRepository = movimientoRepository,
            reglaRepository = reglaRepository,
            savedStateHandle = savedStateHandle,
        )

        advanceUntilIdle()
        viewModel.setFiltroTipo(TipoTransaccionFiltro.DEBITO)
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertTrue(estado.transacciones.isNotEmpty())
        assertTrue(estado.movimientosTarjeta.isEmpty())
    }

    @Test
    fun `la categoria Compra se normaliza y no cae en sin categorizar`() = runTest {
        val tx = transaccion(
            id = "tx-1",
            cuentaId = "cuenta-1",
            bancoCodigo = "BANRESERVAS",
            fecha = Instant.parse("2026-01-15T12:00:00Z"),
            monto = BigDecimal("250.00"),
            tipo = TipoTransaccion.DEBITO,
            categoriaId = "Compra",
        )

        whenever(
            repository.obtenerTransaccionesPage(
                eq("uid-123"),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
            )
        ).thenReturn(
            AppResult.Success(
                TransaccionesPage(
                    transacciones = listOf(tx),
                    lastVisible = null,
                    hasMore = false,
                )
            )
        )
        whenever(
            movimientoRepository.obtenerMovimientos(
                eq("uid-123"),
                any(),
                anyOrNull(),
            )
        ).thenReturn(AppResult.Success(emptyList()))

        viewModel = TransaccionesViewModel(
            auth = auth,
            repository = repository,
            movimientoRepository = movimientoRepository,
            reglaRepository = reglaRepository,
            savedStateHandle = savedStateHandle,
        )

        advanceUntilIdle()
        viewModel.aplicarFiltros(
            viewModel.state.value.filtros.copy(
                categorias = setOf(CategoriaCatalogo.COMPRAS),
            )
        )
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertTrue(estado.transacciones.any { it.id == tx.id })
        assertEquals(CategoriaCatalogo.COMPRAS, CategoriaCatalogo.normalizarId(estado.transacciones.first().categoriaId))
    }

    @Test
    fun `el estado del filtro se restaura al recrear el ViewModel`() = runTest {
        whenever(
            repository.obtenerTransaccionesPage(
                eq("uid-123"),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
            )
        ).thenReturn(
            AppResult.Success(
                TransaccionesPage(
                    transacciones = emptyList(),
                    lastVisible = null,
                    hasMore = false,
                )
            )
        )
        whenever(
            movimientoRepository.obtenerMovimientos(
                eq("uid-123"),
                any(),
                anyOrNull(),
            )
        ).thenReturn(AppResult.Success(emptyList()))

        val primeraInstancia = TransaccionesViewModel(
            auth = auth,
            repository = repository,
            movimientoRepository = movimientoRepository,
            reglaRepository = reglaRepository,
            savedStateHandle = savedStateHandle,
        )

        advanceUntilIdle()
        primeraInstancia.setFiltroTipo(TipoTransaccionFiltro.CREDITO)
        advanceUntilIdle()

        val segundaInstancia = TransaccionesViewModel(
            auth = auth,
            repository = repository,
            movimientoRepository = movimientoRepository,
            reglaRepository = reglaRepository,
            savedStateHandle = savedStateHandle,
        )

        advanceUntilIdle()

        assertEquals(TipoTransaccionFiltro.CREDITO, segundaInstancia.state.value.filtroTipo)
    }

    private fun transaccion(
        id: String,
        cuentaId: String,
        bancoCodigo: String,
        fecha: Instant,
        monto: BigDecimal,
        tipo: TipoTransaccion,
        categoriaId: String? = null,
    ): Transaccion = Transaccion(
        id = id,
        uidUsuario = "uid-123",
        cuentaId = cuentaId,
        bancoCodigo = bancoCodigo,
        fecha = fecha,
        fechaPosteo = null,
        descripcionCorta = "Compra tarjeta",
        descripcionOriginal = "Compra tarjeta",
        descripcionNormalizada = "compra tarjeta",
        monto = monto,
        tipo = tipo,
        moneda = Moneda.DOP,
        balanceDespues = null,
        referencia = null,
        serial = null,
        categoriaId = categoriaId,
        categoriaAutomatica = false,
        cargaId = "carga-1",
        notaUsuario = null,
        metadataBanco = emptyMap(),
        creadoEn = fecha,
    )

    private fun movimiento(
        id: String,
        tarjetaId: String,
        bancoCodigo: String,
        fecha: Instant,
        monto: BigDecimal,
        tipoMovimiento: TipoMovimientoTarjeta,
    ): MovimientoTarjeta = MovimientoTarjeta(
        id = id,
        uidUsuario = "uid-123",
        tarjetaId = tarjetaId,
        bancoCodigo = bancoCodigo,
        fechaTransaccion = fecha,
        fechaPosteo = null,
        descripcionOriginal = "Compra tarjeta",
        descripcionNormalizada = "compra tarjeta",
        monto = monto,
        montoUsd = null,
        tipoMovimiento = tipoMovimiento,
        moneda = Moneda.DOP,
        numeroAutorizacion = null,
        categoriaId = null,
        categoriaAutomatica = false,
        cargaId = "carga-1",
        metadataBanco = emptyMap(),
        creadoEn = fecha,
    )
}
