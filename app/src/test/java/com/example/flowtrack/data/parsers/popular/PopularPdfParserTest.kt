package com.example.flowtrack.data.parsers.popular

import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PopularPdfParserTest {

    private lateinit var parser: PopularPdfParser

    @Before
    fun setUp() {
        parser = PopularPdfParser()
    }

    @Test
    fun `reconstruye debitos creditos y balances desde texto PDF`() {
        val resultado = parser.parsearTexto(
            """
            BANCO POPULAR DOMINICANO
            8 DE JUNIO DE 2026
            NUMERO DE CUENTA 1234567890
            FECHAS DE ENTRADA TRANSAC. No. DE CHEQUE DETALLE DÉBITO RD$ CRÉDITO RD$ BALANCE RD$
            11MAY BALANCE ANTERIOR 100.00
            25MAY 25MAY COMPRA MERCADO 20.00 80.00
            26MAY 26MAY DEPOSITO EN EFECTIVO 50.00 130.00
            08JUN BALANCE AL CORTE 130.00
            """.trimIndent()
        )

        assertTrue(resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        assertEquals(2, estado.movimientos.size)
        assertEquals(BigDecimal("100.00"), estado.balanceInicial)
        assertEquals(BigDecimal("130.00"), estado.balanceFinal)
        assertEquals(TipoMovimiento.GASTO, estado.movimientos[0].tipo)
        assertEquals(BigDecimal("20.00"), estado.movimientos[0].monto)
        assertEquals(TipoMovimiento.INGRESO, estado.movimientos[1].tipo)
        assertEquals(BigDecimal("50.00"), estado.movimientos[1].monto)
    }

    @Test
    fun `importa cuando PDFBox separa las columnas del encabezado en varias lineas`() {
        val resultado = parser.parsearTexto(
            """
            BANCO POPULAR DOMINICANO
            8 DE JUNIO DE 2026
            NUMERO DE CUENTA
            1234567890
            FECHAS DE
            ENTRADA
            TRANSAC.
            No. DE
            CHEQUE
            DETALLE
            DÉBITO
            RD$
            CRÉDITO
            RD$
            BALANCE
            RD$
            11MAY BALANCE ANTERIOR 100.00
            25MAY 25MAY COMPRA MERCADO 20.00 80.00
            26MAY 26MAY DEPOSITO EN EFECTIVO 50.00 130.00
            08JUN BALANCE AL CORTE 130.00
            """.trimIndent()
        )

        assertTrue(resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        assertEquals(2, estado.movimientos.size)
        assertEquals(BigDecimal("20.00"), estado.movimientos[0].monto)
        assertEquals(BigDecimal("50.00"), estado.movimientos[1].monto)
    }

    @Test
    fun `importa por balance anterior aunque PDFBox omita el encabezado`() {
        val resultado = parser.parsearTexto(
            """
            BANCO POPULAR DOMINICANO
            8 DE JUNIO DE 2026
            NUMERO DE CUENTA 1234567890
            11MAY BALANCE ANTERIOR 100.00
            25MAY 25MAY COMPRA MERCADO 20.00 80.00
            08JUN BALANCE AL CORTE 80.00
            """.trimIndent()
        )

        assertTrue(resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        assertEquals(1, estado.movimientos.size)
        assertEquals(BigDecimal("20.00"), estado.movimientos.single().monto)
    }

}
