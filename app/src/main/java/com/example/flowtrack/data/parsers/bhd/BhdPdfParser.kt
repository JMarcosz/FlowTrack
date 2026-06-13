package com.example.flowtrack.data.parsers.bhd

import com.example.flowtrack.data.parsers.core.BankStatementParser
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.ParserKey
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import javax.inject.Inject

/**
 * Parser PDF de BHD para estados de cuenta de cuentas.
 */
class BhdPdfParser @Inject constructor(
) : BankStatementParser {

    override val key: ParserKey = ParserKey("BHD", ProductoTipo.CUENTA, FileFormat.PDF)
    override val version: Int = 1

    private val textParser = BhdTextParser()

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto = extraerTexto(request.archivo.bytes, request.claveDocumento)
            textParser.parse(texto)
        } catch (e: InvalidPasswordException) {
            if (request.claveDocumento == null) ParseResult.ClaveRequerida
            else ParseResult.ClaveIncorrecta
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear PDF de BHD: ${e.message}", e)
        }
    }

    private fun extraerTexto(bytes: ByteArray, claveDocumento: String?): String =
        (claveDocumento?.let { PDDocument.load(bytes, it) } ?: PDDocument.load(bytes)).use { doc ->
            PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }
}
