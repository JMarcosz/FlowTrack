package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileFormatDetectorTest {

    @Test
    fun `detecta PDF por firma aunque la extension sea incorrecta`() {
        val archivo = archivo(
            nombre = "estado.csv",
            extension = "csv",
            mimeType = "text/csv",
            bytes = "%PDF-1.7 contenido".toByteArray(),
        )

        assertEquals(FileFormat.PDF, detectarFormatoArchivo(archivo))
    }

    @Test
    fun `detecta CSV textual`() {
        val archivo = archivo(
            nombre = "estado.csv",
            extension = "csv",
            mimeType = "text/csv",
            bytes = "Fecha,Monto,Descripcion\n01/01/2026,10.00,Prueba".toByteArray(),
        )

        assertEquals(FileFormat.CSV, detectarFormatoArchivo(archivo))
    }

    @Test
    fun `rechaza extension PDF sin firma PDF`() {
        val archivo = archivo(
            nombre = "estado.pdf",
            extension = "pdf",
            mimeType = "application/pdf",
            bytes = "contenido que no es un pdf".toByteArray(),
        )

        assertNull(detectarFormatoArchivo(archivo))
    }

    private fun archivo(
        nombre: String,
        extension: String,
        mimeType: String,
        bytes: ByteArray,
    ) = ArchivoEntrada(
        nombre = nombre,
        extension = extension,
        tamanioBytes = bytes.size.toLong(),
        bytes = bytes,
        mimeType = mimeType,
    )
}
