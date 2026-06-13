package com.example.flowtrack.data.parsers.qik

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Test instrumentado del [QikPdfParser].
 *
 * PDFBox-Android requiere runtime Android (fuentes/CMaps), por eso este test corre en dispositivo
 * y no en JVM. Lee un fixture SINTÉTICO commiteable (`androidTest/assets/fixtures/qik_v1.pdf`,
 * datos falsos con el formato real de Qik) para validar la extracción de texto + el parseo.
 *
 * Verifica especialmente las correcciones de exactitud:
 *  - límite con label "Límite Aprobado:" (antes daba 0)
 *  - regex multilínea `[^\n]` (balance/mínimo en la línea siguiente al label)
 *
 * Correr con dispositivo/emulador conectado:
 *   .\\gradlew.bat connectedDebugAndroidTest --tests "*.QikPdfParserInstrumentedTest"
 */
@RunWith(AndroidJUnit4::class)
class QikPdfParserInstrumentedTest {

    private lateinit var parser: QikPdfParser

    @Before
    fun setUp() {
        // El contexto de la app destino es suficiente para inicializar los recursos de PDFBox.
        PDFBoxResourceLoader.init(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        parser = QikPdfParser()
    }

    private fun cargarAsset(nombre: String): ByteArray? =
        runCatching {
            // Los assets de androidTest viven en el contexto de la instrumentación, no en el target.
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("fixtures/$nombre").use { it.readBytes() }
        }.getOrNull()

    @Test
    fun parsearQikSinteticoVerificaMetadataYMovimientos() = runBlocking {
        val bytes = cargarAsset("qik_v1.pdf")
        assertTrue("Falta el fixture sintético androidTest/assets/fixtures/qik_v1.pdf", bytes != null)
        bytes!!

        val archivo = ArchivoEntrada(
            nombre = "qik_v1.pdf",
            extension = "pdf",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "application/pdf",
        )
        val request = ImportRequest(
            uidUsuario = "test_uid",
            bancoCodigo = "QIK",
            productoTipo = ProductoTipo.TARJETA,
            formato = FileFormat.PDF,
            archivo = archivo,
        )

        val resultado = parser.parse(request)
        assertTrue("Parseo debe retornar Success, fue: $resultado", resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado

        // Metadata
        assertEquals("Últimos 4 de la tarjeta", "0739", estado.productoId)
        // Regresión límite: label real es "Límite Aprobado:" (antes el parser daba 0).
        assertTrue("Límite debe extraerse > 0 (regresión label 'Límite Aprobado')",
            (estado.limiteCredito ?: BigDecimal.ZERO) > BigDecimal.ZERO)
        assertTrue("Balance al corte debe extraerse > 0 (regresión regex [^\\n] multilínea)",
            (estado.balanceFinal ?: BigDecimal.ZERO) > BigDecimal.ZERO)
        val corte = estado.fechaCorte
        val pago = estado.fechaLimitePago
        assertTrue("Fecha de pago >= corte", corte != null && pago != null && !pago.isBefore(corte))

        // Movimientos: el fixture sintético tiene 5 filas de movimiento.
        assertEquals("Cantidad de movimientos", 5, estado.movimientos.size)
        estado.movimientos.forEach { mov ->
            assertTrue("Monto > 0: ${mov.descripcionCorta}", mov.monto > BigDecimal.ZERO)
            assertTrue("descripcionCorta <= 40", mov.descripcionCorta.length <= 40)
        }
        // Clasificación por keywords: deben aparecer CASHBACK, PAGO_TARJETA e INTERES (no todo GASTO).
        val tipos = estado.movimientos.map { it.tipo }.toSet()
        assertTrue("Debe clasificar más allá de GASTO: $tipos", tipos.size > 1)
        assertTrue("Debe detectar PAGO_TARJETA", TipoMovimiento.PAGO_TARJETA in tipos)
        assertTrue("Debe detectar INTERES", TipoMovimiento.INTERES in tipos)
    }
}
