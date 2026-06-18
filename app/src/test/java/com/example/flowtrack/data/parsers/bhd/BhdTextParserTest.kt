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
    fun `BHD v1 valida balance inicial y final del ultimo movimiento`() {
        val texto = """
            BHD
            ESTADO DE CUENTA
            Fecha de Corte
            30/06/2026
            Balance Inicial
            ${'$'}3.43
            Balance Final
            ${'$'}0.00
            Fecha Ref. Detalle Debitos Creditos Balance
            ** Balance al inicial: 01/06/2026 ${'$'}3.43
            03/06/2026 6452169 TRANSFERENCIA RECIBIDA ${'$'}0.00 ${'$'}1,200.00 ${'$'}1,203.43
            10/06/2026 9590966 MARTE RIVERA, JE- ${'$'}3,345.00 ${'$'}0.00 ${'$'}8.43
            Total: ${'$'}4,545.00 Total: ${'$'}4,550.00 Balance Final: ${'$'}0.00
        """.trimIndent()

        val resultado = parser.parse(texto)
        if (resultado is ParseResult.Error) {
            println("ERROR_V1: ${resultado.message}")
        }
        assertTrue("Fallo parseo v1: $resultado", resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        
        assertEquals(BigDecimal("3.43"), estado.balanceInicial)
        assertEquals(BigDecimal("8.43"), estado.balanceFinal)
        assertEquals(2, estado.movimientos.size)
    }

    @Test
    fun `BHD usa ultima fila fisica cuando hay varios movimientos en la misma fecha`() {
        val texto = """
            BHD
            ESTADO DE CUENTA
            Fecha de Corte
            30/06/2026
            Balance Inicial
            ${'$'}3.43
            Balance Final
            ${'$'}0.00
            Fecha Ref. Detalle Debitos Creditos Balance
            ** Balance al inicial: 01/06/2026 ${'$'}3.43
            10/06/2026 1111111 TRANSFERENCIA RECIBIDA ${'$'}0.00 ${'$'}4,444.13 ${'$'}4,447.56
            10/06/2026 2222222 MARTE RIVERA, JE- ${'$'}4,439.13 ${'$'}0.00 ${'$'}8.43
            Total: ${'$'}4,439.13 Total: ${'$'}4,444.13 Balance Final: ${'$'}0.00
        """.trimIndent()

        val resultado = parser.parse(texto)

        assertTrue("Fallo parseo mismo dia: $resultado", resultado is ParseResult.Success)
        val success = resultado as ParseResult.Success
        assertEquals(BigDecimal("8.43"), success.estado.balanceFinal)
        assertEquals("ULTIMA_FILA_BALANCE", success.estado.metadata["balanceFinalFuente"])
        assertEquals("8.43", success.estado.metadata["balanceCalculadoFormula"])
        assertEquals(BigDecimal("8.43"), success.estado.movimientos.last().balancePosterior)
    }

    @Test
    fun `BHD v2 valida balance inicial y final del ultimo movimiento`() {
        val texto = """
            BHD
            Número de cuenta
            XXXXXXX-001-1
            Fecha de corte
            2026-05-31
            Balance inicial
            3,002.77
            Fecha Ref. Detalles Débitos Créditos Balance
            01/05 2604010142 Pago al Instante 2,895.00 3.43
            14/05  TRANSFERENCIA RECIBIDA DE HELEN VEGA 300.00 303.43
            18/05  MARTE RIVERA, JE- 300.00 3.43
            Balance Final
            0.00
        """.trimIndent()

        val resultado = parser.parse(texto)
        if (resultado is ParseResult.Error) {
            println("ERROR_V2: ${resultado.message}")
        }
        assertTrue("Fallo parseo v2: $resultado", resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        
        assertEquals(BigDecimal("3002.77"), estado.balanceInicial)
        assertEquals(BigDecimal("3.43"), estado.balanceFinal)
        assertEquals(3, estado.movimientos.size)
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
        Moneda
        $moneda
        Balance Inicial
        $balanceInicial
        Balance Final
        $balanceFinal
    """.trimIndent()
}
