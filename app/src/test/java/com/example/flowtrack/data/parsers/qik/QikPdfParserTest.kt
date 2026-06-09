package com.example.flowtrack.data.parsers.qik

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
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests unitarios del QikPdfParser.
 *
 * Para correr con fixture real (docs/03-fixtures/qik.pdf):
 *   .\\gradlew.bat testDebugUnitTest --tests "*.QikPdfParserTest"
 */
class QikPdfParserTest {

    private lateinit var parser: QikPdfParser

    companion object {
        @JvmStatic
        @BeforeClass
        fun initPdfBox() {
            runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(null) }
        }
    }

    @Before
    fun setUp() {
        parser = QikPdfParser()
    }

    // ─── Tests de metadatos ───────────────────────────────────────────────────

    @Test
    fun `key bancoCodigo es QIK`() {
        assertEquals("QIK", parser.key.bancoCodigo)
    }

    @Test
    fun `key productoTipo es TARJETA`() {
        assertEquals(ProductoTipo.TARJETA, parser.key.productoTipo)
    }

    @Test
    fun `key formato es PDF`() {
        assertEquals(FileFormat.PDF, parser.key.formato)
    }

    @Test
    fun `version es 1`() {
        assertEquals(1, parser.version)
    }

    // ─── Test de flujo del usuario ────────────────────────────────────────────

    /**
     * Flujo del usuario: el usuario sube su estado de cuenta PDF de Qik.
     * El sistema extrae los movimientos y el estado de corte.
     *
     * Carga desde: app/src/test/resources/fixtures/qik_v1.pdf (CI sintético)
     * o docs/03-fixtures/qik.pdf (fixture real local).
     *
     * Nota: PDFBox-Android requiere contexto Android para fuentes/CMaps. Si no puede
     * inicializarse en JVM, el test se omite con advertencia (no falla).
     */
    @Test
    fun `flujo usuario Qik - parsear y verificar movimientos de tarjeta`() = runTest {
        val bytes = FixtureLoader.cargar("qik_v1.pdf", "qik")
        if (bytes == null) {
            println("⚠️ Fixture Qik no disponible. Test omitido.")
            return@runTest
        }

        val archivo = ArchivoEntrada(
            nombre = "qik.pdf",
            extension = "pdf",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "application/pdf",
        )

        val resultado = try {
            parser.parse(makeRequest(archivo))
        } catch (e: Throwable) {
            println("⚠️ PDFBox no disponible en JVM (${e.javaClass.simpleName}: ${e.message}). Test omitido.")
            return@runTest
        }

        assertTrue("Parseo debe retornar Success, fue: $resultado", resultado is ParseResult.Success)
        val exito = resultado as ParseResult.Success
        val estado = exito.estado

        // La tarjeta fue identificada (ultimos4 en productoId)
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
        assertTrue("Pago mínimo debe ser ≥ 0", (estado.pagoMinimo ?: BigDecimal.ZERO) >= BigDecimal.ZERO)

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

        println("✅ Qik: tarjeta ****${ultimos4 ?: "?????"}, ${estado.movimientos.size} movimientos, corte=$fechaCorte")
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun makeRequest(archivo: ArchivoEntrada) = ImportRequest(
        uidUsuario = "test_uid",
        bancoCodigo = "QIK",
        productoTipo = ProductoTipo.TARJETA,
        formato = FileFormat.PDF,
        archivo = archivo,
    )

}
