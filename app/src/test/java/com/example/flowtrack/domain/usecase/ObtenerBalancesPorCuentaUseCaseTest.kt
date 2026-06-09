package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class ObtenerBalancesPorCuentaUseCaseTest {

    private lateinit var repository: TransaccionRepository
    private lateinit var useCase: ObtenerBalancesPorCuentaUseCase

    @Before
    fun setUp() {
        repository = mock()
        useCase = ObtenerBalancesPorCuentaUseCase(repository)
    }

    // ─── Test 1: happy path — 3 cuentas, una sin transacciones con balanceDespues ───

    @Test
    fun `happy path - retorna balance de transaccion mas reciente por cuenta`() = runTest {
        val ahora = Instant.now()
        val txs = listOf(
            // Cuenta A — dos transacciones; debe tomar la más reciente
            transaccion("A", ahora.minus(5, ChronoUnit.DAYS), BigDecimal("100.00"), false),
            transaccion("A", ahora.minus(1, ChronoUnit.DAYS), BigDecimal("80.00"), false),
            // Cuenta B — una transacción
            transaccion("B", ahora.minus(3, ChronoUnit.DAYS), BigDecimal("200.00"), false),
            // Cuenta C — sin balanceDespues → no debe aparecer en el mapa
            transaccion("C", ahora.minus(2, ChronoUnit.DAYS), null, false),
        )
        whenever(repository.obtenerTransacciones(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(AppResult.Success(txs))

        val result = useCase.ejecutar("uid")

        assertTrue(result is AppResult.Success)
        val mapa = (result as AppResult.Success).data
        assertEquals(BigDecimal("80.00"), mapa["A"])
        assertEquals(BigDecimal("200.00"), mapa["B"])
        assertNull(mapa["C"])
    }

    // ─── Test 2: transacciones derivadas NO se usan como balance ─────────────────

    @Test
    fun `transaccion derivada se omite - usa la anterior no derivada`() = runTest {
        val ahora = Instant.now()
        val txs = listOf(
            // La más reciente es derivada (esDerivada=true) → no debe usarse
            transaccion("A", ahora.minus(1, ChronoUnit.DAYS), BigDecimal("999.00"), esDerivada = true),
            // La anterior no derivada → esta sí debe usarse
            transaccion("A", ahora.minus(5, ChronoUnit.DAYS), BigDecimal("50.00"), esDerivada = false),
        )
        whenever(repository.obtenerTransacciones(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(AppResult.Success(txs))

        val result = useCase.ejecutar("uid")

        val mapa = (result as AppResult.Success).data
        assertEquals(BigDecimal("50.00"), mapa["A"])
    }

    // ─── Test 3: usuario sin transacciones → mapa vacío, no crash ────────────────

    @Test
    fun `usuario sin transacciones retorna mapa vacio`() = runTest {
        whenever(repository.obtenerTransacciones(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(AppResult.Success(emptyList()))

        val result = useCase.ejecutar("uid")

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isEmpty())
    }

    // ─── Test 4: error del repositorio → propaga AppResult.Error ─────────────────

    @Test
    fun `error del repositorio se propaga como AppResult Error`() = runTest {
        whenever(repository.obtenerTransacciones(any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(AppResult.Error(ErrorApp.FirestoreError("fallo", null)))

        val result = useCase.ejecutar("uid")

        assertTrue(result is AppResult.Error)
    }

    // ─── Test 5: balanceEfectivo usa fallback a Cuenta.balanceActual ─────────────

    @Test
    fun `balanceEfectivo cae a Cuenta balanceActual cuando la cuenta no esta en el mapa`() {
        val cuenta = cuentaConBalance("X", BigDecimal("777.00"))
        val mapaVacio = emptyMap<String, BigDecimal>()

        assertEquals(BigDecimal("777.00"), cuenta.balanceEfectivo(mapaVacio))
    }

    @Test
    fun `balanceEfectivo prefiere el mapa sobre Cuenta balanceActual`() {
        val cuenta = cuentaConBalance("X", BigDecimal("100.00"))
        val mapa = mapOf("X" to BigDecimal("999.00"))

        assertEquals(BigDecimal("999.00"), cuenta.balanceEfectivo(mapa))
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private fun transaccion(
        cuentaId: String,
        fecha: Instant,
        balanceDespues: BigDecimal?,
        esDerivada: Boolean,
    ) = Transaccion(
        id = "${cuentaId}_${fecha.epochSecond}",
        uidUsuario = "uid",
        cuentaId = cuentaId,
        bancoCodigo = "TEST",
        fecha = fecha,
        fechaPosteo = null,
        descripcionCorta = "Test",
        descripcionOriginal = "Test",
        descripcionNormalizada = "TEST",
        monto = BigDecimal("10.00"),
        tipo = TipoTransaccion.DEBITO,
        moneda = Moneda.DOP,
        balanceDespues = balanceDespues,
        referencia = null,
        serial = null,
        categoriaId = null,
        categoriaAutomatica = false,
        esDerivada = esDerivada,
        transaccionPadreId = null,
        derivadasIds = emptyList(),
        cargaId = "carga1",
        notaUsuario = null,
        metadataBanco = emptyMap(),
        creadoEn = fecha,
    )

    private fun cuentaConBalance(id: String, balance: BigDecimal?) = Cuenta(
        id = id,
        uidUsuario = "uid",
        bancoCodigo = "TEST",
        numeroCuenta = "0000",
        numeroCuentaCompleto = null,
        alias = "Test",
        tipoCuenta = TipoCuenta.CORRIENTE,
        moneda = Moneda.DOP,
        balanceActual = balance,
        balanceAlCorte = balance,
        fechaUltimoCorte = null,
        titular = "Test",
        activa = true,
        mostrarEnDashboard = true,
        ultimaSincronizacion = null,
        creadoEn = Instant.now(),
    )
}
