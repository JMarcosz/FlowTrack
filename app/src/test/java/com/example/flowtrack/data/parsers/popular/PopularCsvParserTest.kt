package com.example.flowtrack.data.parsers.popular

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
 * Tests unitarios del PopularCsvParser.
 *
 * Para correr con fixture real (docs/03-fixtures/popular.csv):
 *   .\\gradlew.bat testDebugUnitTest --tests "*.PopularCsvParserTest"
 */
class PopularCsvParserTest {

    private lateinit var parser: PopularCsvParser

    @Before
    fun setUp() {
        parser = PopularCsvParser()
    }

    // ─── Tests de metadatos ───────────────────────────────────────────────────

    @Test
    fun `key bancoCodigo es POPULAR`() {
        assertEquals("POPULAR", parser.key.bancoCodigo)
    }

    @Test
    fun `key productoTipo es CUENTA`() {
        assertEquals(ProductoTipo.CUENTA, parser.key.productoTipo)
    }

    @Test
    fun `key formato es CSV`() {
        assertEquals(FileFormat.CSV, parser.key.formato)
    }

    @Test
    fun `version es 1`() {
        assertEquals(1, parser.version)
    }

    // ─── Test de flujo del usuario ────────────────────────────────────────────

    /**
     * Flujo del usuario: el usuario sube su CSV del Banco Popular.
     * El sistema lo parsea y muestra los movimientos.
     *
     * Carga desde: app/src/test/resources/fixtures/popular_v1.csv (CI sintético)
     * o docs/03-fixtures/popular.csv (fixture real local).
     */
    @Test
    fun `flujo usuario Popular - parsear y verificar movimientos`() = runTest {
        val bytes = cargarFixture("popular_v1.csv", "popular.csv")
        if (bytes == null) {
            println("⚠️ Fixture Popular no disponible. Test omitido.")
            return@runTest
        }

        val archivo = ArchivoEntrada(
            nombre = "popular.csv",
            extension = "csv",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "text/csv",
        )

        val resultado = parser.parse(makeRequest(archivo))
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

        val ingresos = exito.estado.movimientos.count { it.tipo == TipoMovimiento.INGRESO || it.tipo == TipoMovimiento.CASHBACK }
        val gastos = exito.estado.movimientos.size - ingresos
        println("✅ Popular: ${exito.estado.movimientos.size} movimientos (gastos=$gastos, ingresos=$ingresos)")
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun makeRequest(archivo: ArchivoEntrada) = ImportRequest(
        uidUsuario = "test_uid",
        bancoCodigo = "POPULAR",
        productoTipo = ProductoTipo.CUENTA,
        formato = FileFormat.CSV,
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
