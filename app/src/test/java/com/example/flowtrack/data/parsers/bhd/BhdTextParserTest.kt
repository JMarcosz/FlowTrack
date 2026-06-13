package com.example.flowtrack.data.parsers.bhd

import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.domain.model.Moneda
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BhdTextParserTest {

    private lateinit var parser: BhdTextParser

    @Before
    fun setUp() {
        parser = BhdTextParser()
    }

    @Test
    fun `parsea debitos creditos descripcion multilinea y balances DOP`() {
        val texto = estadoSintetico(
                movimientos = """
                    01/06/2026
                    REF-INGRESO
                    TRANSFERENCIA RECIBIDA DE
                    CLIENTE SINTETICO
                    RD${'$'}0.00
                    RD${'$'}1,000.00
                    RD${'$'}1,100.00
                    02/06/2026
                    REF-GASTO
                    COMPRA DE PRUEBA
                    RD${'$'}250.00
                    RD${'$'}0.00
                    RD${'$'}850.00
                """.trimIndent(),
                moneda = "RD${'$'}",
                balanceInicial = "${'$'}100.00",
                balanceFinal = "${'$'}850.00",
        )

        val resultado = parser.parse(texto)

        assertTrue("Se esperaba Success, fue: $resultado", resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        assertEquals("BHD", estado.bancoCodigo)
        assertEquals(Moneda.DOP, estado.moneda)
        assertEquals(BigDecimal("100.00"), estado.balanceInicial)
        assertEquals(BigDecimal("850.00"), estado.balanceFinal)
        assertEquals(2, estado.movimientos.size)
        assertEquals(TipoMovimiento.INGRESO, estado.movimientos[0].tipo)
        assertEquals(TipoMovimiento.GASTO, estado.movimientos[1].tipo)
        assertTrue(estado.movimientos[0].descripcionOriginal.contains("CLIENTE SINTETICO"))
        assertEquals("REF-INGRESO", estado.movimientos[0].referencia)
    }

    @Test
    fun `propaga moneda USD a movimientos y estado`() {
        val resultado = parser.parse(
            estadoSintetico(
                movimientos = """
                    03/06/2026
                    REF-USD
                    DEPOSITO DE PRUEBA
                    US${'$'}0.00
                    US${'$'}50.00
                    US${'$'}75.00
                """.trimIndent(),
                moneda = "US${'$'}",
                balanceInicial = "US${'$'}25.00",
                balanceFinal = "US${'$'}75.00",
            )
        )

        assertTrue("Se esperaba Success, fue: $resultado", resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        assertEquals(Moneda.USD, estado.moneda)
        assertEquals(Moneda.USD, estado.movimientos.single().moneda)
        assertEquals(BigDecimal("50.00"), estado.movimientos.single().monto)
    }

    @Test
    fun `documento sin encabezado de movimientos devuelve error`() {
        val resultado = parser.parse(
            """
                BHD
                ESTADO DE CUENTA
                Numero de Cuenta
                0000000000
                Balance Final
                ${'$'}0.00
            """.trimIndent()
        )

        assertTrue(resultado is ParseResult.Error)
    }

    @Test
    fun `bloque incompleto se ignora y reporta error si no quedan movimientos`() {
        val resultado = parser.parse(
            estadoSintetico(
                movimientos = """
                    04/06/2026
                    REF-INCOMPLETA
                    MOVIMIENTO SIN BALANCE
                    ${'$'}10.00
                    ${'$'}0.00
                """.trimIndent(),
            )
        )

        assertTrue(resultado is ParseResult.Error)
    }

    @Test
    fun `documento de otro banco devuelve error`() {
        val resultado = parser.parse(
            """
                OTRO BANCO
                ESTADO DE CUENTA
                Fecha Ref. Detalle Debitos Creditos Balance
                01/06/2026 REF-1 PRUEBA ${'$'}1.00 ${'$'}0.00 ${'$'}9.00
            """.trimIndent()
        )

        assertTrue(resultado is ParseResult.Error)
    }

    @Test
    fun `parsea texto real extraido de pdf`() {
        val file = java.io.File("../bhd_pdfbox_extracted.txt")
        if (!file.exists()) return // Skip if file is not present
        val texto = file.readText(Charsets.UTF_8)
        
        // Debug
        val p = BhdTextParser()
        val r = p.parse(texto)
        if (r is ParseResult.Error) {
            java.io.File("bhd_pdfbox_error.txt").writeText("Error: " + r.message)
            throw RuntimeException("Parser falló con: ${r.message}")
        }
        assertTrue(r is ParseResult.Success)
    }

    @Test
    fun debugRegex() {
        val file = java.io.File("../bhd_pdfbox_extracted.txt")
        if (!file.exists()) return // Skip if file is not present
        val texto = file.readText(Charsets.UTF_8)
        val lineas = texto.lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotBlank() }
        
        val out = StringBuilder()
        var start = lineas.indexOfFirst { it.contains("Fecha Ref.") }
        out.appendLine("Start at: " + start)
        if (start != -1) {
            val regexSingleLine = Regex("""^(\d{2}/\d{2}/\d{4})\s+(\S+)\s+(.+?)\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})$""")
            for (i in start + 1 until lineas.size) {
                val linea = lineas[i]
                out.appendLine("Checking: " + linea)
                val esFecha = linea.take(10).matches(Regex("""\d{2}/\d{2}/\d{4}"""))
                out.appendLine("esFecha: " + esFecha)
                if (esFecha) {
                    val match = regexSingleLine.find(linea)
                    out.appendLine("Match single line: " + (match != null))
                }
            }
        }
        java.io.File("bhd_regex_debug.txt").writeText(out.toString())
    }

    private fun estadoSintetico(
        movimientos: String,
        moneda: String = "RD${'$'}",
        balanceInicial: String = "${'$'}0.00",
        balanceFinal: String = "${'$'}0.00",
    ): String = """
        BHD
        ESTADO DE CUENTA
        Fecha Ref. Detalle Debitos Creditos Balance
        $movimientos
        Total
        Numero de Cuenta
        0000000000
        Numero de Cuenta Regional
        DO00TEST00000000000000000000
        Moneda
        $moneda
        Balance Inicial
        $balanceInicial
        Balance Final
        $balanceFinal
    """.trimIndent()

}
