package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.CategoriaCatalogo
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.presentation.model.FiltrosAvanzadosState
import com.example.flowtrack.presentation.model.RangoMonto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ObtenerResumenUseCaseFiltrosTest {

    private lateinit var transaccionRepository: TransaccionRepository
    private lateinit var movimientoTarjetaRepository: MovimientoTarjetaRepository
    private lateinit var cuentaRepository: CuentaRepository
    private lateinit var tasaCambioRepository: TasaCambioRepository
    private lateinit var flujoUnificadoUseCase: ObtenerFlujoUnificadoUseCase
    private lateinit var useCase: ObtenerResumenUseCase

    private val baseTx = Transaccion(
        id = "1", uidUsuario = "uid", cuentaId = "cta-1", bancoCodigo = "POPULAR",
        tipo = TipoTransaccion.DEBITO, monto = BigDecimal("100"), descripcionOriginal = "Compra",
        descripcionCorta = "Compra", descripcionNormalizada = "compra",
        fecha = Instant.now(), categoriaId = "cat-1",
        esDerivada = false, cargaId = "carga-1",
        fechaPosteo = null, moneda = Moneda.DOP, balanceDespues = null, referencia = null, serial = null,
        categoriaAutomatica = false, creadoEn = Instant.now()
    )

    private val baseMov = MovimientoTarjeta(
        id = "1", uidUsuario = "uid", tarjetaId = "tar-1", bancoCodigo = "POPULAR",
        tipoMovimiento = TipoMovimientoTarjeta.COMPRA, monto = BigDecimal("100"),
        descripcionOriginal = "Compra", descripcionNormalizada = "compra",
        fechaTransaccion = Instant.now(), categoriaId = "cat-1", cargaId = "carga-1",
        fechaPosteo = null, moneda = Moneda.DOP, numeroAutorizacion = null,
        categoriaAutomatica = false, creadoEn = Instant.now()
    )

    private val baseCuenta = Cuenta(
        id = "cta-1", uidUsuario = "uid", alias = "Cuenta POPULAR", bancoCodigo = "POPULAR",
        tipoCuenta = TipoCuenta.CORRIENTE, activa = true, mostrarEnDashboard = true,
        numeroCuenta = "1234", numeroCuentaCompleto = null, moneda = Moneda.DOP,
        balanceActual = null, balanceAlCorte = null, titular = "Juan",
        ultimaSincronizacion = null, creadoEn = Instant.now()
    )

    @Before
    fun setUp() {
        val transacciones = listOf(
            baseTx.copy(id = "1", bancoCodigo = "POPULAR", monto = BigDecimal("100"), categoriaId = "cat-1"),
            baseTx.copy(id = "2", bancoCodigo = "BHD", monto = BigDecimal("500"), categoriaId = "cat-2"),
            baseTx.copy(id = "3", bancoCodigo = "POPULAR", monto = BigDecimal("1000"), categoriaId = null)
        )
        val movimientos = listOf(
            baseMov.copy(id = "1", bancoCodigo = "POPULAR", monto = BigDecimal("200"), categoriaId = "cat-1")
        )
        val cuentas = listOf(
            baseCuenta.copy(id = "cta-1", bancoCodigo = "POPULAR"),
            baseCuenta.copy(id = "cta-2", bancoCodigo = "BHD")
        )

        transaccionRepository = mock {
            onBlocking { obtenerTransacciones(any(), org.mockito.kotlin.anyOrNull(), any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull()) } doReturn AppResult.Success(transacciones)
        }
        movimientoTarjetaRepository = mock {
            onBlocking { obtenerMovimientos(any(), any(), any()) } doReturn AppResult.Success(movimientos)
        }
        cuentaRepository = mock {
            onBlocking { obtenerCuentas(any()) } doReturn AppResult.Success(cuentas)
        }
        tasaCambioRepository = mock {
            onBlocking { obtenerTasaDelDia() } doReturn AppResult.Success(
                TasaCambio(
                    compra = BigDecimal("58.50"),
                    venta = BigDecimal("59.10"),
                    fecha = java.time.LocalDate.of(2026, 1, 1),
                    fuente = "Mock",
                )
            )
        }

        flujoUnificadoUseCase = ObtenerFlujoUnificadoUseCase(transaccionRepository, movimientoTarjetaRepository)
        useCase = ObtenerResumenUseCase(flujoUnificadoUseCase, transaccionRepository, cuentaRepository, tasaCambioRepository)
    }

    @Test
    fun `UseCase excluye correctamente transacciones que no coinciden con el filtro de banco`() = runTest {
        val filtros = FiltrosAvanzadosState(bancoId = "BHD")
        val result = useCase.ejecutar("uid", Instant.now(), Instant.now(), filtros)
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(BigDecimal("700"), data.gastosTotales)
        assertTrue(data.gastosPorBanco.any { it.bancoCodigo == "POPULAR" })
    }

    @Test
    fun `UseCase excluye transacciones con montos fuera del rango`() = runTest {
        val filtros = FiltrosAvanzadosState(rangoMonto = RangoMonto(BigDecimal("400"), BigDecimal("600")))
        val result = useCase.ejecutar("uid", Instant.now(), Instant.now(), filtros)
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(BigDecimal("500"), data.gastosTotales)
    }

    @Test
    fun `UseCase filtra transacciones por categorias indicadas`() = runTest {
        val filtros = FiltrosAvanzadosState(categorias = setOf("cat-1"))
        val result = useCase.ejecutar("uid", Instant.now(), Instant.now(), filtros)
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        // tx1(100) + mov1(200)
        assertEquals(BigDecimal("300"), data.gastosTotales)
    }
    
    @Test
    fun `UseCase filtra solo transacciones sin categorizar`() = runTest {
        val filtros = FiltrosAvanzadosState(soloSinCategorizar = true)
        val result = useCase.ejecutar("uid", Instant.now(), Instant.now(), filtros)
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        // tx3(1000)
        assertEquals(BigDecimal("1000"), data.gastosTotales)
    }

    @Test
    fun `UseCase normaliza la categoria Compra como compras`() = runTest {
        val transacciones = listOf(baseTx.copy(id = "4", categoriaId = "Compra", monto = BigDecimal("150")))
        val movimientos = listOf(baseMov.copy(id = "2", categoriaId = "Compra", monto = BigDecimal("50")))

        transaccionRepository = mock {
            onBlocking { obtenerTransacciones(any(), org.mockito.kotlin.anyOrNull(), any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull()) } doReturn AppResult.Success(transacciones)
        }
        movimientoTarjetaRepository = mock {
            onBlocking { obtenerMovimientos(any(), any(), any()) } doReturn AppResult.Success(movimientos)
        }
        flujoUnificadoUseCase = ObtenerFlujoUnificadoUseCase(transaccionRepository, movimientoTarjetaRepository)
        useCase = ObtenerResumenUseCase(flujoUnificadoUseCase, transaccionRepository, cuentaRepository, tasaCambioRepository)

        val result = useCase.ejecutar("uid", Instant.now(), Instant.now(), FiltrosAvanzadosState())
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data

        assertTrue(data.gastosPorCategoria.any { it.categoriaId == CategoriaCatalogo.COMPRAS })
    }

}
