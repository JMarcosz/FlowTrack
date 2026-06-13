package com.example.flowtrack.data.parsers.bhd

import com.example.flowtrack.data.parsers.core.ParserKey
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BhdPdfParserTest {

    private lateinit var parser: BhdPdfParser

    @Before
    fun setUp() {
        parser = BhdPdfParser()
    }

    @Test
    fun `declara contrato BHD para cuentas PDF`() {
        assertEquals(
            ParserKey("BHD", ProductoTipo.CUENTA, FileFormat.PDF),
            parser.key,
        )
        assertEquals(1, parser.version)
    }
}
