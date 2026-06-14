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
        assertEquals("v1", estado.metadata["versionDetectada"])
    }

    @Test
    fun `parsea BHD v2 con texto exacto de Logcat`() {
        val texto = """
            BHD
            Número de cuenta
            XXXXXXX-001-1
            Fecha de corte
            2026-05-31
            Balance Inicial
            3,002.77
            Fecha Ref. Detalles Débitos Créditos Balance
            01/05 2604010142 Pago al Instante 2,895.00 3.43
            01/05 2604010142 Pago al Instante 100.00 3.43
            01/05 2604010142 Pago al Instante 4.34 3.43
            14/05  TRANSFERENCIA RECIBIDA DE HELEN VEGA 300.00 303.43
            18/05  MARTE RIVERA, JE- 300.00 3.43
            Balance Final
            3.43
        """.trimIndent()

        val resultado = parser.parse(texto)
        assertTrue("Fallo parseo v2 exacto: $resultado", resultado is ParseResult.Success)
        val estado = (resultado as ParseResult.Success).estado
        assertEquals(5, estado.movimientos.size)
        
        // El del 14/05 debe ser ingreso
        val m14 = estado.movimientos.first { it.fechaTransaccion.dayOfMonth == 14 }
        assertEquals(TipoMovimiento.INGRESO, m14.tipo)
        assertEquals(BigDecimal("300.00"), m14.monto)
        
        // El del 18/05 debe ser gasto
        val m18 = estado.movimientos.first { it.fechaTransaccion.dayOfMonth == 18 }
        assertEquals(TipoMovimiento.GASTO, m18.tipo)
        assertEquals("v2", estado.metadata["versionDetectada"])
    }

    @Test
    fun `bloque con un solo monto devuelve error`() {
        val texto = estadoSintetico(
            movimientos = """
                04/06/2026
                REF-INCOMPLETA
                MOVIMIENTO SIN BALANCE
                ${'$'}10.00
            """.trimIndent(),
        )

        val resultado = parser.parse(texto)
        assertTrue("Se esperaba error por falta de montos, fue: $resultado", resultado is ParseResult.Error)
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
