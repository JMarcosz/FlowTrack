package com.example.flowtrack.data.parsers.qik

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.BankParser
import com.example.flowtrack.data.parsers.core.ConfianzaDeteccion
import com.example.flowtrack.data.parsers.core.ContextoParseo
import com.example.flowtrack.data.parsers.core.EstadoTarjetaDetectado
import com.example.flowtrack.data.parsers.core.MovimientoTarjetaNormalizado
import com.example.flowtrack.data.parsers.core.ResultadoParseo
import com.example.flowtrack.data.parsers.core.TarjetaDetectada
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parser PDF de Qik Banco Digital (tarjeta de crédito VISA).
 *
 * Señales de detección:
 *   0.5 → texto "QIK" o "qik.com.do"
 *   0.3 → texto "VISA" + estructura de estado de cuenta de tarjeta
 *   0.2 → columnas "Compras" / "Pagos" / "Fecha de corte"
 *
 * Auto-extracción: límite de crédito, tasa de interés, fecha de corte, fecha límite de pago.
 */
class QikPdfParser @Inject constructor() : BankParser {

    override val codigoBanco: String = "QIK"
    override val tipoDocumento: TipoDocumento = TipoDocumento.TARJETA_CREDITO
    override val version: Int = 1
    override val formatosArchivo: Set<String> = setOf("pdf")

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateFormatterAlt = DateTimeFormatter.ofPattern("dd/MM/yy")

    override suspend fun puedeManejar(archivo: ArchivoEntrada): ConfianzaDeteccion {
        if (archivo.extension != "pdf") return ConfianzaDeteccion(0f, "No es PDF")
        return try {
            val texto = extraerTexto(archivo.bytes).uppercase()
            var confianza = 0f
            val razones = mutableListOf<String>()
            val pistas = mutableMapOf<String, String>()

            if (texto.contains("QIK") || texto.contains("QIK.COM.DO")) {
                confianza += 0.5f; razones.add("texto 'QIK'"); pistas["marca"] = "QIK"
            }
            if (texto.contains("VISA") && texto.contains("TARJETA DE CREDITO")) {
                confianza += 0.3f; razones.add("VISA + tarjeta de crédito")
            }
            if (texto.contains("FECHA DE CORTE") || texto.contains("FECHA LIMITE DE PAGO")) {
                confianza += 0.2f; razones.add("campos de corte")
            }
            ConfianzaDeteccion(confianza.coerceAtMost(1f), razones.joinToString(", ").ifEmpty { "Sin señales" }, pistas)
        } catch (e: Exception) { ConfianzaDeteccion(0f, "Error: ${e.message}") }
    }

    override suspend fun parsear(archivo: ArchivoEntrada, contexto: ContextoParseo): ResultadoParseo {
        return try {
            val texto = extraerTexto(archivo.bytes)
            val tarjeta = extraerTarjeta(texto)
                ?: return ResultadoParseo.Error("No se pudo extraer información de la tarjeta Qik.")
            val estadoTarjeta = extraerEstadoTarjeta(texto)
                ?: return ResultadoParseo.Error("No se pudo extraer el estado de corte Qik.")
            val (movimientos, advertencias) = extraerMovimientos(texto)

            ResultadoParseo.ExitoTarjeta(tarjeta, estadoTarjeta, movimientos, advertencias)
        } catch (e: Exception) {
            ResultadoParseo.Error("Error al parsear PDF de Qik: ${e.message}", e)
        }
    }

    private fun extraerTexto(bytes: ByteArray): String =
        PDDocument.load(bytes).use { doc ->
            PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }

    private fun extraerTarjeta(texto: String): TarjetaDetectada? {
        // Últimos 4 dígitos: "XXXX XXXX XXXX 1234" o "****1234"
        val ultimos4Regex = Regex("""(?:\*{4}|\d{4}\s\d{4}\s\d{4}\s)(\d{4})""")
        val ultimos4 = ultimos4Regex.find(texto)?.groupValues?.getOrNull(1) ?: return null

        val titularRegex = Regex("""(?:Titular|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\s]+)""")
        val titular = titularRegex.find(texto)?.groupValues?.getOrNull(1)?.trim() ?: "TITULAR"

        val limiteRegex = Regex("""(?:Límite|Limite)[:\s]+(?:RD\$?\s*)?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
        val limite = limiteRegex.find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO

        val tasaRegex = Regex("""(?:Tasa|TEA|TNA)[:\s]+([\d.]+)\s*%""", RegexOption.IGNORE_CASE)
        val tasa = tasaRegex.find(texto)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 60.0

        return TarjetaDetectada(
            ultimos4 = ultimos4, titular = titular.take(60), tipoRed = "VISA",
            limiteCredito = limite, moneda = Moneda.DOP,
            diaCorte = null, diaPago = null, tasaInteresAnual = tasa,
        )
    }

    private fun extraerEstadoTarjeta(texto: String): EstadoTarjetaDetectado? {
        fun buscarMonto(patron: String): BigDecimal {
            return Regex("""$patron[:\s]+(?:RD\$?\s*)?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
                .find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO
        }
        fun buscarFecha(patron: String): LocalDate? {
            return Regex("""$patron[:\s]+(\d{2}/\d{2}/\d{2,4})""", RegexOption.IGNORE_CASE)
                .find(texto)?.groupValues?.getOrNull(1)?.let {
                    try { LocalDate.parse(it, dateFormatter) }
                    catch (_: Exception) {
                        try { LocalDate.parse(it, dateFormatterAlt) } catch (_: Exception) { null }
                    }
                }
        }

        val fechaCorte = buscarFecha("Fecha de corte") ?: return null
        val fechaPago = buscarFecha("Fecha.*pago") ?: fechaCorte.plusDays(25)
        val balanceCorte = buscarMonto("Balance al corte|Saldo al corte")
        val pagoMinimo = buscarMonto("Pago m[ií]nimo")
        val pagoTotal = buscarMonto("Pago total|Pago contado")
        val balanceAnterior = buscarMonto("Balance anterior|Saldo anterior").takeIf { it > BigDecimal.ZERO }
        val interes = buscarMonto("Interés|Interes por financiamiento").takeIf { it > BigDecimal.ZERO }
        val cashback = buscarMonto("Cashback|Cash[Bb]ack").takeIf { it > BigDecimal.ZERO }

        return EstadoTarjetaDetectado(
            fechaCorte = fechaCorte, fechaLimitePago = fechaPago,
            balanceAlCorte = balanceCorte, balanceAnterior = balanceAnterior,
            pagoMinimo = pagoMinimo, pagoTotal = pagoTotal,
            montoVencido = BigDecimal.ZERO, balancePromedioDiario = null,
            interesPorFinanciamiento = interes, cashbackGanado = cashback,
        )
    }

    private fun extraerMovimientos(texto: String): Pair<List<MovimientoTarjetaNormalizado>, List<String>> {
        val movimientos = mutableListOf<MovimientoTarjetaNormalizado>()
        val advertencias = mutableListOf<String>()
        val lineas = texto.lines()

        val headerIdx = lineas.indexOfFirst { l ->
            val u = l.uppercase()
            u.contains("FECHA") && (u.contains("DESCRIPCION") || u.contains("DESCRIPCIÓN") || u.contains("COMERCIO"))
        }
        if (headerIdx == -1) { advertencias.add("No se encontró tabla de movimientos."); return Pair(movimientos, advertencias) }

        var fallidas = 0
        for (linea in lineas.subList(headerIdx + 1, lineas.size)) {
            if (linea.isBlank()) continue
            val upper = linea.uppercase()
            if (upper.contains("TOTAL") || upper.contains("SUBTOTAL") || upper.contains("BALANCE")) break

            val fechaMatch = Regex("""^(\d{2}/\d{2}/\d{2,4})""").find(linea.trim()) ?: continue
            val fechaStr = fechaMatch.value
            val fechaParseada = runCatching {
                LocalDate.parse(fechaStr, dateFormatter)
            }.recoverCatching {
                LocalDate.parse(fechaStr, dateFormatterAlt)
            }.getOrNull()

            if (fechaParseada == null) {
                fallidas++
                continue
            }

            val fecha: LocalDate = fechaParseada

            val restante = linea.trim().substring(fechaMatch.value.length).trim()
            val tokens = restante.split(Regex("\\s{2,}"))
            if (tokens.size < 2) continue

            val montoStr = tokens.last().trim()
            val montoRaw = montoStr.replace(Regex("[(),]"), "").toBigDecimalSafe()
            if (montoRaw == null) { fallidas++; continue }
            val monto = montoRaw
            val descripcion = tokens.dropLast(1).joinToString(" ").trim()

            val esDebito = !montoStr.contains("(") && !montoStr.contains("-")
            val tipoMov = when {
                descripcion.uppercase().normalizarDescripcion().contains("PAGO") -> TipoMovimientoTarjeta.PAGO
                descripcion.uppercase().normalizarDescripcion().contains("CASHBACK") -> TipoMovimientoTarjeta.CASHBACK
                descripcion.uppercase().normalizarDescripcion().contains("INTERES") -> TipoMovimientoTarjeta.INTERES
                esDebito -> TipoMovimientoTarjeta.COMPRA
                else -> TipoMovimientoTarjeta.PAGO
            }

            movimientos.add(MovimientoTarjetaNormalizado(
                fechaTransaccion = fecha, fechaPosteo = null,
                descripcionOriginal = descripcion,
                monto = monto.abs(), tipoMovimiento = tipoMov,
                moneda = Moneda.DOP, numeroAutorizacion = null,
                metadataBanco = mapOf("lineaOriginal" to linea.trim()),
            ))
        }
        if (fallidas > 0) advertencias.add("$fallidas movimiento(s) ignorados por datos inválidos.")
        return Pair(movimientos, advertencias)
    }
}
