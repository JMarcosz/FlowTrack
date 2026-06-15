package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.TasaCambio
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ConvertirMonedaUseCaseTest {

    private val useCase = ConvertirMonedaUseCase()

    @Test
    fun `convierte de DOP a USD usando la tasa de venta`() {
        val tasa = TasaCambio(
            compra = BigDecimal("58.50"),
            venta = BigDecimal("59.10"),
            fecha = LocalDate.of(2026, 1, 1),
            fuente = "Mock",
        )

        val resultado = useCase.ejecutar(BigDecimal("591"), true, tasa)

        assertEquals(BigDecimal("10.00"), resultado)
    }

    @Test
    fun `convierte de USD a DOP usando la tasa de compra`() {
        val tasa = TasaCambio(
            compra = BigDecimal("58.50"),
            venta = BigDecimal("59.10"),
            fecha = LocalDate.of(2026, 1, 1),
            fuente = "Mock",
        )

        val resultado = useCase.ejecutar(BigDecimal("10"), false, tasa)

        assertEquals(BigDecimal("585.00"), resultado)
    }

    @Test
    fun `sin tasa retorna cero con dos decimales`() {
        val resultado = useCase.ejecutar(BigDecimal("100"), true, null)

        assertEquals(BigDecimal("0.00"), resultado)
    }
}
