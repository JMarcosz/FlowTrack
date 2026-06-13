package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class BankParserFactoryTest {

    private fun parserFake(
        bancoCodigo: String,
        productoTipo: ProductoTipo = ProductoTipo.CUENTA,
        formato: FileFormat = FileFormat.PDF,
    ): BankStatementParser = mock<BankStatementParser> {
        on { key } doReturn ParserKey(bancoCodigo, productoTipo, formato)
        on { version } doReturn 1
    }

    @Test
    fun `obtenerParser devuelve parser para BHD cuando esta registrado`() {
        val parser = parserFake("BHD", ProductoTipo.CUENTA, FileFormat.PDF)
        val factory = BankParserFactory(ParserRegistry(setOf(parser)))

        val result = factory.obtenerParser("BHD", ProductoTipo.CUENTA, FileFormat.PDF)

        assertTrue(result.isSuccess)
        assertEquals(ParserKey("BHD", ProductoTipo.CUENTA, FileFormat.PDF), result.getOrNull()!!.key)
    }

    @Test
    fun `obtenerParser devuelve error generico cuando no existe parser`() {
        val factory = BankParserFactory(ParserRegistry(emptySet()))

        val result = factory.obtenerParser("BHD", ProductoTipo.CUENTA, FileFormat.PDF)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as ParserNoDisponibleException).error
        assertEquals("BHD", error.bancoCodigo)
        assertTrue(error is ErrorApp.ParserNoDisponible)
        assertEquals(false, error.proximamente)
        assertEquals("No hay parser disponible para BHD.", result.exceptionOrNull()!!.message)
    }
}
