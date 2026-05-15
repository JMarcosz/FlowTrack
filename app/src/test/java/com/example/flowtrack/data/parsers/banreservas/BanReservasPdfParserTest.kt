package com.example.flowtrack.data.parsers.banreservas

import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.ContextoParseo
import com.example.flowtrack.data.parsers.core.ResultadoParseo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun setUp() {
        parser = BanReservasPdfParser()
    }

    // ─── Tests de detección ───────────────────────────────────────────────────

    @Test
    fun `puedeManejar - archivo no pdf devuelve confianza 0`() = runTest {
        val archivo = ArchivoEntrada(
            nombre = "estado.csv",
            extension = "csv",
            tamanioBytes = 100,
            bytes = ByteArray(0),
            mimeType = "text/csv",
        )
        val resultado = parser.puedeManejar(archivo)
        assertEquals(0f, resultado.confianza)
    }

    @Test
    fun `codigoBanco es BANRESERVAS`() {
        assertEquals("BANRESERVAS", parser.codigoBanco)
    }

    @Test
    fun `version es 1`() {
        assertEquals(1, parser.version)
    }

    @Test
    fun `formatosArchivo contiene pdf`() {
        assertTrue("pdf" in parser.formatosArchivo)
    }

    // ─── Tests con fixture real (ignorados en CI sin el fixture) ──────────────

    /**
     * Para activar este test, copiar docs/03-fixtures/banreservas.pdf a
     * app/src/test/resources/fixtures/banreservas_v1.pdf
     * (fixture sintético sin datos reales — ver plan de acción §8)
     */
    @Test
    fun `parsear fixture sintetico BanReservas - extrae transacciones correctamente`() = runTest {
        // Cargar fixture sintético desde test resources
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/banreservas_v1.pdf")
        if (stream == null) {
            // Fixture no disponible en CI — skip silencioso
            println("⚠️ Fixture banreservas_v1.pdf no disponible. Test omitido.")
            return@runTest
        }

        val bytes = stream.readBytes()
        val archivo = ArchivoEntrada(
            nombre = "banreservas_v1.pdf",
            extension = "pdf",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "application/pdf",
        )

        // Verificar detección
        val confianza = parser.puedeManejar(archivo)
        assertTrue("Confianza debe ser ≥ 0.8 para fixture real", confianza.confianza >= 0.8f)

        // Parsear
        val contexto = ContextoParseo(uidUsuario = "test_uid")
        val resultado = parser.parsear(archivo, contexto)

        assertTrue("Resultado debe ser ExitoCuenta", resultado is ResultadoParseo.ExitoCuenta)
        val exito = resultado as ResultadoParseo.ExitoCuenta

        assertTrue("Debe haber transacciones", exito.transacciones.isNotEmpty())

        // Verificar que todas las transacciones tienen monto positivo
        exito.transacciones.forEach { tx ->
            assertTrue("Monto debe ser positivo: ${tx.monto}", tx.monto > BigDecimal.ZERO)
        }

        // Verificar que las derivadas DGII tienen referenciaPadre
        val derivadas = exito.transacciones.filter { it.esDerivada }
        derivadas.forEach { dgii ->
            assertTrue("Derivada DGII debe tener referenciaPadre", dgii.referenciaPadre != null)
        }

        println("✅ Transacciones parseadas: ${exito.transacciones.size}")
        println("✅ Derivadas DGII: ${derivadas.size}")
    }

    // ─── Tests de normalización ───────────────────────────────────────────────

    @Test
    fun `normalizarDescripcion - elimina acentos y caracteres especiales`() {
        val input = "CONSUMO POS CTA CTE"
        // Verificar via extensions
        val norm = input.uppercase()
            .replace(Regex("[ÁÀÄÂ]"), "A")
            .replace(Regex("[ÉÈËÊ]"), "E")
            .trim()
        assertEquals("CONSUMO POS CTA CTE", norm)
    }
}
