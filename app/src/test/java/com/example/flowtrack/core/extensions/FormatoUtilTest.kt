package com.example.flowtrack.core.extensions

import com.example.flowtrack.domain.model.Moneda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests de las funciones de formato según preferencias del usuario (Bloque B, issue #2).
 */
class FormatoUtilTest {

    private val fecha = LocalDate.of(2026, 3, 15)

    @Test
    fun `formatearFecha respeta el patron configurado`() {
        assertEquals("15/03/2026", formatearFecha(fecha, "dd/MM/yyyy"))
        assertEquals("2026-03-15", formatearFecha(fecha, "yyyy-MM-dd"))
        assertEquals("15-03-2026", formatearFecha(fecha, "dd-MM-yyyy"))
    }

    @Test
    fun `formatearFecha con patron invalido cae al formato largo`() {
        // Patrón inválido → no debe lanzar; usa fallback en español.
        val r = formatearFecha(fecha, "qqqq-zz")
        assertTrue("Fallback debe contener el año", r.contains("2026"))
    }

    @Test
    fun `formatearMoneda prefijo con espacio (RD$ 0,00)`() {
        val r = formatearMoneda(BigDecimal("1234.56"), Moneda.DOP, "RD$ 0.00")
        assertEquals("RD$ 1,234.56", r)
    }

    @Test
    fun `formatearMoneda sufijo (0,00 RD$)`() {
        val r = formatearMoneda(BigDecimal("1234.56"), Moneda.DOP, "0.00 RD$")
        assertEquals("1,234.56 RD$", r)
    }

    @Test
    fun `formatearMoneda prefijo sin espacio ($0,00)`() {
        val r = formatearMoneda(BigDecimal("1234.56"), Moneda.DOP, "$0.00")
        assertEquals("RD$1,234.56", r)
    }

    @Test
    fun `formatearMoneda usa simbolo USD segun moneda`() {
        val r = formatearMoneda(BigDecimal("50.00"), Moneda.USD, "RD$ 0.00")
        assertTrue("Debe usar US$ para USD: $r", r.startsWith("US$"))
    }

    @Test
    fun `formatearMoneda con signo muestra mas y menos`() {
        assertTrue(formatearMoneda(BigDecimal("10.00"), Moneda.DOP, withSign = true).startsWith("+ "))
        assertTrue(formatearMoneda(BigDecimal("-10.00"), Moneda.DOP, withSign = true).startsWith("- "))
    }

    @Test
    fun `formatearMoneda negativo sin withSign muestra menos`() {
        assertTrue(formatearMoneda(BigDecimal("-10.00"), Moneda.DOP).startsWith("- "))
    }
}
