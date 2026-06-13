package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
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
    private lateinit var comparisonService: FinancialComparisonService
    private lateinit var useCase: ObtenerResumenDashboardUseCase

    private val uid = "test-uid"
    private val zona = ZoneId.of("America/Santo_Domingo")

    @Before
    fun setUp() {
        transaccionRepository = mock()
        movimientoTarjetaRepository = mock()
        comparisonService = FinancialComparisonService() // Real instance as it is a pure logic service
        useCase = ObtenerResumenDashboardUseCase(
            transaccionRepository,
            movimientoTarjetaRepository,
            comparisonService
        )
    }

    @Test
    fun `cuando hay transacciones, calcula totales y serie correctamente`() = runBlocking {
        // GIVEN
        val ahora = LocalDate.now(zona)
        val rango = comparisonService.getCurrentComparisonPeriod("Este mes", ahora, zona)
        
        val txs = listOf(
            crearTx("id1", BigDecimal("100.00"), TipoTransaccion.DEBITO, rango.inicio.plusSeconds(100)),
            crearTx("id2", BigDecimal("500.00"), TipoTransaccion.CREDITO, rango.inicio.plusSeconds(200))
        )

        whenever(transaccionRepository.obtenerTransacciones(eq(uid), any(), any(), eq(0)))
            .thenReturn(AppResult.Success(txs))
        whenever(movimientoTarjetaRepository.obtenerMovimientos(eq(uid), any(), any()))
            .thenReturn(AppResult.Success(emptyList()))

        // WHEN
        val result = useCase.ejecutar(uid, "Este mes")

        // THEN
        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(BigDecimal("100.00"), data.gastoTotal)
        assertEquals(BigDecimal("500.00"), data.ingresoTotal)
        assertEquals(BigDecimal("400.00"), data.balanceNeto)
    }

    private fun crearTx(id: String, monto: BigDecimal, tipo: TipoTransaccion, fecha: Instant) = Transaccion(
        id = id,
        uidUsuario = uid,
        cuentaId = "c1",
        bancoCodigo = "POPULAR",
        fecha = fecha,
        fechaPosteo = null,
        descripcionOriginal = "DESC",
        descripcionNormalizada = "DESC",
        descripcionCorta = "DESC",
        monto = monto,
        tipo = tipo,
        moneda = Moneda.DOP,
        balanceDespues = null,
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
