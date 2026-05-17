package com.example.flowtrack.data.parsers.qik

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.data.parsers.core.EstadoCuentaNormalizado
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.MovimientoNormalizado
import com.example.flowtrack.data.parsers.core.ParseReport
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.ParserKey
import com.example.flowtrack.data.parsers.core.BankStatementParser
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ProductoTipo
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parser PDF de Qik Banco Digital (tarjeta de crédito VISA).
 *
 * Formato real del estado de cuenta:
 *   - Tabla con columnas: Fecha | Entrada | Descripción | Monto
 *   - Cada fila tiene DOS fechas (transacción + posteo): "dd/MM/yyyy  dd/MM/yyyy  Descripcion RD$ X,XXX.XX"
 *   - Montos con prefijo "RD$" y comas de miles
 *   - Algunas filas tienen línea de continuación (sin fecha) con descripción adicional
 */
class QikPdfParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("QIK", ProductoTipo.TARJETA, FileFormat.PDF)
    override val version: Int = 1

    private val dateFormatter    = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateFormatterAlt = DateTimeFormatter.ofPattern("dd/MM/yy")

    private val MESES_ES = mapOf(
        "ene" to 1, "feb" to 2, "mar" to 3, "abr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "ago" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dic" to 12,
    )

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto = extraerTexto(request.archivo.bytes)
            val ultimos4 = extraerUltimos4(texto) ?: return ParseResult.Error("No se pudo extraer información de la tarjeta Qik.")
            val titular    = extraerTitular(texto)
            val limite     = extraerMonto(texto, "(?:L[ií]mite|Limite)[:\\s]+")
            val tasa       = Regex("""(?:tasa|TEA|TNA)[:\s]+([\d.]+)\s*%""", RegexOption.IGNORE_CASE)
                .find(texto)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 60.0
            val fechaCorte = buscarFecha(texto, "Fecha de corte")
            val fechaPago  = buscarFecha(texto, "Fecha.*(?:L[ií]mite|Limite).*(?:Pago|Pag)")
                ?: buscarFechaLinea(texto, "Fecha.*pago")
                ?: fechaCorte?.plusDays(25)
            val balanceCorte    = extraerMontoLinea(texto, "Balance al corte|Saldo al corte")
                .takeIf { it > BigDecimal.ZERO }
            val pagoMinimo      = extraerMontoLinea(texto, "Pago m[ií]nimo|M[ií]nimo a pagar")
                .takeIf { it > BigDecimal.ZERO }
            val pagoTotal       = extraerMontoLinea(texto, "Pago total|Pago contado")
                .takeIf { it > BigDecimal.ZERO }
            val balanceAnterior = extraerMontoLinea(texto, "Balance.*anterior|Saldo.*anterior")
                .takeIf { it > BigDecimal.ZERO }
            val interes  = extraerMontoLinea(texto, "Inter[eé]s[^\\n]*financiamiento")
                .takeIf { it > BigDecimal.ZERO }
            val cashback = extraerMontoLinea(texto, "Cashback|Cash[Bb]ack")
                .takeIf { it > BigDecimal.ZERO }

            val (movimientos, advertencias, ignorados) = extraerMovimientos(texto)

            val estado = EstadoCuentaNormalizado(
                bancoCodigo      = "QIK",
                productoTipo     = ProductoTipo.TARJETA,
                productoId       = ultimos4,
                titular          = titular,
                moneda           = Moneda.DOP,
                fechaInicio      = movimientos.minOfOrNull { it.fechaTransaccion },
                fechaFin         = movimientos.maxOfOrNull { it.fechaTransaccion } ?: fechaCorte,
                balanceInicial   = balanceAnterior,
                balanceFinal     = balanceCorte,
                movimientos      = movimientos,
                fechaCorte       = fechaCorte,
                fechaLimitePago  = fechaPago,
                pagoMinimo       = pagoMinimo,
                pagoTotal        = pagoTotal,
                montoVencido     = BigDecimal.ZERO,
                limiteCredito    = limite,
                tipoRed          = "VISA",
                tasaInteresAnual = tasa,
                balancePromedioDiario    = null,
                interesPorFinanciamiento = interes,
                cashbackGanado           = cashback,
            )

            val report = ParseReport(
                parserId       = "QIK_PDF_v$version",
                totalDetectado = movimientos.size + ignorados,
                totalImportado = movimientos.size,
                totalIgnorado  = ignorados,
                warnings       = advertencias,
                errors         = emptyList(),
            )

            ParseResult.Success(estado, report)
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear PDF de Qik: ${e.message}", e)
        }
    }

    // ─── Extracción de texto ──────────────────────────────────────────────────

    private fun extraerTexto(bytes: ByteArray): String =
        PDDocument.load(bytes).use { doc ->
            PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }

    // ─── Metadata ────────────────────────────────────────────────────────────

    private fun extraerUltimos4(texto: String): String? =
        Regex("""\*+(\d{4})""").find(texto)?.groupValues?.getOrNull(1)
            ?: Regex("""\d{4}\s\d{4}\s\d{4}\s(\d{4})""").find(texto)?.groupValues?.getOrNull(1)

    private fun extraerTitular(texto: String): String =
        Regex("""(?:Hola,?\s+|Titular[:\s]+|Nombre[:\s]+)([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\s]+)""")
            .find(texto)?.groupValues?.getOrNull(1)?.trim()?.take(60) ?: "TITULAR"

    /** Extrae monto en la misma línea que el patrón. */
    private fun extraerMonto(texto: String, patron: String): BigDecimal =
        Regex("""$patron\s*(?:RD\$?\s*)?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO

    /** Busca el patrón y luego el primer monto en esa línea o la siguiente. */
    private fun extraerMontoLinea(texto: String, patron: String): BigDecimal {
        val montoInlinea = Regex("""$patron[^\\n]*?(?:RD\$?\s*)?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe()
        if (montoInlinea != null) return montoInlinea

        // Fallback: find label then scan next non-blank line for a monto
        val matchStart = Regex(patron, RegexOption.IGNORE_CASE).find(texto)?.range?.last ?: return BigDecimal.ZERO
        val sigText = texto.substring(matchStart).lines()
            .drop(1).firstOrNull { it.isNotBlank() } ?: return BigDecimal.ZERO
        return Regex("""RD\$?\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(sigText)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO
    }

    private fun buscarFecha(texto: String, patron: String): LocalDate? {
        // Formato numérico en la misma línea: dd/MM/yyyy o dd/MM/yy
        Regex("""$patron[:\s]+(\d{1,2}/\d{2}/\d{2,4})""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.let { s ->
                runCatching { LocalDate.parse(s, dateFormatter) }
                    .recoverCatching { LocalDate.parse(s, dateFormatterAlt) }
                    .getOrNull()?.let { return it }
            }
        // Formato textual español en la misma línea: "25 abr 2026"
        Regex("""$patron[:\s]+(\d{1,2})\s+([a-záéíóúñ]{3,4})\.?\s+(\d{4})""", RegexOption.IGNORE_CASE)
            .find(texto)?.let { m ->
                val dia  = m.groupValues[1].toIntOrNull() ?: return@let null
                val mes  = MESES_ES[m.groupValues[2].lowercase().take(3)] ?: return@let null
                val anio = m.groupValues[3].toIntOrNull() ?: return@let null
                runCatching { LocalDate.of(anio, mes, dia) }.getOrNull()?.let { return it }
            }
        return null
    }

    /** Busca el patrón y luego una fecha española en la siguiente línea. */
    private fun buscarFechaLinea(texto: String, patron: String): LocalDate? {
        val matchEnd = Regex(patron, RegexOption.IGNORE_CASE).find(texto)?.range?.last ?: return null
        val sigLinea = texto.substring(matchEnd).lines().drop(1).firstOrNull { it.isNotBlank() } ?: return null
        Regex("""(\d{1,2})\s+([a-záéíóúñ]{3,4})\.?\s+(\d{4})""", RegexOption.IGNORE_CASE)
            .find(sigLinea)?.let { m ->
                val dia  = m.groupValues[1].toIntOrNull() ?: return@let null
                val mes  = MESES_ES[m.groupValues[2].lowercase().take(3)] ?: return@let null
                val anio = m.groupValues[3].toIntOrNull() ?: return@let null
                return runCatching { LocalDate.of(anio, mes, dia) }.getOrNull()
            }
        return null
    }

    // ─── Extracción de movimientos ────────────────────────────────────────────

    private fun extraerMovimientos(texto: String): Triple<List<MovimientoNormalizado>, List<String>, Int> {
        val movimientos  = mutableListOf<MovimientoNormalizado>()
        val advertencias = mutableListOf<String>()
        var ignorados    = 0
        val lineas       = texto.lines()

        // Find the transaction table header (contains "FECHA" + description/entrada column name)
        val headerIdx = lineas.indexOfFirst { l ->
            val norm = l.normalizarDescripcion()
            norm.contains("FECHA") && (norm.contains("DESCRIPCION") || norm.contains("ENTRADA") || norm.contains("COMERCIO"))
        }
        if (headerIdx == -1) {
            advertencias.add("No se encontró tabla de movimientos.")
            return Triple(movimientos, advertencias, ignorados)
        }

        // Two dates at start: "  dd/MM/yyyy  dd/MM/yyyy  ...rest..."
        val dosFechasReg = Regex("""^\s*(\d{2}/\d{2}/\d{2,4})\s+(\d{2}/\d{2}/\d{2,4})\s+(.+)$""")
        // One date at start (fallback)
        val unaFechaReg  = Regex("""^\s*(\d{2}/\d{2}/\d{2,4})\s+(.+)$""")
        // Amount anchored at end: "RD$ X,XXX.XX" — must have cents
        val montoFinalReg = Regex("""RD\$?\s*([\d,]+\.\d{2})\s*$""", RegexOption.IGNORE_CASE)

        var pendFecha:     LocalDate?  = null
        var pendPosteo:    LocalDate?  = null
        var pendDesc:      String      = ""
        var pendMonto:     BigDecimal? = null

        fun commitPending() {
            val fecha = pendFecha ?: return
            val monto = pendMonto ?: run { ignorados++; pendFecha = null; pendDesc = ""; return }
            val descNorm  = pendDesc.trim().normalizarDescripcion()
            val tipo = when {
                descNorm.contains("CASHBACK") || descNorm.contains("REBATE") ||
                    descNorm.contains("RECOMPENSA") || descNorm.contains("PAYMENT RETURN") -> TipoMovimiento.CASHBACK
                descNorm.contains("INTERES")                                               -> TipoMovimiento.INTERES
                descNorm.contains("COMISION")                                              -> TipoMovimiento.COMISION
                descNorm.contains("PAGO") && !descNorm.contains("PAGOS EN")               -> TipoMovimiento.PAGO_TARJETA
                descNorm.contains("DEVOLUCION") || descNorm.contains("REVERSO") ||
                    descNorm.contains("REVERSAL") || descNorm.contains("ABONO")           -> TipoMovimiento.DEVOLUCION
                else                                                                        -> TipoMovimiento.GASTO
            }
            movimientos.add(
                MovimientoNormalizado(
                    fechaTransaccion    = fecha,
                    fechaPosteo         = pendPosteo,
                    descripcionOriginal = pendDesc.trim(),
                    descripcionNormalizada = descNorm,
                    descripcionCorta    = descNorm.take(40),
                    monto               = monto.abs(),
                    tipo                = tipo,
                    moneda              = Moneda.DOP,
                    balancePosterior    = null,
                    referencia          = null,
                    metadata            = emptyMap(),
                )
            )
            pendFecha = null; pendPosteo = null; pendDesc = ""; pendMonto = null
        }

        for (linea in lineas.subList(headerIdx + 1, lineas.size)) {
            if (linea.isBlank()) continue
            val norm = linea.normalizarDescripcion()

            // Stop at "Información Adicional" section or page footer
            if (norm.contains("INFORMACION ADICIONAL")) break
            if (norm.startsWith("WWW QIK") || (norm.contains("QIK BANCO DIGITAL") && norm.contains("PAG "))) break

            val dosMatch = dosFechasReg.find(linea)
            if (dosMatch != null) {
                commitPending()
                val fecha = parseDate(dosMatch.groupValues[1])
                if (fecha == null) { ignorados++; continue }
                val posteo  = parseDate(dosMatch.groupValues[2])
                val resto   = dosMatch.groupValues[3]
                val montoM  = montoFinalReg.find(resto)
                if (montoM == null) { ignorados++; continue }
                val monto   = montoM.groupValues[1].toBigDecimalSafe()
                if (monto == null) { ignorados++; continue }
                pendFecha   = fecha
                pendPosteo  = posteo
                pendDesc    = resto.substring(0, montoM.range.first).trim()
                pendMonto   = monto
                continue
            }

            val unaMatch = unaFechaReg.find(linea)
            if (unaMatch != null) {
                commitPending()
                val fecha  = parseDate(unaMatch.groupValues[1])
                if (fecha == null) { ignorados++; continue }
                val resto  = unaMatch.groupValues[2]
                val montoM = montoFinalReg.find(resto)
                if (montoM == null) { ignorados++; continue }
                val monto  = montoM.groupValues[1].toBigDecimalSafe()
                if (monto == null) { ignorados++; continue }
                pendFecha  = fecha
                pendPosteo = null
                pendDesc   = resto.substring(0, montoM.range.first).trim()
                pendMonto  = monto
                continue
            }

            // Continuation line — append to pending description
            if (pendFecha != null) {
                val trimmed = linea.trim()
                if (trimmed.isNotBlank()) {
                    pendDesc = if (pendDesc.isBlank()) trimmed else "$pendDesc $trimmed"
                }
            }
        }
        commitPending()

        if (ignorados > 0) advertencias.add("$ignorados movimiento(s) ignorados por datos inválidos.")
        return Triple(movimientos, advertencias, ignorados)
    }

    private fun parseDate(s: String): LocalDate? =
        runCatching { LocalDate.parse(s, dateFormatter) }
            .recoverCatching { LocalDate.parse(s, dateFormatterAlt) }
            .getOrNull()
}
