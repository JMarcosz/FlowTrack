package com.example.flowtrack.data.parsers.cibao

import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.FixtureLoader
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests unitarios del CibaoXlsParser.
 *
 * Para correr con fixture real (docs/03-fixtures/cibao.xls):
 *   .\\gradlew.bat testDebugUnitTest --tests "*.CibaoXlsParserTest"
 */
class CibaoXlsParserTest {

    private lateinit var parser: CibaoXlsParser

    @Before
    fun setUp() {
        parser = CibaoXlsParser()
    }

    // ─── Tests de metadatos ───────────────────────────────────────────────────

    @Test
    fun `key bancoCodigo es CIBAO`() {
        assertEquals("CIBAO", parser.key.bancoCodigo)
    }

    @Test
    fun `key productoTipo es TARJETA`() {
        assertEquals(ProductoTipo.TARJETA, parser.key.productoTipo)
    }

    @Test
    fun `key formato es XLS`() {
        assertEquals(FileFormat.XLS, parser.key.formato)
    }

    @Test
    fun `version es 2`() {
        assertEquals(2, parser.version)
    }

    // ─── Test de flujo del usuario ────────────────────────────────────────────

    /**
     * Flujo del usuario: el usuario sube su estado de cuenta XLS de Asociación Cibao.
     * El sistema extrae los movimientos y el estado de corte desde el Excel.
     *
     * Carga desde: app/src/test/resources/fixtures/cibao_v1.xls (CI sintético)
     * o docs/03-fixtures/cibao.xls (fixture real local).
     */
    @Test
    fun `flujo usuario Cibao - parsear y verificar movimientos de tarjeta`() = runTest {
        val bytes = FixtureLoader.cargar("cibao_v1.xls", "cibao")
        if (bytes == null) {
            println("⚠️ Fixture Cibao no disponible. Test omitido.")
            return@runTest
        }

        val archivo = ArchivoEntrada(
            nombre = "cibao.xls",
            extension = "xls",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "application/vnd.ms-excel",
        )

        val resultado = parser.parse(makeRequest(archivo))
        assertTrue("Parseo debe retornar Success, fue: $resultado", resultado is ParseResult.Success)
        val exito = resultado as ParseResult.Success
        val estado = exito.estado

        // La tarjeta fue identificada (ultimos4 = 4 dígitos). No se afirma el valor literal
        // para no filtrar el identificador real del usuario al repositorio.
        val ultimos4 = estado.productoId
        assertTrue(
            "productoId debe tener exactamente 4 dígitos: '$ultimos4'",
            ultimos4 != null && ultimos4.length == 4 && ultimos4.all { it.isDigit() },
        )

        // ── Regresión metadata columnar ──────────────────────────────────────
        // Antes del fix la metadata no se extraía: corte caía a HOY y límite/balance a 0.
        val fechaCorte = estado.fechaCorte
        val fechaPago = estado.fechaLimitePago
        assertTrue("Fecha de corte debe extraerse del XLS, no del reloj (era LocalDate.now())",
            fechaCorte != null && fechaCorte != java.time.LocalDate.now())
        assertTrue("Fecha de pago debe extraerse y ser >= corte",
            fechaPago != null && fechaCorte != null && !fechaPago.isBefore(fechaCorte))
        assertTrue("Límite de crédito debe ser > 0 (regresión: antes 0)",
            (estado.limiteCredito ?: BigDecimal.ZERO) > BigDecimal.ZERO)
        assertTrue("Balance al corte (pago contado) debe ser > 0 (regresión: antes 0)",
            (estado.balanceFinal ?: BigDecimal.ZERO) > BigDecimal.ZERO)

        // ── Movimientos válidos ──────────────────────────────────────────────
        assertTrue("Debe haber al menos 1 movimiento", estado.movimientos.isNotEmpty())
        estado.movimientos.forEach { mov ->
            // Invariante bimoneda: cada movimiento tiene monto en DOP o en USD (o ambos).
            val tieneMonto = mov.monto > BigDecimal.ZERO ||
                (mov.montoUsd ?: BigDecimal.ZERO) > BigDecimal.ZERO
            assertTrue("Movimiento sin monto en ninguna moneda: '${mov.descripcionCorta}'", tieneMonto)
            assertTrue("Monto DOP no puede ser negativo: ${mov.monto}", mov.monto >= BigDecimal.ZERO)
            assertTrue("Descripción no puede estar vacía", mov.descripcionOriginal.isNotBlank())
            assertTrue("descripcionCorta no puede superar 40 caracteres", mov.descripcionCorta.length <= 40)
            assertTrue("Tipo debe ser TipoMovimiento válido", mov.tipo in TipoMovimiento.entries)
        }

        // ── Regresión bimoneda ───────────────────────────────────────────────
        // Antes del fix las columnas DOP/USD se leían como débito/crédito; los movimientos
        // en USD se importaban como DOP. Ahora deben existir movimientos con montoUsd poblado.
        val conUsd = estado.movimientos.count { (it.montoUsd ?: BigDecimal.ZERO) > BigDecimal.ZERO }
        assertTrue("Debe haber al menos 1 movimiento con monto en USD (columna 'Monto en dólares')",
            conUsd > 0)

        val tiposPresentes = estado.movimientos.map { it.tipo }.toSet()
        assertTrue("No todos los movimientos pueden ser GASTO",
            tiposPresentes.size > 1 || tiposPresentes.first() != TipoMovimiento.GASTO)

        println("✅ Cibao: ${estado.movimientos.size} movimientos ($conUsd con USD), corte=$fechaCorte, tipos=$tiposPresentes")
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun makeRequest(archivo: ArchivoEntrada) = ImportRequest(
        uidUsuario = "test_uid",
        bancoCodigo = "CIBAO",
        productoTipo = ProductoTipo.TARJETA,
        formato = FileFormat.XLS,
        archivo = archivo,
    )

}
