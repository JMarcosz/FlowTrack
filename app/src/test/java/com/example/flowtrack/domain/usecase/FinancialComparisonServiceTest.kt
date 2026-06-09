package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FinancialComparisonServiceTest {

    private lateinit var service: FinancialComparisonService
    private val zona = ZoneId.of("America/Santo_Domingo")

    @Before
    fun setUp() {
        service = FinancialComparisonService()
    }

    // ─── Test 1: períodos equivalentes completos → calcula % ────────────────

    @Test
    fun `test 1 - periodos equivalentes completos calcula porcentaje`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin    = instant("2026-04-13T23:59:59"),
        )

        // Mayo 1–13: gastos = 1000, ingresos = 2000
        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal("1000"), TipoTransaccion.DEBITO),
            tx(instant("2026-05-05"), BigDecimal("2000"), TipoTransaccion.CREDITO),
        )

        // Abril 1–13 (cobertura completa desde el inicio): gastos = 500, ingresos = 1000
        val txAnteriores = listOf(
            tx(instant("2026-04-01"), BigDecimal("500"), TipoTransaccion.DEBITO),
            tx(instant("2026-04-02"), BigDecimal("1000"), TipoTransaccion.CREDITO),
        )

        val result = service.calcular(periodoAnterior, txActuales, emptyList(), txAnteriores, emptyList())

        assertTrue("comparisonAvailable debe ser true", result.comparisonAvailable)
        assertFalse("coverageWarning debe ser false", result.coverageWarning)
        assertNull("reason debe ser null", result.reason)

        // gastos: (1000 - 500) / 500 * 100 = 100.0%
        assertEquals(BigDecimal("100.0"), result.expenseChangePercentage)
        assertTrue("gasto incrementó", result.expenseIsIncrement)

        // ingresos: (2000 - 1000) / 1000 * 100 = 100.0%
        assertEquals(BigDecimal("100.0"), result.incomeChangePercentage)
        assertTrue("ingreso incrementó", result.incomeIsIncrement)
    }

    // ─── Test 2: datos del período anterior empiezan tarde → comparisonAvailable=false ──

    @Test
    fun `test 2 - datos anteriores empiezan el dia 10 de abril coverage warning`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin    = instant("2026-04-13T23:59:59"),
        )

        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal("500"), TipoTransaccion.DEBITO),
        )

        // Datos del mes anterior comienzan recién el 10 de abril (más de 3 días de desfase)
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

    // ─── Test 3: previousTotal=0 y currentTotal>0 → sin porcentaje ──────────

    @Test
    fun `test 3 - previous total cero y current mayor que cero no muestra porcentaje`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin    = instant("2026-04-13T23:59:59"),
        )

        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal("800"), TipoTransaccion.DEBITO),
        )

        // El período anterior tiene datos (cobertura OK) pero ningún gasto ni ingreso computable
        val txAnteriores = listOf(
            // Transacción derivada: no cuenta como gasto real
            tx(instant("2026-04-01"), BigDecimal("100"), TipoTransaccion.DEBITO, esDerivada = true),
        )

        val result = service.calcular(periodoAnterior, txActuales, emptyList(), txAnteriores, emptyList())

        assertTrue("comparisonAvailable debe ser true (hay datos con cobertura)", result.comparisonAvailable)
        assertFalse("coverageWarning debe ser false", result.coverageWarning)

        // gasto anterior = 0 (la tx derivada no cuenta), gasto actual = 800 → porcentaje null (∞)
        assertNull("expenseChangePercentage debe ser null cuando anterior==0 y actual>0", result.expenseChangePercentage)
        // ingreso anterior = 0, ingreso actual = 0 → 0% (neutral)
        assertEquals(BigDecimal.ZERO, result.incomeChangePercentage)
    }

    // ─── Test 4: ambos 0 → neutral (0%) ─────────────────────────────────────

    @Test
    fun `test 4 - ambos periodos cero retorna neutral`() {
        val periodoAnterior = RangoPeriodo(
            inicio = instant("2026-04-01"),
            fin    = instant("2026-04-13T23:59:59"),
        )

        // Transacciones que existen (cobertura OK) pero montos en cero
        val txActuales = listOf(
            tx(instant("2026-05-01"), BigDecimal.ZERO, TipoTransaccion.DEBITO),
        )
        val txAnteriores = listOf(
            tx(instant("2026-04-01"), BigDecimal.ZERO, TipoTransaccion.DEBITO),
        )

        val result = service.calcular(periodoAnterior, txActuales, emptyList(), txAnteriores, emptyList())

        assertTrue(result.comparisonAvailable)
        assertEquals(
            "expense 0% cuando ambos son cero",
            BigDecimal.ZERO,
            result.expenseChangePercentage,
        )
        assertEquals(
            "income 0% cuando ambos son cero",
            BigDecimal.ZERO,
            result.incomeChangePercentage,
        )
    }

    // ─── Test 5: meses completos → comparación de calendario normal ──────────

    @Test
    fun `test 5 - getPreviousEquivalentPeriod Este mes recorta al mismo dia del mes anterior`() {
        // Hoy es 13 de mayo de 2026
        val ahora = LocalDate.of(2026, 5, 13)

        val actual   = service.getCurrentComparisonPeriod("Este mes", ahora, zona)
        val anterior = service.getPreviousEquivalentPeriod("Este mes", ahora, zona)

        // Período actual: 01/05 – 13/05
        val inicioActualEsperado = instant("2026-05-01")
        val finActualEsperado    = LocalDate.of(2026, 5, 13).atTime(23, 59, 59).atZone(zona).toInstant()
        assertEquals(inicioActualEsperado, actual.inicio)
        assertEquals(finActualEsperado, actual.fin)

        // Período anterior: 01/04 – 13/04 (no 10/04 ni fin del mes)
        val inicioAnteriorEsperado = instant("2026-04-01")
        val finAnteriorEsperado    = LocalDate.of(2026, 4, 13).atTime(23, 59, 59).atZone(zona).toInstant()
        assertEquals(inicioAnteriorEsperado, anterior.inicio)
        assertEquals(finAnteriorEsperado, anterior.fin)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun instant(isoDate: String): Instant =
        if (isoDate.contains("T")) Instant.parse("${isoDate}Z")
        else LocalDate.parse(isoDate).atStartOfDay(zona).toInstant()

    private fun tx(
        fecha: Instant,
        monto: BigDecimal,
        tipo: TipoTransaccion,
        esDerivada: Boolean = false,
    ) = Transaccion(
        id                    = "test-${fecha.epochSecond}-${tipo.name}",
        uidUsuario            = "uid",
        cuentaId              = "cuenta1",
        bancoCodigo           = "BANRESERVAS",
        fecha                 = fecha,
        fechaPosteo           = null,
        descripcionCorta      = "Desc",
        descripcionOriginal   = "Desc original",
        descripcionNormalizada = "Desc normalizada",
        monto                 = monto,
        tipo                  = tipo,
        moneda                = Moneda.DOP,
        balanceDespues        = null,
        referencia            = null,
        serial                = null,
        categoriaId           = null,
        categoriaAutomatica   = false,
        esDerivada            = esDerivada,
        cargaId               = "carga1",
        creadoEn              = fecha,
    )
}
