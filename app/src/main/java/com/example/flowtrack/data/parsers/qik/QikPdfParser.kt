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
 * Auto-extracción: límite de crédito, tasa de interés, fecha de corte, fecha límite de pago.
 */
class QikPdfParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("QIK", ProductoTipo.TARJETA, FileFormat.PDF)
    override val version: Int = 1

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateFormatterAlt = DateTimeFormatter.ofPattern("dd/MM/yy")

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto = extraerTexto(request.archivo.bytes)
            val ultimos4 = extraerUltimos4(texto) ?: return ParseResult.Error("No se pudo extraer información de la tarjeta Qik.")
            val titular = extraerTitular(texto)
            val limite = extraerMonto(texto, "(?:Límite|Limite)[:\\s]+(?:RD\\$?\\s*)?([\\d,]+\\.?\\d*)")
            val tasa = Regex("""(?:Tasa|TEA|TNA)[:\s]+([\d.]+)\s*%""", RegexOption.IGNORE_CASE)
                .find(texto)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 60.0

            val fechaCorte = buscarFecha(texto, "Fecha de corte") ?: return ParseResult.Error("No se pudo extraer fecha de corte Qik.")
            val fechaPago = buscarFecha(texto, "Fecha.*pago") ?: fechaCorte.plusDays(25)
            val balanceCorte = extraerMonto(texto, "Balance al corte|Saldo al corte")
            val pagoMinimo = extraerMonto(texto, "Pago m[ií]nimo")
            val pagoTotal = extraerMonto(texto, "Pago total|Pago contado")
            val balanceAnterior = extraerMonto(texto, "Balance anterior|Saldo anterior").takeIf { it > BigDecimal.ZERO }
            val interes = extraerMonto(texto, "Interés|Interes por financiamiento").takeIf { it > BigDecimal.ZERO }
            val cashback = extraerMonto(texto, "Cashback|Cash[Bb]ack").takeIf { it > BigDecimal.ZERO }

            val (movimientos, advertencias, ignorados) = extraerMovimientos(texto)

            val estado = EstadoCuentaNormalizado(
                bancoCodigo = "QIK",
                productoTipo = ProductoTipo.TARJETA,
                productoId = ultimos4,
                titular = titular,
                moneda = Moneda.DOP,
                fechaInicio = movimientos.minOfOrNull { it.fechaTransaccion },
                fechaFin = movimientos.maxOfOrNull { it.fechaTransaccion } ?: fechaCorte,
                balanceInicial = balanceAnterior,
                balanceFinal = balanceCorte,
                movimientos = movimientos,
                fechaCorte = fechaCorte,
                fechaLimitePago = fechaPago,
                pagoMinimo = pagoMinimo,
                pagoTotal = pagoTotal,
                montoVencido = BigDecimal.ZERO,
                limiteCredito = limite,
                tipoRed = "VISA",
                tasaInteresAnual = tasa,
                balancePromedioDiario = null,
                interesPorFinanciamiento = interes,
                cashbackGanado = cashback,
            )

            val report = ParseReport(
                parserId = "QIK_PDF_v$version",
                totalDetectado = movimientos.size + ignorados,
                totalImportado = movimientos.size,
                totalIgnorado = ignorados,
                warnings = advertencias,
                errors = emptyList(),
            )

            ParseResult.Success(estado, report)
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear PDF de Qik: ${e.message}", e)
        }
    }

    private fun extraerTexto(bytes: ByteArray): String =
        PDDocument.load(bytes).use { doc ->
            PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }

    private fun extraerUltimos4(texto: String): String? =
        Regex("""(?:\*{4}|\d{4}\s\d{4}\s\d{4}\s)(\d{4})""").find(texto)?.groupValues?.getOrNull(1)

    private fun extraerTitular(texto: String): String =
        Regex("""(?:Titular|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\s]+)""")
            .find(texto)?.groupValues?.getOrNull(1)?.trim()?.take(60) ?: "TITULAR"

    private fun extraerMonto(texto: String, patron: String): BigDecimal =
        Regex("""$patron[:\s]+(?:RD\$?\s*)?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO

    private fun buscarFecha(texto: String, patron: String): LocalDate? =
        Regex("""$patron[:\s]+(\d{2}/\d{2}/\d{2,4})""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.let { fechaStr ->
                runCatching { LocalDate.parse(fechaStr, dateFormatter) }
                    .recoverCatching { LocalDate.parse(fechaStr, dateFormatterAlt) }
                    .getOrNull()
            }

    private fun extraerMovimientos(texto: String): Triple<List<MovimientoNormalizado>, List<String>, Int> {
        val movimientos = mutableListOf<MovimientoNormalizado>()
        val advertencias = mutableListOf<String>()
        var ignorados = 0
        val lineas = texto.lines()

        val headerIdx = lineas.indexOfFirst { l ->
            val u = l.uppercase()
            u.contains("FECHA") && (u.contains("DESCRIPCION") || u.contains("DESCRIPCIÓN") || u.contains("COMERCIO"))
        }
        if (headerIdx == -1) {
            advertencias.add("No se encontró tabla de movimientos.")
            return Triple(movimientos, advertencias, ignorados)
        }

        for (linea in lineas.subList(headerIdx + 1, lineas.size)) {
            if (linea.isBlank()) continue
            val upper = linea.uppercase()
            if (upper.contains("TOTAL") || upper.contains("SUBTOTAL") || upper.contains("BALANCE")) break

            val fechaMatch = Regex("""^(\d{2}/\d{2}/\d{2,4})""").find(linea.trim()) ?: continue
            val fecha = runCatching { LocalDate.parse(fechaMatch.value, dateFormatter) }
                .recoverCatching { LocalDate.parse(fechaMatch.value, dateFormatterAlt) }
                .getOrNull()
            if (fecha == null) { ignorados++; continue }

            val restante = linea.trim().substring(fechaMatch.value.length).trim()
            val tokens = restante.split(Regex("\\s{2,}"))
            if (tokens.size < 2) continue

            val montoStr = tokens.last().trim()
            val montoRaw = montoStr.replace(Regex("[(),]"), "").toBigDecimalSafe()
            if (montoRaw == null) { ignorados++; continue }

            val descripcion = tokens.dropLast(1).joinToString(" ").trim()
            val descNorm = descripcion.uppercase().normalizarDescripcion()

            val esCredito = montoStr.contains("(") || montoStr.contains("-")
            val tipo = when {
                descNorm.contains("CASHBACK")                                       -> TipoMovimiento.CASHBACK
                descNorm.contains("INTERES")                                        -> TipoMovimiento.INTERES
                descNorm.contains("COMISION")                                       -> TipoMovimiento.COMISION
                descNorm.contains("PAGO") && !descNorm.contains("PAGOS EN")        -> TipoMovimiento.PAGO_TARJETA
                esCredito && (descNorm.contains("DEVOLUCION") ||
                    descNorm.contains("REVERSO") || descNorm.contains("REVERSAL") ||
                    descNorm.contains("ABONO") || descNorm.contains("CREDITO"))     -> TipoMovimiento.DEVOLUCION
                esCredito                                                            -> TipoMovimiento.PAGO_TARJETA
                else                                                                -> TipoMovimiento.GASTO
            }

            movimientos.add(MovimientoNormalizado(
                fechaTransaccion = fecha,
                fechaPosteo = null,
                descripcionOriginal = descripcion,
                descripcionNormalizada = descNorm,
                descripcionCorta = descNorm.take(40),
                monto = montoRaw.abs(),
                tipo = tipo,
                moneda = Moneda.DOP,
                balancePosterior = null,
                referencia = null,
                metadata = mapOf("lineaOriginal" to linea.trim()),
            ))
        }
        if (ignorados > 0) advertencias.add("$ignorados movimiento(s) ignorados por datos inválidos.")
        return Triple(movimientos, advertencias, ignorados)
    }
}
