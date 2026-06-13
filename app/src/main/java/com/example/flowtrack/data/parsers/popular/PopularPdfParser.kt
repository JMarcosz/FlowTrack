package com.example.flowtrack.data.parsers.popular

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.data.parsers.core.BankStatementParser
import com.example.flowtrack.data.parsers.core.EstadoCuentaNormalizado
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.MovimientoNormalizado
import com.example.flowtrack.data.parsers.core.ParseReport
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.ParserKey
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ProductoTipo
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Parser del estado de cuenta PDF de Banco Popular.
 *
 * Las filas contienen fechas abreviadas sin año y balance posterior. El movimiento
 * se determina por la diferencia entre balances consecutivos, evitando depender de
 * columnas de débito/crédito que PDFBox puede omitir cuando están vacías.
 */
class PopularPdfParser @Inject constructor() : BankStatementParser {

    override val key = ParserKey("POPULAR", ProductoTipo.CUENTA, FileFormat.PDF)
    override val version: Int = 1

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto = extraerTexto(request.archivo.bytes, request.claveDocumento)
            parsearTexto(texto)
        } catch (_: InvalidPasswordException) {
            if (request.claveDocumento == null) ParseResult.ClaveRequerida
            else ParseResult.ClaveIncorrecta
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear PDF de Popular: ${e.message}", e)
        }
    }

    internal fun parsearTexto(texto: String): ParseResult {
        val lineas = texto.lines()
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotBlank() }

        if (lineas.none { it.uppercase().normalizarDescripcion().contains("BANCO POPULAR") }) {
            return ParseResult.Error("El PDF no corresponde a Banco Popular.")
        }

        val year = YEAR_REGEX.find(texto)?.value?.toIntOrNull()
            ?: return ParseResult.Error("No se pudo determinar el año del estado de cuenta Popular.")
        val mesCorte = extraerMesCorte(texto)
        val headerIndex = encontrarEncabezadoMovimientos(lineas)
        if (headerIndex < 0) {
            return ParseResult.Error("No se encontró el encabezado de movimientos del PDF de Popular.")
        }

        val filas = reconstruirFilas(lineas.drop(headerIndex + 1))
        var balanceAnterior: BigDecimal? = null
        var balanceFinalDeclarado: BigDecimal? = null
        var balancePrevio: BigDecimal? = null
        var ignoradas = 0
        val movimientos = mutableListOf<MovimientoNormalizado>()

        for (fila in filas) {
            val normalizada = fila.texto.uppercase().normalizarDescripcion()
            val balance = extraerBalance(fila.texto)
            if (normalizada.contains("BALANCE ANTERIOR")) {
                balanceAnterior = balance
                balancePrevio = balance
                continue
            }
            if (normalizada.contains("BALANCE AL CORTE")) {
                balanceFinalDeclarado = balance
                continue
            }

            val previo = balancePrevio
            if (balance == null || previo == null) {
                ignoradas++
                continue
            }

            val diferencia = balance.subtract(previo)
            if (diferencia.compareTo(BigDecimal.ZERO) == 0) {
                balancePrevio = balance
                ignoradas++
                continue
            }

            val fecha = fechaPopular(fila.dia, fila.mes, year, mesCorte)
            val esCredito = diferencia.signum() > 0
            val descripcion = limpiarDescripcion(fila.texto)
            val clasificacion = PopularMovementNormalizer.clasificar(
                descripcionCorta = if (esCredito) "Crédito" else "Débito",
                descripcionLarga = descripcion,
            )

            movimientos += MovimientoNormalizado(
                fechaTransaccion = fecha,
                fechaPosteo = fila.fechaTransaccion?.let {
                    fechaPopular(it.first, it.second, year, mesCorte)
                },
                descripcionOriginal = descripcion,
                descripcionNormalizada = clasificacion.descripcionNormalizada,
                descripcionCorta = clasificacion.descripcionCorta,
                monto = diferencia.abs(),
                tipo = clasificacion.tipo,
                moneda = Moneda.DOP,
                balancePosterior = balance,
                referencia = REFERENCIA_REGEX.find(fila.texto)?.value,
                metadata = mapOf("origen" to "POPULAR_PDF"),
            )
            balancePrevio = balance
        }

        if (movimientos.isEmpty()) {
            return ParseResult.Error("No se encontraron movimientos válidos en el PDF de Popular.")
        }

        val ordenados = movimientos.sortedBy { it.fechaTransaccion }
        val numeroCuenta = extraerNumeroCuenta(lineas, headerIndex)
        val balanceFinal = balanceFinalDeclarado
            ?: movimientos.lastOrNull()?.balancePosterior

        return ParseResult.Success(
            estado = EstadoCuentaNormalizado(
                bancoCodigo = "POPULAR",
                productoTipo = ProductoTipo.CUENTA,
                productoId = numeroCuenta,
                titular = "TITULAR",
                moneda = Moneda.DOP,
                fechaInicio = ordenados.first().fechaTransaccion,
                fechaFin = ordenados.last().fechaTransaccion,
                balanceInicial = balanceAnterior,
                balanceFinal = balanceFinal,
                movimientos = ordenados,
            ),
            report = ParseReport(
                parserId = "POPULAR_PDF_v$version",
                totalDetectado = movimientos.size + ignoradas,
                totalImportado = movimientos.size,
                totalIgnorado = ignoradas,
                warnings = if (ignoradas > 0) {
                    listOf("$ignoradas fila(s) sin variación de balance o incompletas fueron ignoradas.")
                } else {
                    emptyList()
                },
                errors = emptyList(),
            ),
        )
    }

    private fun extraerTexto(bytes: ByteArray, clave: String?): String =
        (clave?.let { PDDocument.load(bytes, it) } ?: PDDocument.load(bytes)).use { document ->
            PDFTextStripper().apply { sortByPosition = true }.getText(document)
        }

    private fun reconstruirFilas(lineas: List<String>): List<FilaPopularPdf> {
        val filas = mutableListOf<FilaPopularPdf>()
        var actual: FilaPopularPdf? = null

        for (linea in lineas) {
            val match = ROW_REGEX.find(linea)
            if (match != null) {
                actual?.let(filas::add)
                actual = FilaPopularPdf(
                    dia = match.groupValues[1].toInt(),
                    mes = mesNumero(match.groupValues[2]),
                    fechaTransaccion = match.groupValues[3].takeIf { it.isNotBlank() }?.toInt()?.let {
                        it to mesNumero(match.groupValues[4])
                    },
                    texto = linea,
                )
                if (linea.uppercase().normalizarDescripcion().contains("BALANCE AL CORTE")) {
                    filas += actual
                    actual = null
                    break
                }
            } else if (actual != null && !esPieDePagina(linea)) {
                actual = actual.copy(texto = "${actual.texto} $linea")
            }
        }
        actual?.let(filas::add)
        return filas
    }

    private fun extraerBalance(texto: String): BigDecimal? =
        MONEY_REGEX.findAll(texto).lastOrNull()?.value?.toBigDecimalSafe()

    private fun limpiarDescripcion(texto: String): String {
        var limpia = texto.replaceFirst(ROW_PREFIX_REGEX, "")
        limpia = MONEY_REGEX.replace(limpia, " ")
        limpia = limpia.replace(Regex("""\bRD\$\b|\bRD\$""", RegexOption.IGNORE_CASE), " ")
        limpia = limpia.replace(Regex("""\s+\.\s+"""), " ")
        return limpia.replace(Regex("""\s+"""), " ").trim()
            .ifBlank { "MOVIMIENTO BANCO POPULAR" }
    }

    private fun extraerNumeroCuenta(lineas: List<String>, headerIndex: Int): String {
        val cabecera = lineas.take(headerIndex).takeLast(12).joinToString(" ")
        val desdeEtiqueta = Regex(
            """NUMERO\s+DE\s+CUENTA\D{0,40}(\d{8,})""",
            setOf(RegexOption.IGNORE_CASE),
        ).find(cabecera.uppercase().normalizarDescripcion())
            ?.groupValues?.getOrNull(1)
        return desdeEtiqueta?.takeLast(10) ?: "DESCONOCIDA"
    }

    private fun encontrarEncabezadoMovimientos(lineas: List<String>): Int {
        for (index in lineas.indices) {
            val bloque = lineas
                .subList(index, minOf(index + HEADER_WINDOW_SIZE, lineas.size))
                .joinToString(" ")
                .uppercase()
                .normalizarDescripcion()
            if (
                bloque.contains("ENTRADA") &&
                bloque.contains("TRANSAC") &&
                bloque.contains("BALANCE")
            ) {
                return index
            }
        }
        val balanceAnteriorIndex = lineas.indexOfFirst {
            it.uppercase().normalizarDescripcion().contains("BALANCE ANTERIOR")
        }
        return if (balanceAnteriorIndex > 0) balanceAnteriorIndex - 1 else balanceAnteriorIndex
    }

    private fun extraerMesCorte(texto: String): Int? {
        val normalizado = texto.uppercase().normalizarDescripcion()
        return MESES.entries.firstOrNull { (_, nombre) ->
            Regex("""\b$nombre\b""").containsMatchIn(normalizado)
        }?.key
    }

    private fun fechaPopular(
        dia: Int,
        mes: Int,
        yearCorte: Int,
        mesCorte: Int?,
    ): LocalDate {
        val year = if (mesCorte != null && mes > mesCorte + 6) yearCorte - 1 else yearCorte
        return LocalDate.of(year, mes, dia)
    }

    private fun mesNumero(valor: String): Int =
        MESES_CORTOS[valor.uppercase().normalizarDescripcion()]
            ?: error("Mes no reconocido: $valor")

    private fun esPieDePagina(linea: String): Boolean {
        val normalizada = linea.uppercase().normalizarDescripcion()
        return normalizada.contains("INTERES") ||
            normalizada.contains("PAGINA") ||
            normalizada.contains("TASA") ||
            normalizada.matches(Regex("""[\d.,%+\-\s]+"""))
    }

    private data class FilaPopularPdf(
        val dia: Int,
        val mes: Int,
        val fechaTransaccion: Pair<Int, Int>?,
        val texto: String,
    )

    private companion object {
        const val HEADER_WINDOW_SIZE = 16
        val MESES_CORTOS = mapOf(
            "ENE" to 1, "FEB" to 2, "MAR" to 3, "ABR" to 4,
            "MAY" to 5, "JUN" to 6, "JUL" to 7, "AGO" to 8,
            "SEP" to 9, "OCT" to 10, "NOV" to 11, "DIC" to 12,
        )
        val MESES = mapOf(
            1 to "ENERO", 2 to "FEBRERO", 3 to "MARZO", 4 to "ABRIL",
            5 to "MAYO", 6 to "JUNIO", 7 to "JULIO", 8 to "AGOSTO",
            9 to "SEPTIEMBRE", 10 to "OCTUBRE", 11 to "NOVIEMBRE", 12 to "DICIEMBRE",
        )
        val YEAR_REGEX = Regex("""\b(?:19|20)\d{2}\b""")
        val ROW_REGEX = Regex(
            """^(\d{1,2})(ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEP|OCT|NOV|DIC)\b""" +
                """(?:\s+(\d{1,2})(ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEP|OCT|NOV|DIC)\b)?""",
            RegexOption.IGNORE_CASE,
        )
        val ROW_PREFIX_REGEX = Regex(
            """^\d{1,2}(?:ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEP|OCT|NOV|DIC)\b""" +
                """(?:\s+\d{1,2}(?:ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEP|OCT|NOV|DIC)\b)?""",
            RegexOption.IGNORE_CASE,
        )
        val MONEY_REGEX = Regex("""(?<!\d)[+-]?(?:\d[\d,]*|\d*)\.\d{2}(?!\d)""")
        val REFERENCIA_REGEX = Regex("""\b\d{1,6}-\d{1,6}\b""")
    }
}
