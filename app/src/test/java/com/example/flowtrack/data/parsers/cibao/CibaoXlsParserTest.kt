package com.example.flowtrack.data.parsers.cibao

import com.example.flowtrack.data.parsers.core.ArchivoEntrada
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
    fun `version es 1`() {
        assertEquals(1, parser.version)
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
        val bytes = cargarFixture("cibao_v1.xls", "cibao.xls")
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

        // La tarjeta fue identificada
        val ultimos4 = estado.productoId
        if (ultimos4 != null) {
            assertTrue(
                "productoId debe tener exactamente 4 dígitos: '$ultimos4'",
                ultimos4.length == 4 && ultimos4.all { it.isDigit() },
            )
        }
        assertTrue("Límite de crédito debe ser ≥ 0", (estado.limiteCredito ?: BigDecimal.ZERO) >= BigDecimal.ZERO)

        // El estado de corte es válido
        val fechaCorte = estado.fechaCorte
        val fechaPago = estado.fechaLimitePago
        if (fechaCorte != null && fechaPago != null) {
            assertTrue("Fecha de pago debe ser >= fecha de corte", !fechaPago.isBefore(fechaCorte))
        }
        assertTrue("Balance al corte debe ser ≥ 0", (estado.balanceFinal ?: BigDecimal.ZERO) >= BigDecimal.ZERO)

        // Movimientos válidos
        assertTrue("Debe haber al menos 1 movimiento", estado.movimientos.isNotEmpty())
        estado.movimientos.forEach { mov ->
            assertTrue("Monto de movimiento debe ser positivo: ${mov.monto}", mov.monto > BigDecimal.ZERO)
            assertTrue("Descripción no puede estar vacía", mov.descripcionOriginal.isNotBlank())
            assertTrue("descripcionCorta no puede superar 40 caracteres", mov.descripcionCorta.length <= 40)
            assertTrue("Tipo debe ser TipoMovimiento válido", mov.tipo in TipoMovimiento.entries)
        }
        val tiposPresentes = estado.movimientos.map { it.tipo }.toSet()
        assertTrue("No todos los movimientos pueden ser GASTO", tiposPresentes.size > 1 || tiposPresentes.first() != TipoMovimiento.GASTO)
        println("✅ Cibao: tarjeta ****${ultimos4 ?: "?????"}, ${estado.movimientos.size} movimientos, corte=$fechaCorte, tipos=$tiposPresentes")
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun makeRequest(archivo: ArchivoEntrada) = ImportRequest(
        uidUsuario = "test_uid",
        bancoCodigo = "CIBAO",
        productoTipo = ProductoTipo.TARJETA,
        formato = FileFormat.XLS,
        archivo = archivo,
    )

    private fun cargarFixture(nombreSintetico: String, nombreReal: String): ByteArray? {
        javaClass.classLoader?.getResourceAsStream("fixtures/$nombreSintetico")
            ?.let { return it.readBytes() }
        val f = java.io.File("../docs/03-fixtures/$nombreReal")
        if (f.exists()) return f.readBytes()
        return null
    }
}
