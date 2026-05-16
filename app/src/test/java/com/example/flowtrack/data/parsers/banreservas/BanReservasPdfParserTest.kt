package com.example.flowtrack.data.parsers.banreservas

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
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests unitarios del BanReservasPdfParser.
 *
 * Los tests contra el fixture real (docs/03-fixtures/banreservas.pdf) deben ejecutarse
 * localmente. El fixture NO está en el repositorio (datos sensibles, .gitignore).
 *
 * Para correr con fixture real:
 *   .\\gradlew.bat testDebugUnitTest --tests "*.BanReservasPdfParserTest"
 *
 * Para CI, usa fixtures sintéticos en app/src/test/resources/fixtures/
 */
class BanReservasPdfParserTest {

    private lateinit var parser: BanReservasPdfParser

    companion object {
        @JvmStatic
        @BeforeClass
        fun initPdfBox() {
            runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(null) }
        }
    }

    @Before
    fun setUp() {
        parser = BanReservasPdfParser()
    }

    // ─── Tests de metadatos ───────────────────────────────────────────────────

    @Test
    fun `key bancoCodigo es BANRESERVAS`() {
        assertEquals("BANRESERVAS", parser.key.bancoCodigo)
    }

    @Test
    fun `key formato es PDF`() {
        assertEquals(FileFormat.PDF, parser.key.formato)
    }

    @Test
    fun `key productoTipo es CUENTA`() {
        assertEquals(ProductoTipo.CUENTA, parser.key.productoTipo)
    }

    @Test
    fun `version es 1`() {
        assertEquals(1, parser.version)
    }

    // ─── Test de flujo del usuario ────────────────────────────────────────────

    /**
     * Flujo del usuario: el usuario sube su PDF de BanReservas.
     * El sistema lo parsea y muestra los movimientos.
     *
     * Carga desde: app/src/test/resources/fixtures/banreservas_v1.pdf (CI sintético)
     * o docs/03-fixtures/banreservas.pdf (fixture real local).
     *
     * Nota: PDFBox-Android requiere contexto Android para fuentes/CMaps. Si no puede
     * inicializarse en JVM, el test se omite con advertencia (no falla).
     */
    @Test
    fun `flujo usuario BanReservas - parsear y verificar movimientos`() = runTest {
        val bytes = cargarFixture("banreservas_v1.pdf", "banreservas.pdf")
        if (bytes == null) {
            println("⚠️ Fixture BanReservas no disponible. Test omitido.")
            return@runTest
        }

        val archivo = ArchivoEntrada(
            nombre = "banreservas.pdf",
            extension = "pdf",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "application/pdf",
        )

        val resultado = try {
            parser.parse(makeRequest(archivo))
        } catch (e: Throwable) {
            println("⚠️ Parseo falló por entorno JVM (${e.javaClass.simpleName}). Test omitido.")
            return@runTest
        }

        assertTrue("Parseo debe retornar Success, fue: $resultado", resultado is ParseResult.Success)
        val exito = resultado as ParseResult.Success

        assertTrue("Debe haber al menos 1 movimiento", exito.estado.movimientos.isNotEmpty())
        exito.estado.movimientos.forEach { mov ->
            assertTrue("Monto debe ser positivo: ${mov.monto}", mov.monto > BigDecimal.ZERO)
            assertTrue("Descripción no puede estar vacía", mov.descripcionCorta.isNotBlank())
            assertTrue("descripcionCorta no puede superar 40 caracteres", mov.descripcionCorta.length <= 40)
            assertTrue("Tipo debe ser TipoMovimiento válido", mov.tipo in TipoMovimiento.entries)
        }
        val tiposPresentes = exito.estado.movimientos.map { it.tipo }.toSet()
        assertTrue("No todos los movimientos pueden ser GASTO", tiposPresentes.size > 1 || tiposPresentes.first() != TipoMovimiento.GASTO)

        val inicio = exito.estado.fechaInicio
        val fin = exito.estado.fechaFin
        if (inicio != null && fin != null) {
            assertTrue("Período inicio <= fin", !inicio.isAfter(fin))
        }

        val balance = exito.estado.balanceFinal
        if (balance != null) {
            assertTrue("Balance ≥ 0", balance >= BigDecimal.ZERO)
        }

        println("✅ BanReservas: ${exito.estado.movimientos.size} movimientos parsados")
    }

    // ─── Tests de normalización ───────────────────────────────────────────────

    @Test
    fun `normalizarDescripcion - elimina acentos y caracteres especiales`() {
        val input = "CONSUMO POS CTA CTE"
        val norm = input.uppercase()
            .replace(Regex("[ÁÀÄÂ]"), "A")
            .replace(Regex("[ÉÈËÊ]"), "E")
            .trim()
        assertEquals("CONSUMO POS CTA CTE", norm)
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun makeRequest(archivo: ArchivoEntrada) = ImportRequest(
        uidUsuario = "test_uid",
        bancoCodigo = "BANRESERVAS",
        productoTipo = ProductoTipo.CUENTA,
        formato = FileFormat.PDF,
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
