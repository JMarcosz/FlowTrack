package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.OrigenTasa
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.TipoCuenta
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class ObtenerBalanceNetoUseCaseTest {

    private lateinit var cuentaRepository: CuentaRepository
    private lateinit var tarjetaRepository: TarjetaRepository
    private lateinit var balancesPorCuentaUseCase: ObtenerBalancesPorCuentaUseCase
    private lateinit var useCase: ObtenerBalanceNetoUseCase

    private val uid = "test-uid"

    @Before
    fun setUp() {
        cuentaRepository = mock()
        tarjetaRepository = mock()
        balancesPorCuentaUseCase = mock()
        useCase = ObtenerBalanceNetoUseCase(
            cuentaRepository,
            tarjetaRepository,
            balancesPorCuentaUseCase
        )
    }

    @Test
    fun `cuando hay cuentas y tarjetas con deuda, calcula el balance neto correctamente`() = runBlocking {
        // GIVEN
        val cuenta = Cuenta(
            id = "c1",
            uidUsuario = uid,
            alias = "Ahorros",
            bancoCodigo = "POPULAR",
            numeroCuenta = "123",
            numeroCuentaCompleto = null,
            tipoCuenta = TipoCuenta.AHORRO,
            moneda = Moneda.DOP,
            balanceActual = BigDecimal("1000.00"),
            balanceAlCorte = BigDecimal("1000.00"),
            fechaUltimoCorte = null,
            titular = "JEAN",
            activa = true,
            mostrarEnDashboard = true,
            creadoEn = Instant.now(),
            ultimaSincronizacion = null
        )
        val tarjeta = Tarjeta(
            id = "t1",
            uidUsuario = uid,
            bancoCodigo = "POPULAR",
            ultimos4 = "4444",
            alias = "Visa",
            tipoRed = "VISA",
            limiteCredito = BigDecimal("5000.00"),
            moneda = Moneda.DOP,
            diaCorte = 15,
            diaPago = 5,
            tasaInteresAnual = 60.0,
            tasaInteresOrigen = OrigenTasa.AUTO_EXTRAIDA,
            estado = EstadoTarjeta.ACTIVO,
            titular = "JEAN",
            activa = true,
            creadoEn = Instant.now(),
            ultimaSincronizacion = null
        )
        val snap = EstadoTarjetaSnap(
            id = "s1",
            uidUsuario = uid,
            tarjetaId = "t1",
            fechaCorte = Instant.now(),
            fechaLimitePago = Instant.now(),
            periodoInicio = Instant.now(),
            periodoFin = Instant.now(),
            balanceAlCorte = BigDecimal("300.00"),
            balanceAnterior = BigDecimal("0.00"),
            pagoMinimo = BigDecimal("50.00"),
            pagoTotal = BigDecimal("300.00"),
            montoVencido = BigDecimal("0.00"),
            balancePromedioDiario = null,
            interesFinanciamiento = null,
            cashbackGanado = null,
            moneda = Moneda.DOP,
            cargaId = "c1",
            creadoEn = Instant.now()
        )

        whenever(cuentaRepository.obtenerCuentas(uid)).thenReturn(AppResult.Success(listOf(cuenta)))
        whenever(tarjetaRepository.obtenerTarjetas(uid)).thenReturn(AppResult.Success(listOf(tarjeta)))
        whenever(tarjetaRepository.obtenerEstadosTarjeta(uid, "t1")).thenReturn(AppResult.Success(listOf(snap)))
        whenever(balancesPorCuentaUseCase.ejecutar(uid)).thenReturn(AppResult.Success(mapOf("c1" to BigDecimal("1000.00"))))

        // WHEN
        val result = useCase.ejecutar(uid)

        // THEN
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(BigDecimal("1000.00"), data.totalActivos)
        assertEquals(BigDecimal("300.00"), data.totalPasivos)
        assertEquals(BigDecimal("700.00"), data.neto)
    }

    @Test
    fun `cuando una cuenta no debe mostrarse en dashboard, se ignora en el calculo`() = runBlocking {
        // GIVEN
        val cuentaVisible = Cuenta(id = "c1", uidUsuario = uid, alias = "V", bancoCodigo = "B", numeroCuenta = "1", 
            numeroCuentaCompleto = null, titular = "J", tipoCuenta = TipoCuenta.AHORRO, moneda = Moneda.DOP, 
            balanceActual = BigDecimal("500.00"), balanceAlCorte = BigDecimal("500.00"), 
            fechaUltimoCorte = null, activa = true, mostrarEnDashboard = true,
            creadoEn = Instant.now(), ultimaSincronizacion = null)
        val cuentaOculta = cuentaVisible.copy(id = "c2", mostrarEnDashboard = false, balanceActual = BigDecimal("1000.00"))

        whenever(cuentaRepository.obtenerCuentas(uid)).thenReturn(AppResult.Success(listOf(cuentaVisible, cuentaOculta)))
        whenever(tarjetaRepository.obtenerTarjetas(uid)).thenReturn(AppResult.Success(emptyList()))
        whenever(balancesPorCuentaUseCase.ejecutar(uid)).thenReturn(AppResult.Success(mapOf("c1" to BigDecimal("500.00"), "c2" to BigDecimal("1000.00"))))

        // WHEN
        val result = useCase.ejecutar(uid)

        // THEN
        val data = (result as AppResult.Success).data
        assertEquals(BigDecimal("500.00"), data.totalActivos)
    }
}
