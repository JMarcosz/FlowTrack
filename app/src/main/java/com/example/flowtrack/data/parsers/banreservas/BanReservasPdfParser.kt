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
 * Parser para estados de cuenta de BanReservas (cuenta corriente, formato PDF tabular).
 *
 * Columnas: Fecha | Referencia | Concepto | Cheques y cargos | Depósitos y abonos | Balance
 */
class BanReservasPdfParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("BANRESERVAS", ProductoTipo.CUENTA, FileFormat.PDF)
    override val version: Int = 1

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ibanRegex = Regex("""DO\d{2}BRRD\d+""")

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto = extraerTexto(request.archivo.bytes)
            val lineas = texto.lines()

            val (numeroCuenta, titular, iban) = extraerInfoCuenta(texto)
            val (movimientos, advertencias, ignorados) = extraerMovimientos(lineas)

            val estado = EstadoCuentaNormalizado(
                bancoCodigo = "BANRESERVAS",
                productoTipo = ProductoTipo.CUENTA,
                productoId = numeroCuenta,
                titular = titular,
                moneda = Moneda.DOP,
                fechaInicio = movimientos.minOfOrNull { it.fechaTransaccion },
                fechaFin = movimientos.maxOfOrNull { it.fechaTransaccion },
                balanceInicial = null,
                balanceFinal = movimientos.lastOrNull()?.balancePosterior,
                movimientos = movimientos,
                numeroCuentaCompleto = iban,
            )

            val report = ParseReport(
                parserId = "BANRESERVAS_PDF_v$version",
                totalDetectado = movimientos.size + ignorados,
                totalImportado = movimientos.size,
                totalIgnorado = ignorados,
                warnings = advertencias,
                errors = emptyList(),
            )

            ParseResult.Success(estado, report)
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear PDF de BanReservas: ${e.message}", e)
        }
    }

    // ─── Extracción de texto PDF ──────────────────────────────────────────────

    private fun extraerTexto(bytes: ByteArray): String =
        PDDocument.load(bytes).use { doc ->
            PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }

    // ─── Info de cuenta ───────────────────────────────────────────────────────

    private fun extraerInfoCuenta(texto: String): Triple<String, String, String?> {
        val iban = ibanRegex.find(texto)?.value
        val numeroCuenta = Regex("""(?:Cuenta|No\.?\s*Cuenta|Número de cuenta)[:\s]+(\d[\d\s\-]+)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.trim()
            ?: iban?.takeLast(10)
            ?: "DESCONOCIDA"
        val titular = Regex("""(?:Titular|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]+)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.trim()?.take(60) ?: "TITULAR"
        return Triple(numeroCuenta.replace(Regex("[\\s\\-]"), "").takeLast(10), titular, iban)
    }

    // ─── Extracción de movimientos ────────────────────────────────────────────

    private fun extraerMovimientos(lineas: List<String>): Triple<List<MovimientoNormalizado>, List<String>, Int> {
        val movimientos = mutableListOf<MovimientoNormalizado>()
        val advertencias = mutableListOf<String>()
        var ignorados = 0

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
            if (lineaUpper.contains("CHEQUES PAGADOS:") ||
                lineaUpper.contains("TOTAL DÉBITOS") ||
                (lineaUpper.contains("BALANCE ANTERIOR") && movimientos.isNotEmpty())
            ) break

            val mov = parsearLinea(linea)
            if (mov != null) movimientos.add(mov) else ignorados++
        }

        return Triple(movimientos, advertencias, ignorados)
    }

    // Regex que captura un número con formato bancario: opcionalmente negativo,
    // separador de miles en coma, 2 decimales. Ej: "1,234.56" ó "-50.00"
    private val numericPattern = Regex("""-?\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?""")

    private fun parsearLinea(linea: String): MovimientoNormalizado? {
        val trimmed = linea.trim()
        if (trimmed.length < 20) return null

        val fechaMatch = Regex("""^(\d{2}/\d{2}/\d{4})""").find(trimmed) ?: return null
        val fecha = try { LocalDate.parse(fechaMatch.value, dateFormatter) } catch (_: Exception) { return null }

        val restante = trimmed.substring(fechaMatch.value.length).trim()

        // Extraer los números que aparecen al final de la línea (balance, depósito, cargo).
        // Se buscan en posición: al final de la cadena los últimos 3 tokens numéricos.
        // Esto evita que tokens numéricos embebidos en el concepto (refs, horas) desplacen columnas.
        val allNums = numericPattern.findAll(restante).toList()
        if (allNums.size < 2) return null

        val balanceStr = allNums.last().value
        val balance = balanceStr.toBigDecimalSafe()

        // Las dos columnas de monto son los dos penúltimos números al final de la línea.
        // Si hay 3+ números, los penúltimos 2 son cargo y depósito; el anterior y todo lo que
        // queda es el concepto + referencia.
        val montoChequesStr = if (allNums.size >= 3) allNums[allNums.size - 3].value else ""
        val montoDepositosStr = if (allNums.size >= 2) allNums[allNums.size - 2].value else ""
        val montoCheques = montoChequesStr.toBigDecimalSafe()
        val montoDepositos = montoDepositosStr.toBigDecimalSafe()

        // Concepto: todo lo que está antes del primer número de la columna de montos.
        // Cortar desde la posición del tercer número desde el final (o segundo si no hay tres).
        val primerMontoIdx = if (allNums.size >= 3) allNums[allNums.size - 3].range.first
                             else allNums[allNums.size - 2].range.first
        val parteConcepto = restante.substring(0, primerMontoIdx).trim()
        val conceptoTokens = parteConcepto.split(Regex("\\s{2,}"))
        val referencia = conceptoTokens.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() &&
            !it.matches(Regex("""\d{2}:\d{2}""")) } // descartar timestamps HH:MM como referencia
        val concepto = if (conceptoTokens.size >= 2) conceptoTokens.drop(1).joinToString(" ").trim()
                       else conceptoTokens.getOrNull(0)?.trim() ?: ""
        if (concepto.isBlank()) return null

        val (monto, tipoMovimiento) = when {
            // Cuando ambas columnas tienen valor, usar el concepto como desempate.
            // Palabras clave de crédito tienen prioridad sobre la posición de columna.
            montoCheques != null && montoCheques > BigDecimal.ZERO &&
            montoDepositos != null && montoDepositos > BigDecimal.ZERO -> {
                val upper = concepto.uppercase()
                val esIngreso = upper.contains("DEP ") || upper.contains("DEPOSITO") ||
                    upper.contains("NOMINA") || upper.contains("NÓMINA") ||
                    upper.contains("TRANSF RECIB") || upper.contains("TRANS RECIB") ||
                    upper.contains("TRANSFERENCIA RECIB") || upper.contains("ABONO") ||
                    upper.contains("CREDITO RECIBIDO")
                if (esIngreso) Pair(montoDepositos, TipoMovimiento.INGRESO)
                else Pair(montoCheques, clasificarGasto(concepto))
            }
            montoCheques != null && montoCheques > BigDecimal.ZERO ->
                Pair(montoCheques, clasificarGasto(concepto))
            montoDepositos != null && montoDepositos > BigDecimal.ZERO ->
                Pair(montoDepositos, TipoMovimiento.INGRESO)
            else -> return null
        }

        val descripcionCorta = normalizarConcepto(concepto)
        val descNorm = concepto.uppercase().normalizarDescripcion()

        return MovimientoNormalizado(
            fechaTransaccion = fecha,
            fechaPosteo = null,
            descripcionOriginal = concepto,
            descripcionNormalizada = descNorm,
            descripcionCorta = descripcionCorta,
            monto = monto,
            tipo = tipoMovimiento,
            moneda = Moneda.DOP,
            balancePosterior = balance,
            referencia = referencia,
            metadata = mapOf("lineaOriginal" to linea.trim()),
        )
    }

    private fun clasificarGasto(concepto: String): TipoMovimiento {
        val upper = concepto.uppercase().normalizarDescripcion()
        return when {
            upper.contains("COBRO IMP") || upper.contains("DGII") -> TipoMovimiento.IMPUESTO
            upper.contains("COMISION") -> TipoMovimiento.COMISION
            upper.contains("RETIRO ATM") || upper.contains("RETIRO SAB") -> TipoMovimiento.RETIRO_ATM
            upper.contains("TRANS CREDITO") || upper.contains("TRANS. CREDITO") ||
                upper.contains("LBTR") -> TipoMovimiento.TRANSFERENCIA
            else -> TipoMovimiento.GASTO
        }
    }

    private fun normalizarConcepto(concepto: String): String {
        val upper = concepto.uppercase().normalizarDescripcion()
        return when {
            upper.contains("CONSUMO POS") -> "CONSUMO POS"
            upper.contains("RETIRO ATM") -> "RETIRO ATM"
            upper.contains("RETIRO SAB") -> "RETIRO SUCURSAL"
            upper.contains("TRANS CREDITO") || upper.contains("TRANS. CREDITO") -> "TRANSFERENCIA SALIENTE"
            upper.contains("CR TRANSFERENCIA") || upper.contains("TRANSF PROPIA") -> when {
                upper.contains("AHORRO") -> "TRANSFERENCIA PROPIA"
                else -> "TRANSFERENCIA ENTRANTE"
            }
            upper.contains("NOMINAS ACH") || upper.contains("NOMINA") -> "NOMINA"
            upper.contains("DEBITO CTA") -> "PAGO DEBITADO"
            upper.contains("COBRO IMP") || upper.contains("DGII") -> "IMPUESTO DGII"
            upper.contains("COMISION MENSUAL") -> "COMISION ATM"
            upper.contains("COMISION") -> "COMISION"
            upper.contains("LBTR") -> "TRANSFERENCIA LBTR"
            upper.contains("COBRO DE PENDIENTES") -> "CARGO PENDIENTE"
            else -> upper.take(40)
        }
    }
}
