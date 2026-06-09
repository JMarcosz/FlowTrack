package com.example.flowtrack.data.parsers.banreservas

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
 * Parser para estados de cuenta de BanReservas (PDF tabular).
 *
 * Columnas: Fecha | Referencia | Concepto | Cheques y cargos | Depósitos y abonos | Balance
 *
 * Estrategia de parseo:
 *  1. Regex de montos restringido a formato bancario (X,XXX.XX) para no confundir
 *     referencias numéricas largas con montos.
 *  2. Tipo (débito/crédito) determinado por el cambio de balance entre líneas consecutivas:
 *     balance subió → crédito; bajó → débito. Sin dependencia de qué columna está populada.
 *  3. "Balance Anterior" se captura antes del primer movimiento como balance de referencia.
 */
class BanReservasPdfParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF)
    override val version: Int = 2

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ibanRegex     = Regex("""DO\d{2}BRRD\d+""")

    // Solo matchea montos en formato bancario: 1-3 dígitos, grupos de miles con coma, 2 decimales.
    // NO matchea referencias como "8500123456789" porque no tienen separador ".XX".
    private val moneyPattern = Regex("""\d{1,3}(?:,\d{3})*\.\d{2}""")

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto  = extraerTexto(request.archivo.bytes)
            val lineas = texto.lines()

            val (numeroCuenta, titular, iban) = extraerInfoCuenta(texto)
            val (movimientos, advertencias, ignorados) = extraerMovimientos(lineas)

            val movimientosOrdenados = movimientos.sortedBy { it.fechaTransaccion }

            val estado = EstadoCuentaNormalizado(
                bancoCodigo          = "BANRESERVAS",
                productoTipo         = ProductoTipo.CUENTA,
                productoId           = numeroCuenta,
                titular              = titular,
                moneda               = Moneda.DOP,
                fechaInicio          = movimientosOrdenados.firstOrNull()?.fechaTransaccion,
                fechaFin             = movimientosOrdenados.lastOrNull()?.fechaTransaccion,
                balanceInicial       = null,
                balanceFinal         = movimientosOrdenados.lastOrNull { it.balancePosterior != null }?.balancePosterior,
                movimientos          = movimientosOrdenados,
                numeroCuentaCompleto = iban,
            )

            val report = ParseReport(
                parserId       = "BANRESERVAS_PDF_v$version",
                totalDetectado = movimientos.size + ignorados,
                totalImportado = movimientos.size,
                totalIgnorado  = ignorados,
                warnings       = advertencias,
                errors         = emptyList(),
            )

            ParseResult.Success(estado, report)
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear PDF de BanReservas: ${e.message}", e)
        }
    }

    // ─── Texto ───────────────────────────────────────────────────────────────

    private fun extraerTexto(bytes: ByteArray): String =
        PDDocument.load(bytes).use { doc ->
            PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }

    // ─── Info de cuenta ───────────────────────────────────────────────────────

    private fun extraerInfoCuenta(texto: String): Triple<String, String, String?> {
        val iban = ibanRegex.find(texto)?.value
        val numeroCuenta = Regex(
            """(?:Cuenta|No\.?\s*Cuenta|Número de cuenta)[:\s]+(\d[\d\s\-]+)""",
            RegexOption.IGNORE_CASE
        ).find(texto)?.groupValues?.getOrNull(1)?.trim()
            ?: iban?.takeLast(10)
            ?: "DESCONOCIDA"
        val titular = Regex(
            """(?:Titular|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]+)""",
            RegexOption.IGNORE_CASE
        ).find(texto)?.groupValues?.getOrNull(1)?.trim()?.take(60) ?: "TITULAR"
        return Triple(
            numeroCuenta.replace(Regex("[\\s\\-]"), "").takeLast(10),
            titular,
            iban,
        )
    }

    // ─── Movimientos ─────────────────────────────────────────────────────────

    private fun extraerMovimientos(
        lineas: List<String>
    ): Triple<List<MovimientoNormalizado>, List<String>, Int> {
        val movimientos  = mutableListOf<MovimientoNormalizado>()
        val advertencias = mutableListOf<String>()
        var ignorados    = 0
        var prevBalance: BigDecimal? = null

        val headerIdx = lineas.indexOfFirst { l ->
            val u = l.uppercase()
            u.contains("FECHA") && (u.contains("REFERENCIA") || u.contains("CONCEPTO"))
        }
        if (headerIdx == -1) {
            advertencias.add("No se encontró el encabezado de la tabla de transacciones.")
            return Triple(emptyList(), advertencias, 0)
        }

        for (linea in lineas.subList(headerIdx + 1, lineas.size)) {
            val lineaUpper = linea.uppercase()

            // ── Condiciones de parada (sección de resumen/totales) ───────────
            if (lineaUpper.contains("TOTAL DEB") ||
                lineaUpper.contains("TOTAL CRÉD") ||
                lineaUpper.contains("TOTAL CRED") ||
                lineaUpper.contains("CHEQUES PAGADOS")
            ) break

            // ── Capturar balance anterior como referencia inicial ─────────────
            if (lineaUpper.contains("BALANCE ANTERIOR") || lineaUpper.contains("SALDO ANTERIOR")) {
                if (movimientos.isNotEmpty()) break  // aparece en el pie → fin
                val nums = moneyPattern.findAll(linea).toList()
                if (nums.isNotEmpty()) prevBalance = nums.last().value.toBigDecimalSafe()
                continue
            }

            val mov = parsearLinea(linea, prevBalance)
            if (mov != null) {
                movimientos.add(mov)
                prevBalance = mov.balancePosterior
            } else {
                ignorados++
            }
        }

        if (movimientos.isEmpty()) advertencias.add("No se parseó ningún movimiento.")
        return Triple(movimientos, advertencias, ignorados)
    }

    /**
     * Parsea una sola línea del estado de cuenta.
     *
     * Algoritmo:
     *  1. Extraer fecha al inicio.
     *  2. Buscar montos con [moneyPattern] en el resto de la línea (ignora referencias numéricas).
     *  3. Último monto = balance posterior.
     *  4. Penúltimo monto = importe de la transacción.
     *  5. Todo lo que está antes del primer monto = referencia + concepto (separados por 2+ espacios).
     *  6. Tipo determinado por cambio de balance respecto al prevBalance;
     *     si no hay prevBalance usa keywords del concepto.
     */
    private fun parsearLinea(linea: String, prevBalance: BigDecimal?): MovimientoNormalizado? {
        val trimmed = linea.trim()
        if (trimmed.length < 15) return null

        val fechaMatch = Regex("""^(\d{2}/\d{2}/\d{4})""").find(trimmed) ?: return null
        val fecha = try {
            LocalDate.parse(fechaMatch.value, dateFormatter)
        } catch (_: Exception) { return null }

        val restante = trimmed.substring(fechaMatch.value.length).trim()

        // Extraer únicamente valores en formato bancario (evita falsos positivos de referencias)
        val moneyNums = moneyPattern.findAll(restante).toList()
        if (moneyNums.size < 2) return null  // necesitamos al menos importe + balance

        val balance = moneyNums.last().value.toBigDecimalSafe() ?: return null
        val importe = moneyNums[moneyNums.size - 2].value.toBigDecimalSafe() ?: return null
        if (importe <= BigDecimal.ZERO) return null

        // Concepto: todo lo anterior al primer monto
        val primerMontoIdx = moneyNums.first().range.first
        val parteConcepto  = restante.substring(0, primerMontoIdx).trim()

        // Separar referencia del concepto por 2+ espacios (columnas del PDF)
        val tokens    = parteConcepto.split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotBlank() }
        val esRefNum  = tokens.isNotEmpty() && tokens[0].matches(Regex("""\d+"""))
        val referencia = if (esRefNum) tokens[0] else null
        val concepto   = when {
            esRefNum && tokens.size >= 2 -> tokens.drop(1).joinToString(" ").trim()
            tokens.isNotEmpty()          -> tokens.joinToString(" ").trim()
            else                         -> ""
        }
        if (concepto.isBlank()) return null

        // Determinar tipo por cambio de balance (método más fiable)
        val tipoMovimiento: TipoMovimiento = if (prevBalance != null) {
            val diff = balance - prevBalance
            when {
                diff > BigDecimal.ZERO -> TipoMovimiento.INGRESO
                diff < BigDecimal.ZERO -> clasificarGasto(concepto)
                else                   -> return null  // balance sin cambio, línea de ruido
            }
        } else {
            // Primer movimiento sin prevBalance: usar keywords
            inferirTipoPorKeyword(concepto)
        }

        return MovimientoNormalizado(
            fechaTransaccion       = fecha,
            fechaPosteo            = null,
            descripcionOriginal    = concepto,
            descripcionNormalizada = concepto.uppercase().normalizarDescripcion(),
            descripcionCorta       = normalizarConcepto(concepto),
            monto                  = importe,
            tipo                   = tipoMovimiento,
            moneda                 = Moneda.DOP,
            balancePosterior       = balance,
            referencia             = referencia,
            metadata               = mapOf("lineaOriginal" to linea.trim()),
        )
    }

    private fun inferirTipoPorKeyword(concepto: String): TipoMovimiento {
        val upper = concepto.uppercase()
        val esIngreso = upper.contains("DEP ") || upper.contains("DEPOSITO") ||
            upper.contains("NOMINA") || upper.contains("NÓMINA") ||
            upper.contains("TRANSF RECIB") || upper.contains("TRANS RECIB") ||
            upper.contains("ABONO") || upper.contains("CR ") ||
            upper.contains("CREDITO RECIB") || upper.contains("TRANSFERENCIA RECIB")
        return if (esIngreso) TipoMovimiento.INGRESO else clasificarGasto(concepto)
    }

    private fun clasificarGasto(concepto: String): TipoMovimiento {
        val upper = concepto.uppercase().normalizarDescripcion()
        return when {
            upper.contains("COBRO IMP") || upper.contains("DGII")    -> TipoMovimiento.IMPUESTO
            upper.contains("COMISION")                               -> TipoMovimiento.COMISION
            upper.contains("RETIRO ATM") || upper.contains("RETIRO SAB") -> TipoMovimiento.RETIRO_ATM
            upper.contains("TRANS CREDITO") || upper.contains("TRANS. CREDITO") ||
                upper.contains("LBTR")                               -> TipoMovimiento.TRANSFERENCIA
            else                                                     -> TipoMovimiento.GASTO
        }
    }

    private fun normalizarConcepto(concepto: String): String {
        val upper = concepto.uppercase().normalizarDescripcion()
        return when {
            upper.contains("CONSUMO POS")                             -> "Consumo POS"
            upper.contains("RETIRO ATM")                              -> "Retiro ATM"
            upper.contains("RETIRO SAB")                              -> "Retiro Sucursal"
            upper.contains("TRANS CREDITO") || upper.contains("TRANS. CREDITO") -> "Transferencia saliente"
            upper.contains("CR TRANSFERENCIA") || upper.contains("TRANSF PROPIA") ->
                if (upper.contains("AHORRO")) "Transferencia propia" else "Transferencia entrante"
            upper.contains("NOMINAS ACH") || upper.contains("NOMINA") -> "Nómina"
            upper.contains("DEBITO CTA")                              -> "Pago debitado"
            upper.contains("COBRO IMP") || upper.contains("DGII")    -> "Impuesto DGII"
            upper.contains("COMISION MENSUAL")                        -> "Comisión mensual"
            upper.contains("COMISION")                                -> "Comisión"
            upper.contains("LBTR")                                    -> "Transferencia LBTR"
            upper.contains("COBRO DE PENDIENTES")                     -> "Cargo pendiente"
            else -> concepto.take(40)
        }
    }
}
