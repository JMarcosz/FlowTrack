package com.example.flowtrack.data.parsers.bhd

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ProductoTipo
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BhdPdfParserInstrumentedTest {

    private lateinit var parser: BhdPdfParser
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        cacheDir = context.cacheDir
        parser = BhdPdfParser()
    }

    @Test
    fun fixtureRealParseaYValidaDocumentosCifrados() = runBlocking {
        val fixture = fixtureStaged()
        val cifrado = File(cacheDir, "bhd-test-${System.nanoTime()}-encrypted.pdf")

        try {
            val bytes = fixture.readBytes()

            val resultado = parser.parse(request(bytes))
            assertTrue("El parser BHD debe completar el fixture real.", resultado is ParseResult.Success)
            validarSuccess(resultado as ParseResult.Success)

            crearCopiaCifrada(bytes, cifrado)
            val bytesCifrados = cifrado.readBytes()
            assertEquals(ParseResult.ClaveRequerida, parser.parse(request(bytesCifrados)))
            assertEquals(
                ParseResult.ClaveIncorrecta,
                parser.parse(request(bytesCifrados, "clave-incorrecta")),
            )

            val resultadoDesbloqueado = parser.parse(request(bytesCifrados, USER_PASSWORD))
            assertTrue(
                "El parser BHD debe procesar la copia cifrada con la clave correcta.",
                resultadoDesbloqueado is ParseResult.Success,
            )
            validarSuccess(resultadoDesbloqueado as ParseResult.Success)
        } finally {
            cifrado.delete()
        }
    }

    private fun fixtureStaged(): File {
        val argumento = InstrumentationRegistry.getArguments()
            .getString(ARG_FIXTURE_PATH)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error(
                "Falta el argumento de instrumentation '$ARG_FIXTURE_PATH' " +
                    "con un fixture previamente copiado al cacheDir.",
            )

        val cacheCanonico = cacheDir.canonicalFile
        val solicitado = File(argumento)
        val fixture = if (solicitado.isAbsolute) solicitado else File(cacheCanonico, argumento)
        val fixtureCanonico = fixture.canonicalFile
        val rutaCache = cacheCanonico.path + File.separator

        require(fixtureCanonico.path.startsWith(rutaCache)) {
            "El fixture BHD debe estar dentro del cacheDir del paquete objetivo."
        }
        require(fixtureCanonico.isFile) {
            "El fixture BHD staged no existe o no es un archivo regular."
        }
        require(fixtureCanonico.canRead()) {
            "El fixture BHD staged no se puede leer."
        }
        return fixtureCanonico
    }

    private fun validarSuccess(resultado: ParseResult.Success) {
        val estado = resultado.estado
        assertEquals("BHD", estado.bancoCodigo)
        assertEquals(ProductoTipo.CUENTA, estado.productoTipo)
        assertTrue(estado.moneda == Moneda.DOP || estado.moneda == Moneda.USD)
        assertTrue("La cuenta debe estar identificada.", !estado.productoId.isNullOrBlank())
        assertTrue("La cuenta regional debe estar identificada.", !estado.numeroCuentaCompleto.isNullOrBlank())
        assertNotNull("El periodo debe tener inicio.", estado.fechaInicio)
        assertNotNull("El periodo debe tener fin.", estado.fechaFin)
        assertTrue(!estado.fechaInicio!!.isAfter(estado.fechaFin))
        assertNotNull("Debe existir balance inicial.", estado.balanceInicial)
        assertNotNull("Debe existir balance final.", estado.balanceFinal)
        assertTrue("Debe haber movimientos.", estado.movimientos.isNotEmpty())
        assertTrue(estado.movimientos.all { it.monto > BigDecimal.ZERO })
        assertEquals(estado.movimientos.size, resultado.report.totalImportado)
        assertTrue(resultado.report.errors.isEmpty())
    }

    private fun crearCopiaCifrada(bytes: ByteArray, destino: File) {
        PDDocument.load(bytes).use { document ->
            document.protect(
                StandardProtectionPolicy(
                    OWNER_PASSWORD,
                    USER_PASSWORD,
                    AccessPermission(),
                ).apply {
                    encryptionKeyLength = 128
                }
            )
            document.save(destino)
        }
    }

    private fun request(
        bytes: ByteArray,
        clave: String? = null,
    ) = ImportRequest(
        uidUsuario = "instrumented-test",
        bancoCodigo = "BHD",
        productoTipo = ProductoTipo.CUENTA,
        formato = FileFormat.PDF,
        archivo = ArchivoEntrada(
            nombre = STAGED_FIXTURE_NAME,
            extension = "pdf",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "application/pdf",
        ),
        claveDocumento = clave,
    )

    private companion object {
        const val ARG_FIXTURE_PATH = "bhdFixturePath"
        const val STAGED_FIXTURE_NAME = "estado-bhd.pdf"
        const val OWNER_PASSWORD = "flowtrack-owner-test"
        const val USER_PASSWORD = "flowtrack-user-test"
    }
}
