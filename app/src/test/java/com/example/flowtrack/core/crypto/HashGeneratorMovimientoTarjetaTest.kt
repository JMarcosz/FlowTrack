package com.example.flowtrack.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Tests del hash de movimiento de tarjeta, en particular la desambiguación bimoneda (Cibao).
 *
 * El `montoUsd` se incluyó en el hash para evitar que dos movimientos con igual
 * (fecha, monto DOP, tipo, descripción) pero distinto monto en USD colisionen en el mismo ID
 * y uno se pierda silenciosamente al persistir. El segmento USD solo se añade cuando hay un
 * monto USD ≠ 0, de modo que los movimientos sin USD conservan exactamente el mismo ID que antes.
 */
class HashGeneratorMovimientoTarjetaTest {

    private val uid = "uid-test"
    private val tarjetaId = "tarjeta-001"
    private val fecha = Instant.ofEpochSecond(1_700_000_000L)

    private fun hash(monto: BigDecimal, montoUsd: BigDecimal? = null, desc: String = "amazon") =
        HashGenerator.hashMovimientoTarjeta(uid, tarjetaId, fecha, monto, "COMPRA", desc, montoUsd)

    @Test
    fun `montoUsd nulo no cambia el ID respecto a la firma sin USD`() {
        val sinParametro = HashGenerator.hashMovimientoTarjeta(
            uid, tarjetaId, fecha, BigDecimal("850.00"), "COMPRA", "amazon",
        )
        val conNull = hash(BigDecimal("850.00"), null)
        assertEquals("montoUsd null debe preservar el ID histórico (sin churn)", sinParametro, conNull)
    }

    @Test
    fun `montoUsd cero no cambia el ID`() {
        val sinUsd = hash(BigDecimal("850.00"), null)
        val conCero = hash(BigDecimal("850.00"), BigDecimal("0.00"))
        assertEquals(sinUsd, conCero)
    }

    @Test
    fun `dos movimientos iguales en DOP pero distinto USD no colisionan`() {
        val a = hash(BigDecimal("850.00"), BigDecimal("15.10"))
        val b = hash(BigDecimal("850.00"), BigDecimal("14.95"))
        assertNotEquals("Distinto monto USD debe producir IDs distintos", a, b)
    }

    @Test
    fun `dos movimientos USD-only iguales en lo demas pero distinto USD no colisionan`() {
        // Caso Cibao: monto DOP = 0 en ambos, solo difieren en la columna USD.
        val a = hash(BigDecimal("0.00"), BigDecimal("0.62"), desc = "interes mora")
        val b = hash(BigDecimal("0.00"), BigDecimal("0.25"), desc = "interes mora")
        assertNotEquals(a, b)
    }

    @Test
    fun `hash es determinístico para el mismo input`() {
        assertEquals(hash(BigDecimal("850.00"), BigDecimal("15.10")), hash(BigDecimal("850.00"), BigDecimal("15.10")))
    }
}
