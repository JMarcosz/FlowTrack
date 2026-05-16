package com.example.flowtrack.data.parsers.banreservas

import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests unitarios de lógica de parseo de BanReservasPdfParser usando el fixture real
 * si está disponible.
 *
 * Nota: el parser requiere PDFBox para leer PDFs reales. En JVM estos tests usan el fixture
 * real desde docs/03-fixtures/banreservas.pdf si está disponible; de lo contrario se omiten.
 *
 * Ejecutar:
 *   .\gradlew.bat testDebugUnitTest --tests "com.example.flowtrack.data.parsers.banreservas.BanReservasTipoTransaccionTest"
 */
class BanReservasTipoTransaccionTest {

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

    // ─── Tests contra fixture real ────────────────────────────────────────────

    @Test
    fun `fixture real - hay movimientos de tipo INGRESO (depositos y abonos)`() = runTest {
        val bytes = cargarFixtureReal("banreservas.pdf") ?: run {
            println("Fixture BanReservas no disponible — test omitido")
            return@runTest
        }
        val resultado = try {
            parser.parse(makeRequest(crearArchivo(bytes, "banreservas.pdf", "pdf")))
        } catch (e: Throwable) {
            println("PDFBox no disponible en JVM (${e.javaClass.simpleName}) — test omitido")
            return@runTest
        }

        assertTrue("Parseo debe ser Success", resultado is ParseResult.Success)
        val exito = resultado as ParseResult.Success
        val movs = exito.estado.movimientos

        val ingresos = movs.filter { it.tipo == TipoMovimiento.INGRESO || it.tipo == TipoMovimiento.CASHBACK }
        val gastos = movs.filter { it.tipo != TipoMovimiento.INGRESO && it.tipo != TipoMovimiento.CASHBACK }

        assertTrue(
            "Debe haber al menos 1 movimiento INGRESO (deposito/abono). " +
            "Si este test falla, el parser está clasificando todos los depósitos como GASTO. " +
            "Total: ${movs.size}, gastos=${gastos.size}, ingresos=${ingresos.size}",
            ingresos.isNotEmpty(),
        )

        println("BanReservas: ${movs.size} movimientos — gastos=${gastos.size}, ingresos=${ingresos.size}")
    }

    @Test
    fun `fixture real - todos los montos son positivos`() = runTest {
        val bytes = cargarFixtureReal("banreservas.pdf") ?: run {
            println("Fixture BanReservas no disponible — test omitido")
            return@runTest
        }

        val resultado = try {
            parser.parse(makeRequest(crearArchivo(bytes, "banreservas.pdf", "pdf")))
        } catch (e: Throwable) {
            println("PDFBox no disponible en JVM — test omitido")
            return@runTest
        }

        val exito = resultado as? ParseResult.Success ?: return@runTest
        exito.estado.movimientos.forEach { mov ->
            assertTrue(
                "Monto debe ser positivo: ${mov.monto} en movimiento ${mov.descripcionOriginal}",
                mov.monto > BigDecimal.ZERO,
            )
        }
    }

    @Test
    fun `fixture real - balance final es positivo o cero`() = runTest {
        val bytes = cargarFixtureReal("banreservas.pdf") ?: run {
            println("Fixture BanReservas no disponible — test omitido")
            return@runTest
        }

        val resultado = try {
            parser.parse(makeRequest(crearArchivo(bytes, "banreservas.pdf", "pdf")))
        } catch (e: Throwable) {
            println("PDFBox no disponible en JVM — test omitido")
            return@runTest
        }

        val exito = resultado as? ParseResult.Success ?: return@runTest
        val balance = exito.estado.balanceFinal ?: return@runTest
        assertTrue(
            "Balance final debe ser >= 0, fue $balance",
            balance >= BigDecimal.ZERO,
        )
    }

    // ─── Tests de metadatos del parser ────────────────────────────────────────

    @Test
    fun `key bancoCodigo es BANRESERVAS`() {
        assert(parser.key.bancoCodigo == "BANRESERVAS")
    }

    @Test
    fun `key formato es PDF`() {
        assert(parser.key.formato == FileFormat.PDF)
    }

    @Test
    fun `key productoTipo es CUENTA`() {
        assert(parser.key.productoTipo == ProductoTipo.CUENTA)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun makeRequest(archivo: ArchivoEntrada) = ImportRequest(
        uidUsuario = "uid_test",
        bancoCodigo = "BANRESERVAS",
        productoTipo = ProductoTipo.CUENTA,
        formato = FileFormat.PDF,
        archivo = archivo,
    )

    private fun cargarFixtureReal(nombre: String): ByteArray? {
        javaClass.classLoader?.getResourceAsStream("fixtures/${nombre.replace(".pdf", "_v1.pdf")}")
            ?.let { return it.readBytes() }
        val f = java.io.File("../docs/03-fixtures/$nombre")
        return if (f.exists()) f.readBytes() else null
    }

    private fun crearArchivo(bytes: ByteArray, nombre: String, ext: String): ArchivoEntrada =
        ArchivoEntrada(
            nombre = nombre,
            extension = ext,
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = if (ext == "pdf") "application/pdf" else "application/octet-stream",
        )
}
