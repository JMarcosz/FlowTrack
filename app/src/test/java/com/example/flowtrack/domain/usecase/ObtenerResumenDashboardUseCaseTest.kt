package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.CategoriaCatalogo
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TasaCambio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ObtenerResumenDashboardUseCaseTest {

    private lateinit var transaccionRepository: TransaccionRepository
    private lateinit var movimientoTarjetaRepository: MovimientoTarjetaRepository
    private lateinit var cuentaRepository: CuentaRepository
    private lateinit var tasaCambioRepository: TasaCambioRepository
    private lateinit var comparisonService: FinancialComparisonService
    private lateinit var flujoUnificadoUseCase: ObtenerFlujoUnificadoUseCase
    private lateinit var useCase: ObtenerResumenDashboardUseCase

    private val uid = "test-uid"
    private val zona = ZoneId.of("America/Santo_Domingo")

    @Before
    fun setUp() {
        transaccionRepository = mock()
        movimientoTarjetaRepository = mock()
        cuentaRepository = mock()
        tasaCambioRepository = mock()
        comparisonService = FinancialComparisonService() // Real instance as it is a pure logic service
        runBlocking {
            whenever(tasaCambioRepository.obtenerTasaDelDia()).thenReturn(
                AppResult.Success(
                    TasaCambio(
                        compra = java.math.BigDecimal("58.50"),
                        venta = java.math.BigDecimal("59.10"),
                        fecha = java.time.LocalDate.now(zona),
                        fuente = "Mock",
                    )
                )
            )
        }
        flujoUnificadoUseCase = ObtenerFlujoUnificadoUseCase(transaccionRepository, movimientoTarjetaRepository)
        useCase = ObtenerResumenDashboardUseCase(
            flujoUnificadoUseCase,
            transaccionRepository,
            cuentaRepository,
            tasaCambioRepository,
            comparisonService
        )
    }

    @Test
    fun `cuando hay transacciones, calcula totales y serie correctamente`() = runBlocking {
        // GIVEN
        val ahora = LocalDate.now(zona)
        val rango = comparisonService.getCurrentComparisonPeriod("Este mes", ahora, zona)
        
        val txs = listOf(
            crearTx("id1", BigDecimal("100.00"), TipoTransaccion.DEBITO, rango.inicio.plusSeconds(100), BigDecimal("900.00")),
            crearTx("id2", BigDecimal("500.00"), TipoTransaccion.CREDITO, rango.inicio.plusSeconds(200), BigDecimal("1400.00"))
        )

        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(0), anyOrNull()))
            .thenReturn(AppResult.Success(txs))
        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(1), anyOrNull()))
            .thenReturn(AppResult.Success(emptyList()))
        whenever(movimientoTarjetaRepository.obtenerMovimientos(eq(uid), any(), any()))
            .thenReturn(AppResult.Success(emptyList()))
        
        val cuentas = listOf(
            Cuenta(
                id = "c1", uidUsuario = uid, bancoCodigo = "POPULAR", numeroCuenta = "123",
                numeroCuentaCompleto = null, alias = "A1", tipoCuenta = TipoCuenta.CORRIENTE,
                moneda = Moneda.DOP, balanceActual = BigDecimal("1400.00"), balanceAlCorte = null,
                titular = "T", activa = true, mostrarEnDashboard = true,
                ultimaSincronizacion = null, creadoEn = Instant.now()
            )
        )
        whenever(cuentaRepository.obtenerCuentas(eq(uid))).thenReturn(AppResult.Success(cuentas))

        // WHEN
        val result = useCase.ejecutar(uid, "Este mes")

        // THEN
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(BigDecimal("100.00"), data.gastoTotal)
        assertEquals(BigDecimal("500.00"), data.ingresoTotal)
        assertEquals(BigDecimal("1400.00"), data.balanceNeto)
    }

    @Test
    fun `cuando hay montos en USD los normaliza a DOP en la agregacion`() = runBlocking {
        val ahora = LocalDate.now(zona)
        val rango = comparisonService.getCurrentComparisonPeriod("Este mes", ahora, zona)

        val txs = listOf(
            crearTx(
                "usd1",
                BigDecimal("10.00"),
                TipoTransaccion.DEBITO,
                rango.inicio.plusSeconds(100),
                BigDecimal("12.00"),
                Moneda.USD,
            )
        )

        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(0), anyOrNull()))
            .thenReturn(AppResult.Success(txs))
        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(1), anyOrNull()))
            .thenReturn(AppResult.Success(emptyList()))
        whenever(movimientoTarjetaRepository.obtenerMovimientos(eq(uid), any(), any()))
            .thenReturn(AppResult.Success(emptyList()))

        val cuentas = listOf(
            Cuenta(
                id = "c1", uidUsuario = uid, bancoCodigo = "POPULAR", numeroCuenta = "123",
                numeroCuentaCompleto = null, alias = "A1", tipoCuenta = TipoCuenta.CORRIENTE,
                moneda = Moneda.DOP, balanceActual = BigDecimal("1400.00"), balanceAlCorte = null,
                titular = "T", activa = true, mostrarEnDashboard = true,
                ultimaSincronizacion = null, creadoEn = Instant.now()
            )
        )
        whenever(cuentaRepository.obtenerCuentas(eq(uid))).thenReturn(AppResult.Success(cuentas))

        val result = useCase.ejecutar(uid, "Este mes")

        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(0, data.gastoTotal.compareTo(BigDecimal("585.00")))
        assertEquals(0, data.balanceNeto.compareTo(BigDecimal("702.00")))
    }

    @Test
    fun `normaliza la categoria Compra en la grafica`() = runBlocking {
        val ahora = LocalDate.now(zona)
        val rango = comparisonService.getCurrentComparisonPeriod("Este mes", ahora, zona)

        val txs = listOf(
            crearTx("id1", BigDecimal("100.00"), TipoTransaccion.DEBITO, rango.inicio.plusSeconds(100), BigDecimal("900.00")).copy(categoriaId = "Compra"),
        )

        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(0), anyOrNull()))
            .thenReturn(AppResult.Success(txs))
        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(1), anyOrNull()))
            .thenReturn(AppResult.Success(emptyList()))
        whenever(movimientoTarjetaRepository.obtenerMovimientos(eq(uid), any(), any()))
            .thenReturn(AppResult.Success(emptyList()))

        val cuentas = listOf(
            Cuenta(
                id = "c1", uidUsuario = uid, bancoCodigo = "POPULAR", numeroCuenta = "123",
                numeroCuentaCompleto = null, alias = "A1", tipoCuenta = TipoCuenta.CORRIENTE,
                moneda = Moneda.DOP, balanceActual = BigDecimal("1400.00"), balanceAlCorte = null,
                titular = "T", activa = true, mostrarEnDashboard = true,
                ultimaSincronizacion = null, creadoEn = Instant.now()
            )
        )
        whenever(cuentaRepository.obtenerCuentas(eq(uid))).thenReturn(AppResult.Success(cuentas))

        val result = useCase.ejecutar(uid, "Este mes")

        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertTrue(data.gastosPorCategoria.any { it.categoriaId == CategoriaCatalogo.COMPRAS })
    }

    private fun crearTx(id: String, monto: BigDecimal, tipo: TipoTransaccion, fecha: Instant, balance: BigDecimal? = null) = Transaccion(
        id = id,
        uidUsuario = uid,
        cuentaId = "c1",
        bancoCodigo = "POPULAR",
        fecha = fecha,
        fechaPosteo = null,
        descripcionCorta = "DESC",
        descripcionOriginal = "DESC",
        descripcionNormalizada = "DESC",
        monto = monto,
        tipo = tipo,
        moneda = Moneda.DOP,
        balanceDespues = balance,
        referencia = null,
        serial = null,
        categoriaId = "comida",
        categoriaAutomatica = false,
        esDerivada = false,
        transaccionPadreId = null,
        derivadasIds = emptyList(),
        cargaId = "carga1",
        notaUsuario = null,
        metadataBanco = emptyMap(),
        creadoEn = Instant.now()
    )

    private fun crearTx(
        id: String,
        monto: BigDecimal,
        tipo: TipoTransaccion,
        fecha: Instant,
        balance: BigDecimal? = null,
        moneda: Moneda,
    ) = Transaccion(
        id = id,
        uidUsuario = uid,
        cuentaId = "c1",
        bancoCodigo = "POPULAR",
        fecha = fecha,
        fechaPosteo = null,
        descripcionCorta = "DESC",
        descripcionOriginal = "DESC",
        descripcionNormalizada = "DESC",
        monto = monto,
        tipo = tipo,
        moneda = moneda,
        balanceDespues = balance,
        referencia = null,
        serial = null,
        categoriaId = "comida",
        categoriaAutomatica = false,
        esDerivada = false,
        transaccionPadreId = null,
        derivadasIds = emptyList(),
        cargaId = "carga1",
        notaUsuario = null,
        metadataBanco = emptyMap(),
        creadoEn = Instant.now()
    )
}
