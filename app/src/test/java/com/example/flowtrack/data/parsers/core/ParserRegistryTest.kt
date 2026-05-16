package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ParserRegistryTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun parserFake(
        bancoCodigo: String,
        productoTipo: ProductoTipo = ProductoTipo.CUENTA,
        formato: FileFormat = FileFormat.PDF,
    ): BankStatementParser = mock<BankStatementParser> {
        on { key } doReturn ParserKey(bancoCodigo, productoTipo, formato)
        on { version } doReturn 1
    }

    // ─── get() ────────────────────────────────────────────────────────────────

    @Test
    fun `get - devuelve el parser correcto para una clave existente`() {
        val parser = parserFake("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF)
        val registry = ParserRegistry(setOf(parser))

        val result = registry.get(ParserKey("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF))

        assertNotNull(result)
        assertEquals(ParserKey("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF), result!!.key)
    }

    @Test
    fun `get - devuelve null para clave inexistente`() {
        val parser = parserFake("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF)
        val registry = ParserRegistry(setOf(parser))

        val result = registry.get(ParserKey("POPULAR", ProductoTipo.CUENTA, FileFormat.CSV))

        assertNull(result)
    }

    @Test
    fun `get - distingue por productoTipo`() {
        val parserCuenta = parserFake("BANCO", ProductoTipo.CUENTA, FileFormat.PDF)
        val parserTarjeta = parserFake("BANCO", ProductoTipo.TARJETA, FileFormat.PDF)
        val registry = ParserRegistry(setOf(parserCuenta, parserTarjeta))

        val resultCuenta = registry.get(ParserKey("BANCO", ProductoTipo.CUENTA, FileFormat.PDF))
        val resultTarjeta = registry.get(ParserKey("BANCO", ProductoTipo.TARJETA, FileFormat.PDF))

        assertNotNull(resultCuenta)
        assertNotNull(resultTarjeta)
        assertEquals(ProductoTipo.CUENTA, resultCuenta!!.key.productoTipo)
        assertEquals(ProductoTipo.TARJETA, resultTarjeta!!.key.productoTipo)
    }

    @Test
    fun `get - distingue por formato`() {
        val parserPdf = parserFake("BANCO", ProductoTipo.CUENTA, FileFormat.PDF)
        val parserCsv = parserFake("BANCO", ProductoTipo.CUENTA, FileFormat.CSV)
        val registry = ParserRegistry(setOf(parserPdf, parserCsv))

        val resultPdf = registry.get(ParserKey("BANCO", ProductoTipo.CUENTA, FileFormat.PDF))
        val resultCsv = registry.get(ParserKey("BANCO", ProductoTipo.CUENTA, FileFormat.CSV))

        assertNotNull(resultPdf)
        assertNotNull(resultCsv)
        assertEquals(FileFormat.PDF, resultPdf!!.key.formato)
        assertEquals(FileFormat.CSV, resultCsv!!.key.formato)
    }

    // ─── clavesRegistradas() ──────────────────────────────────────────────────

    @Test
    fun `clavesRegistradas - lista todas las claves`() {
        val parsers = setOf(
            parserFake("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF),
            parserFake("POPULAR",     ProductoTipo.CUENTA, FileFormat.CSV),
            parserFake("QIK",         ProductoTipo.TARJETA, FileFormat.PDF),
        )
        val registry = ParserRegistry(parsers)

        val claves = registry.clavesRegistradas()

        assertEquals(3, claves.size)
    }

    @Test
    fun `clavesRegistradas - registro vacio devuelve lista vacia`() {
        val registry = ParserRegistry(emptySet())
        assertEquals(emptyList<ParserKey>(), registry.clavesRegistradas())
    }

    // ─── 4 parsers del MVP ────────────────────────────────────────────────────

    @Test
    fun `registro con 4 parsers del MVP recupera cada uno por su clave`() {
        val parsers = setOf(
            parserFake("BANRESERVAS", ProductoTipo.CUENTA,   FileFormat.PDF),
            parserFake("POPULAR",     ProductoTipo.CUENTA,   FileFormat.CSV),
            parserFake("QIK",         ProductoTipo.TARJETA,  FileFormat.PDF),
            parserFake("CIBAO",       ProductoTipo.TARJETA,  FileFormat.XLS),
        )
        val registry = ParserRegistry(parsers)

        assertNotNull(registry.get(ParserKey("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF)))
        assertNotNull(registry.get(ParserKey("POPULAR",     ProductoTipo.CUENTA, FileFormat.CSV)))
        assertNotNull(registry.get(ParserKey("QIK",         ProductoTipo.TARJETA, FileFormat.PDF)))
        assertNotNull(registry.get(ParserKey("CIBAO",       ProductoTipo.TARJETA, FileFormat.XLS)))
        assertNull   (registry.get(ParserKey("BHD",         ProductoTipo.CUENTA, FileFormat.PDF)))
    }
}
