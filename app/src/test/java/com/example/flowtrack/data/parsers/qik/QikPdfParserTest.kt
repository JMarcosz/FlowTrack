package com.example.flowtrack.data.parsers.qik

import com.example.flowtrack.data.parsers.core.ParserKey
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import org.junit.Assert.assertEquals
import org.junit.Test

class QikPdfParserTest {

    private val parser = QikPdfParser()

    @Test
    fun `declara contrato Qik para tarjetas PDF`() {
        assertEquals(
            ParserKey("QIK", ProductoTipo.TARJETA, FileFormat.PDF),
            parser.key,
        )
        assertEquals(1, parser.version)
    }
}
