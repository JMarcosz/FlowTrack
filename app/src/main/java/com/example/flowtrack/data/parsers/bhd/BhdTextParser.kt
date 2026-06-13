package com.example.flowtrack.data.parsers.bhd

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.data.parsers.core.EstadoCuentaNormalizado
import com.example.flowtrack.data.parsers.core.MovimientoNormalizado
import com.example.flowtrack.data.parsers.core.ParseReport
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ProductoTipo
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parser textual del estado de cuenta PDF de BHD.
 *
 * El layout del PDF real viene en bloques lineales:
 * Fecha
 * Ref.
 * Detalle
 * Debitos
 * Creditos
 * Balance
 *
 * Cada movimiento ocupa seis líneas y el resumen final trae el balance inicial/final
 * junto con los datos de cuenta.
 */
internal class BhdTextParser {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun parse(texto: String): ParseResult {
        val lineas = normalizarLineas(texto)
        if (lineas.isEmpty()) {
            return ParseResult.Error("El PDF de BHD no contiene texto legible.")
        }

        if (!pareceBhd(lineas)) {
            return ParseResult.Error("El PDF no corresponde a BHD.")
        }

        val encabezadoIndex = encontrarEncabezado(lineas)
            ?: return ParseResult.Error("No se encontró el encabezado de movimientos de BHD.")

        val metadata = extraerMetadata(lineas)
        val movimientos = mutableListOf<MovimientoNormalizado>()
        var ignorados = 0
        var index = encabezadoIndex + 1

        while (index < lineas.size) {
            val linea = lineas[index]
            val normalizada = linea.uppercase().normalizarDescripcion()

            if (esCierreDeMovimientos(normalizada)) {
                break
            }

            if (!esFecha(linea)) {
                index++
                continue
            }

            val parsed = parseMovimiento(lineas, index, metadata.moneda)
            if (parsed == null) {
                ignorados++
                index++
                continue
            }

            movimientos += parsed.movimiento
            index = parsed.siguienteIndex
        }

        if (movimientos.isEmpty()) {
            return ParseResult.Error("No se encontraron movimientos válidos en el PDF de BHD.")
        }

        val ordenados = movimientos.sortedBy { it.fechaTransaccion }
        val balanceInicial = metadata.balanceInicial
            ?: ordenados.firstOrNull()?.balancePosterior
        val balanceFinal = metadata.balanceFinal
            ?: ordenados.lastOrNull()?.balancePosterior

        val estado = EstadoCuentaNormalizado(
            bancoCodigo = "BHD",
            productoTipo = ProductoTipo.CUENTA,
            productoId = metadata.numeroCuenta,
            titular = metadata.titular,
            moneda = metadata.moneda,
            fechaInicio = ordenados.firstOrNull()?.fechaTransaccion,
            fechaFin = ordenados.lastOrNull()?.fechaTransaccion,
            balanceInicial = balanceInicial,
            balanceFinal = balanceFinal,
            movimientos = ordenados,
            numeroCuentaCompleto = metadata.numeroCuentaRegional,
            metadata = mapOf(
                "origen" to "BHD_PDF",
                "numeroCuentaRegional" to (metadata.numeroCuentaRegional ?: ""),
            ),
        )

        val report = ParseReport(
            parserId = "BHD_PDF_v1",
            totalDetectado = movimientos.size + ignorados,
            totalImportado = movimientos.size,
            totalIgnorado = ignorados,
            warnings = buildList {
                if (metadata.balanceInicial == null) {
                    add("No se pudo leer el balance inicial desde el resumen del PDF de BHD.")
                }
                if (metadata.balanceFinal == null) {
                    add("No se pudo leer el balance final desde el resumen del PDF de BHD.")
                }
            },
            errors = emptyList(),
        )

        return ParseResult.Success(estado, report)
    }

    private fun normalizarLineas(texto: String): List<String> =
        texto.lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }

    private fun pareceBhd(lineas: List<String>): Boolean {
        val bloque = lineas.take(100).joinToString(" ").uppercase().normalizarDescripcion()
        return bloque.contains("ESTADO DE CUENTA") &&
            bloque.contains("BHD") &&
            bloque.contains("FECHA") &&
            bloque.contains("REF") &&
            bloque.contains("DETALLE") &&
            bloque.contains("BALANCE")
    }

    private fun encontrarEncabezado(lineas: List<String>): Int? {
        for (index in lineas.indices) {
            val linea = lineas[index].uppercase().normalizarDescripcion()
            if (
                linea.contains("FECHA") &&
                linea.contains("REF") &&
                linea.contains("DETALLE") &&
                linea.contains("DEBITOS") &&
                linea.contains("CREDITOS") &&
                linea.contains("BALANCE")
            ) {
                return index
            }
        }
        return null
    }

    private fun parseMovimiento(
        lineas: List<String>,
        startIndex: Int,
        moneda: Moneda,
    ): MovimientoParseado? {
        val linea = lineas.getOrNull(startIndex) ?: return null

        // Try single-line format first (PDFBox output)
        // Format: {fecha} {referencia} {detalle...} {debito} {credito} {balance}
        val regexSingleLine = Regex("""^(\d{2}/\d{2}/\d{4})\s+(\S+)\s+(.+?)\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})$""")
        val match = regexSingleLine.find(linea)

        if (match != null) {
            val (fechaStr, referencia, detalle, debitoStr, creditoStr, balanceStr) = match.destructured
            val fecha = parseFecha(fechaStr) ?: return null
            val debito = parseMonto(debitoStr) ?: return null
            val credito = parseMonto(creditoStr) ?: return null
            val balance = parseMonto(balanceStr) ?: return null

            val monto = when {
                credito > BigDecimal.ZERO && debito.compareTo(BigDecimal.ZERO) == 0 -> credito
                debito > BigDecimal.ZERO && credito.compareTo(BigDecimal.ZERO) == 0 -> debito
                credito > BigDecimal.ZERO && debito > BigDecimal.ZERO -> if (credito >= debito) credito else debito
                else -> return null
            }

            val tipo = inferirTipo(detalle, debito, credito)

            return MovimientoParseado(
                movimiento = MovimientoNormalizado(
                    fechaTransaccion = fecha,
                    fechaPosteo = null,
                    descripcionOriginal = detalle,
                    descripcionNormalizada = detalle.uppercase().normalizarDescripcion(),
                    descripcionCorta = resumirDescripcion(detalle),
                    monto = monto,
                    tipo = tipo,
                    moneda = moneda,
                    balancePosterior = balance,
                    referencia = referencia,
                    metadata = mapOf(
                        "origen" to "BHD_PDF",
                        "debito" to debito.toPlainString(),
                        "credito" to credito.toPlainString(),
                    ),
                ),
                siguienteIndex = startIndex + 1,
            )
        }

        // Fallback to multi-line format
        val fechaStr = linea.take(10).trim()
        val fecha = parseFecha(fechaStr) ?: return null

        val referencia = lineas.getOrNull(startIndex + 1)
            ?.takeIf { !esMoneyLine(it) && !esFecha(it) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val inicioDetalle = startIndex + 2
        val bloque = lineas.drop(inicioDetalle).take(MAX_LINEAS_POR_MOVIMIENTO)
        val inicioMontos = bloque.indexOfFirst { parseMonto(it) != null }
        if (inicioMontos <= 0) return null
        val detallePartes = bloque.take(inicioMontos)
        val lineasMonto = bloque.drop(inicioMontos).take(3)
        if (lineasMonto.size != 3) return null
        val montos = lineasMonto.map { parseMonto(it) ?: return null }
        val siguienteIndex = inicioDetalle + inicioMontos + 3

        val debito = montos[0]
        val credito = montos[1]
        val balance = montos[2]
        val detalle = detallePartes.joinToString(" ").trim()
        if (detalle.isBlank()) return null

        val monto = when {
            credito > BigDecimal.ZERO && debito.compareTo(BigDecimal.ZERO) == 0 -> credito
            debito > BigDecimal.ZERO && credito.compareTo(BigDecimal.ZERO) == 0 -> debito
            credito > BigDecimal.ZERO && debito > BigDecimal.ZERO -> if (credito >= debito) credito else debito
            else -> return null
        }

        val tipo = inferirTipo(detalle, debito, credito)

        return MovimientoParseado(
            movimiento = MovimientoNormalizado(
                fechaTransaccion = fecha,
                fechaPosteo = null,
                descripcionOriginal = detalle,
                descripcionNormalizada = detalle.uppercase().normalizarDescripcion(),
                descripcionCorta = resumirDescripcion(detalle),
                monto = monto,
                tipo = tipo,
                moneda = moneda,
                balancePosterior = balance,
                referencia = referencia,
                metadata = mapOf(
                    "origen" to "BHD_PDF",
                    "debito" to debito.toPlainString(),
                    "credito" to credito.toPlainString(),
                ),
            ),
            siguienteIndex = siguienteIndex,
        )
    }

    private fun inferirTipo(
        detalle: String,
        debito: BigDecimal,
        credito: BigDecimal,
    ): TipoMovimiento {
        val normalizada = detalle.uppercase().normalizarDescripcion()
        return when {
            credito > BigDecimal.ZERO -> TipoMovimiento.INGRESO
            normalizada.contains("PAGO") -> TipoMovimiento.PAGO_TARJETA
            normalizada.contains("TRANSFERENCIA") -> TipoMovimiento.TRANSFERENCIA
            normalizada.contains("COMISION") -> TipoMovimiento.COMISION
            normalizada.contains("IMPUESTO") || normalizada.contains("DGII") -> TipoMovimiento.IMPUESTO
            normalizada.contains("DEVOLUCION") || normalizada.contains("REVERSO") -> TipoMovimiento.DEVOLUCION
            normalizada.contains("AJUSTE") -> TipoMovimiento.AJUSTE
            debito > BigDecimal.ZERO -> TipoMovimiento.GASTO
            else -> TipoMovimiento.GASTO
        }
    }

    private fun resumirDescripcion(detalle: String): String {
        val normalizada = detalle.uppercase().normalizarDescripcion()
        return when {
            normalizada.contains("TRANSFERENCIA RECIBIDA") -> "Transferencia recibida"
            normalizada.contains("TRANSFERENCIA") && normalizada.contains("RECIB") -> "Transferencia recibida"
            normalizada.contains("TRANSFERENCIA") && normalizada.contains("SALID") -> "Transferencia saliente"
            normalizada.contains("PAGO") -> "Pago"
            normalizada.contains("COMISION") -> "Comisión"
            normalizada.contains("IMPUESTO") || normalizada.contains("DGII") -> "Impuesto"
            else -> detalle.take(40)
        }
    }

    private fun extraerMetadata(lineas: List<String>): MetadataBhd {
        val titular = extraerTitular(lineas)
        val numeroCuenta = extraerValorDespuesDeEtiqueta(lineas, "Numero de Cuenta")
        val numeroCuentaRegional = extraerValorDespuesDeEtiqueta(lineas, "Numero de Cuenta Regional")
        val moneda = extraerValorDespuesDeEtiqueta(lineas, "Moneda")
            ?.let { valor ->
                when (valor.uppercase().normalizarDescripcion()) {
                    "RD" -> Moneda.DOP
                    "US" -> Moneda.USD
                    else -> Moneda.DOP
                }
            } ?: Moneda.DOP

        val balanceInicial = extraerMontoDespuesDeEtiqueta(lineas, "Balance Inicial")
            ?: extraerMontoDespuesDeEtiqueta(lineas, "Balance al inicial")
        val balanceFinal = extraerMontoDespuesDeEtiqueta(lineas, "Balance Final")

        return MetadataBhd(
            titular = titular,
            numeroCuenta = numeroCuenta,
            numeroCuentaRegional = numeroCuentaRegional,
            moneda = moneda,
            balanceInicial = balanceInicial,
            balanceFinal = balanceFinal,
        )
    }

    private fun extraerTitular(lineas: List<String>): String {
        val idxRnc = lineas.indexOfFirst { linea ->
            linea.uppercase().normalizarDescripcion() == "RNC"
        }
        if (idxRnc >= 0) {
            return limpiarTitular(
                lineas.getOrNull(idxRnc + 1)
                    ?: "TITULAR"
            )
        }

        val idxDocumento = lineas.indexOfFirst { linea ->
            linea.uppercase().normalizarDescripcion().contains("DOCUMENTO EMITIDO POR EL")
        }
        if (idxDocumento > 0) {
            return limpiarTitular(
                lineas.getOrNull(idxDocumento - 6)
                    ?: lineas.getOrNull(idxDocumento - 1)
                    ?: "TITULAR"
            )
        }

        return "TITULAR"
    }

    private fun limpiarTitular(valor: String): String {
        val sinDigitos = valor.replace(Regex("\\d"), " ")
        val sinSlash = sinDigitos.replace(Regex("/.*$"), " ")
        return sinSlash
            .replace(Regex("[^\\p{L}\\s,.-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "TITULAR" }
    }

    private fun extraerValorDespuesDeEtiqueta(lineas: List<String>, etiqueta: String): String? {
        val etiquetaNormalizada = etiqueta.uppercase().normalizarDescripcion()
        val idx = lineas.indexOfFirst { linea ->
            linea.uppercase().normalizarDescripcion() == etiquetaNormalizada
        }
        if (idx == -1) return null
        return lineas.getOrNull(idx + 1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extraerMontoDespuesDeEtiqueta(lineas: List<String>, etiqueta: String): BigDecimal? {
        val etiquetaNormalizada = etiqueta.uppercase().normalizarDescripcion()
        val idx = lineas.indexOfLast { linea ->
            linea.uppercase().normalizarDescripcion().contains(etiquetaNormalizada)
        }
        if (idx == -1) return null

        val parteDespuesDeDosPuntos = lineas[idx].substringAfter(":", "").trim()
        if (parteDespuesDeDosPuntos.isNotEmpty()) {
            parseMonto(parteDespuesDeDosPuntos)?.let { return it }
        }

        parseMonto(lineas[idx])?.let { return it }

        return lineas.drop(idx + 1)
            .firstNotNullOfOrNull { linea ->
                parseMonto(linea)
            }
    }

    private fun parseFecha(valor: String): LocalDate? =
        runCatching { LocalDate.parse(valor, dateFormatter) }.getOrNull()

    private fun parseMonto(valor: String): BigDecimal? =
        valor.toBigDecimalSafe()
            ?: valor.removePrefix("$").toBigDecimalSafe()

    private fun esFecha(valor: String): Boolean =
        valor.take(10).matches(Regex("""\d{2}/\d{2}/\d{4}"""))

    private fun esMoneyLine(valor: String): Boolean =
        valor.matches(Regex("""^(?:(?:RD|US)\$|\$)[\d,]+\.\d{2}$""", RegexOption.IGNORE_CASE))

    private fun esCierreDeMovimientos(valorNormalizado: String): Boolean =
        valorNormalizado.startsWith("TOTAL") ||
            valorNormalizado.startsWith("BALANCE FINAL") ||
            valorNormalizado.startsWith("PAGINA") ||
            valorNormalizado.startsWith("VERIFIQUE LA AUTENTICIDAD") ||
            valorNormalizado.startsWith("DOCUMENTO EMITIDO POR EL") ||
            valorNormalizado.startsWith("NUMERO DE CUENTA") ||
            valorNormalizado.startsWith("MONEDA") ||
            valorNormalizado.startsWith("BALANCE INICIAL") ||
            valorNormalizado.startsWith("CHEQUES EN TRANSITO")

    private data class MovimientoParseado(
        val movimiento: MovimientoNormalizado,
        val siguienteIndex: Int,
    )

    private data class MetadataBhd(
        val titular: String,
        val numeroCuenta: String?,
        val numeroCuentaRegional: String?,
        val moneda: Moneda,
        val balanceInicial: BigDecimal?,
        val balanceFinal: BigDecimal?,
    )

    private companion object {
        const val MAX_LINEAS_POR_MOVIMIENTO = 12
    }
}
