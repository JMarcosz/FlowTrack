package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.domain.model.FileFormat

/** Detecta el formato por contenido; extensión y MIME solo validan archivos de texto. */
fun detectarFormatoArchivo(archivo: ArchivoEntrada): FileFormat? {
    val bytes = archivo.bytes
    if (bytes.size >= PDF_SIGNATURE.size &&
        bytes.copyOfRange(0, PDF_SIGNATURE.size).contentEquals(PDF_SIGNATURE)
    ) {
        return FileFormat.PDF
    }
    if (bytes.size >= OLE_SIGNATURE.size &&
        bytes.copyOfRange(0, OLE_SIGNATURE.size).contentEquals(OLE_SIGNATURE)
    ) {
        return FileFormat.XLS
    }
    if (bytes.size >= ZIP_SIGNATURE.size &&
        bytes.copyOfRange(0, ZIP_SIGNATURE.size).contentEquals(ZIP_SIGNATURE)
    ) {
        return FileFormat.XLSX
    }

    val extensionCsv = archivo.extension.equals("csv", ignoreCase = true)
    val mimeCsv = archivo.mimeType?.let {
        it.equals("text/csv", ignoreCase = true) ||
            it.equals("text/plain", ignoreCase = true) ||
            it.contains("comma-separated-values", ignoreCase = true)
    } == true
    if ((extensionCsv || mimeCsv) && pareceTexto(bytes)) {
        return FileFormat.CSV
    }
    return null
}

private fun pareceTexto(bytes: ByteArray): Boolean {
    if (bytes.isEmpty() || bytes.any { it == 0.toByte() }) return false
    val muestra = bytes.take(4096)
    val controles = muestra.count { byte ->
        val value = byte.toInt() and 0xFF
        value < 0x20 && value !in setOf(0x09, 0x0A, 0x0D)
    }
    return controles <= muestra.size / 100
}

private val PDF_SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
private val OLE_SIGNATURE = byteArrayOf(
    0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
    0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
)
private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
