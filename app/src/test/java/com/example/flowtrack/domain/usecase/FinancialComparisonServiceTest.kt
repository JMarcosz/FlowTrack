package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinancialComparisonServiceTest {

    private lateinit var service: FinancialComparisonService
    private val zona = ZoneId.of("America/Santo_Domingo")

    @Before
    fun setUp() {
        service = FinancialComparisonService()
    }

    @Test
    fun `test 1 - periodos equivalentes completos calcula porcentaje`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin = instant("2026-04-13T23:59:59"),
        )

        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal("1000"), TipoTransaccion.DEBITO),
            tx(instant("2026-05-05"), BigDecimal("2000"), TipoTransaccion.CREDITO),
        )

        val txAnteriores = listOf(
            tx(instant("2026-04-01"), BigDecimal("500"), TipoTransaccion.DEBITO),
            tx(instant("2026-04-02"), BigDecimal("1000"), TipoTransaccion.CREDITO),
        )

        val result = service.calcular(periodoAnterior, txActuales, emptyList(), txAnteriores, emptyList())

        assertTrue("comparisonAvailable debe ser true", result.comparisonAvailable)
        assertFalse("coverageWarning debe ser false", result.coverageWarning)
        assertNull("reason debe ser null", result.reason)
        assertEquals(BigDecimal("100.0"), result.expenseChangePercentage)
        assertTrue("gasto incremento", result.expenseIsIncrement)
        assertEquals(BigDecimal("100.0"), result.incomeChangePercentage)
        assertTrue("ingreso incremento", result.incomeIsIncrement)
    }

    @Test
    fun `test 2 - datos anteriores empiezan el dia 10 de abril coverage warning`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin = instant("2026-04-13T23:59:59"),
        )

        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal("500"), TipoTransaccion.DEBITO),
        )

        val txAnteriores = listOf(
            tx(instant("2026-04-10"), BigDecimal("200"), TipoTransaccion.DEBITO),
        )

        val result = service.calcular(periodoAnterior, txActuales, emptyList(), txAnteriores, emptyList())

        assertFalse("comparisonAvailable debe ser false", result.comparisonAvailable)
        assertTrue("coverageWarning debe ser true", result.coverageWarning)
        assertEquals(
            "Faltan datos del periodo anterior para comparar correctamente",
            result.reason,
        )
        assertNull("expenseChangePercentage debe ser null", result.expenseChangePercentage)
        assertNull("incomeChangePercentage debe ser null", result.incomeChangePercentage)
    }

    @Test
    fun `test 3 - previous total cero y current mayor que cero no muestra porcentaje`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin = instant("2026-04-13T23:59:59"),
        )

        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal("800"), TipoTransaccion.DEBITO),
        )

        val txAnteriores = listOf(
            tx(instant("2026-04-01"), BigDecimal("100"), TipoTransaccion.DEBITO, esDerivada = true),
        )

        val result = service.calcular(periodoAnterior, txActuales, emptyList(), txAnteriores, emptyList())

        assertTrue("comparisonAvailable debe ser true (hay datos con cobertura)", result.comparisonAvailable)
        assertFalse("coverageWarning debe ser false", result.coverageWarning)
        assertNull("expenseChangePercentage debe ser null cuando anterior==0 y actual>0", result.expenseChangePercentage)
        assertEquals(BigDecimal.ZERO, result.incomeChangePercentage)
    }

    @Test
    fun `test 4 - ambos periodos cero retorna neutral`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin = instant("2026-04-13T23:59:59"),
        )

        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal.ZERO, TipoTransaccion.DEBITO),
        )
        val txAnteriores = listOf(
            tx(instant("2026-04-01"), BigDecimal.ZERO, TipoTransaccion.DEBITO),
        )

        val result = service.calcular(periodoAnterior, txActuales, emptyList(), txAnteriores, emptyList())

        assertTrue(result.comparisonAvailable)
        assertEquals(BigDecimal.ZERO, result.expenseChangePercentage)
        assertEquals(BigDecimal.ZERO, result.incomeChangePercentage)
    }

    @Test
    fun `test 5 - getPreviousEquivalentPeriod Este mes recorta al mismo dia del mes anterior`() {
        val ahora = LocalDate.of(2026, 5, 13)

        val actual = service.getCurrentComparisonPeriod("Este mes", ahora, zona)
        val anterior = service.getPreviousEquivalentPeriod("Este mes", ahora, zona)

        val inicioActualEsperado = instant("2026-05-01")
        val finActualEsperado = LocalDate.of(2026, 5, 13).atTime(23, 59, 59).atZone(zona).toInstant()
        assertEquals(inicioActualEsperado, actual.inicio)
        assertEquals(finActualEsperado, actual.fin)

        val inicioAnteriorEsperado = instant("2026-04-01")
        val finAnteriorEsperado = LocalDate.of(2026, 4, 13).atTime(23, 59, 59).atZone(zona).toInstant()
        assertEquals(inicioAnteriorEsperado, anterior.inicio)
        assertEquals(finAnteriorEsperado, anterior.fin)
    }

    @Test
    fun `test 6 - permite comparacion con cobertura parcial cuando no se exige cobertura completa`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin = instant("2026-04-13T23:59:59"),
        )

        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal("500"), TipoTransaccion.DEBITO),
        )

        val txAnteriores = listOf(
            tx(instant("2026-04-10"), BigDecimal("200"), TipoTransaccion.DEBITO),
        )

        val result = service.calcular(
            periodoAnterior = periodoAnterior,
            txActuales = txActuales,
            movActuales = emptyList(),
            txAnteriores = txAnteriores,
            movAnteriores = emptyList(),
            requiereCoberturaCompleta = false,
        )

        assertTrue("comparisonAvailable debe ser true aun con cobertura parcial", result.comparisonAvailable)
        assertTrue("coverageWarning debe seguir marcando cobertura parcial", result.coverageWarning)
        assertEquals(BigDecimal("150.0"), result.expenseChangePercentage)
        assertTrue(result.expenseIsIncrement)
    }

    private fun instant(isoDate: String): Instant =
        if (isoDate.contains("T")) Instant.parse("${isoDate}Z")
        else LocalDate.parse(isoDate).atStartOfDay(zona).toInstant()

    private fun tx(
        fecha: Instant,
        monto: BigDecimal,
        tipo: TipoTransaccion,
        esDerivada: Boolean = false,
    ) = Transaccion(
        id = "test-${fecha.epochSecond}-${tipo.name}",
        uidUsuario = "uid",
        cuentaId = "cuenta1",
        bancoCodigo = "BANRESERVAS",
        fecha = fecha,
        fechaPosteo = null,
        descripcionCorta = "Desc",
        descripcionOriginal = "Desc original",
        descripcionNormalizada = "Desc normalizada",
        monto = monto,
        tipo = tipo,
        moneda = Moneda.DOP,
        balanceDespues = null,
        referencia = null,
        serial = null,
        categoriaId = null,
        categoriaAutomatica = false,
        esDerivada = esDerivada,
        cargaId = "carga1",
        creadoEn = fecha,
    )
}
