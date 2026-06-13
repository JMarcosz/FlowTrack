package com.example.flowtrack.data.parsers.banreservas

import com.example.flowtrack.data.parsers.core.ParserKey
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import org.junit.Assert.assertEquals
import org.junit.Test

class BanReservasPdfParserTest {

    private val parser = BanReservasPdfParser()

    @Test
    fun `declara contrato BanReservas para cuentas PDF`() {
        assertEquals(
            ParserKey("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF),
            parser.key,
        )
        assertEquals(2, parser.version)
    }
}
