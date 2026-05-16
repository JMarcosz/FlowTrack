package com.example.flowtrack.data.parsers.cibao

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
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parser XLS de Asociación Cibao (tarjeta de crédito).
 *
 * Formato: hoja 'CreditCardDetail' con sección de resumen y tabla de movimientos.
 * Columnas: Tarjeta | Fecha | Descripción | Débito | Crédito
 */
class CibaoXlsParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("CIBAO", ProductoTipo.TARJETA, FileFormat.XLS)
    override val version: Int = 1

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ZONA = ZoneId.of("America/Santo_Domingo")

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            // WorkbookFactory detecta automáticamente XLS (HSSF) o XLSX (XSSF)
            val wb = try { WorkbookFactory.create(request.archivo.bytes.inputStream()) }
                catch (e: Exception) { return ParseResult.Error("No se pudo abrir el archivo de Cibao (XLS/XLSX).", e) }
            val hoja = wb.getSheetAt(0)

            val ultimos4 = extraerUltimos4(hoja) ?: run { wb.close(); return ParseResult.Error("No se pudo extraer número de tarjeta Cibao.") }
            val titular = extraerTitular(hoja)
            val limite = extraerMontoDeHoja(hoja, "(?:Límite|Limite|L[ií]mite de cr[eé]dito)[:\\s]+(?:RD\\$?\\s*)?([\\d,]+\\.?\\d*)")
            val corte = extraerFechaDeHoja(hoja, "Fecha de corte") ?: LocalDate.now()
            val fechaPago = extraerFechaDeHoja(hoja, "Fecha.*pago") ?: corte.plusDays(25)
            val balanceCorte = extraerMontoDeHoja(hoja, "Balance al corte|Saldo")
            val balanceAnterior = extraerMontoDeHoja(hoja, "Balance anterior").takeIf { it > BigDecimal.ZERO }
            val pagoMinimo = extraerMontoDeHoja(hoja, "Pago m[ií]nimo")
            val pagoTotal = extraerMontoDeHoja(hoja, "Pago total")

            val headerIdx = encontrarHeader(hoja) ?: run { wb.close(); return ParseResult.Error("No se encontró el encabezado de movimientos.") }
            val (movimientos, advertencias, ignorados) = extraerMovimientos(hoja, headerIdx)
            wb.close()

            val estado = EstadoCuentaNormalizado(
                bancoCodigo = "CIBAO",
                productoTipo = ProductoTipo.TARJETA,
                productoId = ultimos4,
                titular = titular,
                moneda = Moneda.DOP,
                fechaInicio = movimientos.minOfOrNull { it.fechaTransaccion },
                fechaFin = movimientos.maxOfOrNull { it.fechaTransaccion } ?: corte,
                balanceInicial = balanceAnterior,
                balanceFinal = balanceCorte,
                movimientos = movimientos,
                fechaCorte = corte,
                fechaLimitePago = fechaPago,
                pagoMinimo = pagoMinimo,
                pagoTotal = pagoTotal,
                montoVencido = BigDecimal.ZERO,
                limiteCredito = limite,
            )

            val report = ParseReport(
                parserId = "CIBAO_XLS_v$version",
                totalDetectado = movimientos.size + ignorados,
                totalImportado = movimientos.size,
                totalIgnorado = ignorados,
                warnings = advertencias,
                errors = emptyList(),
            )

            ParseResult.Success(estado, report)
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear XLS de Cibao: ${e.message}", e)
        }
    }

    private fun textoDeHoja(hoja: Sheet, maxFilas: Int): String =
        (0 until minOf(maxFilas, hoja.lastRowNum + 1))
            .mapNotNull { hoja.getRow(it) }
            .flatMap { row -> (0 until row.lastCellNum).map { row.getCell(it)?.toString() ?: "" } }
            .joinToString(" ").uppercase()

    private fun extraerUltimos4(hoja: Sheet): String? {
        val texto = textoDeHoja(hoja, 15)
        return listOf(
            Regex("""(?:[\*Xx]{4}[\s\-]?){3}(\d{4})(?!\d)"""),
            Regex("""[\*Xx]{4}[\s\-](\d{4})(?!\d)"""),
            Regex("""[\*Xx]{4}(\d{4})(?!\d)"""),
            Regex("""\d{4}[\s\-]\d{4}[\s\-]\d{4}[\s\-](\d{4})"""),
        ).firstNotNullOfOrNull { it.find(texto)?.groupValues?.getOrNull(1) }
    }

    private fun extraerTitular(hoja: Sheet): String {
        val texto = textoDeHoja(hoja, 15)
        return Regex("""(?:Titular|Nombre|Cuentahabiente)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\s]+)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.trim() ?: "TITULAR"
    }

    private fun extraerMontoDeHoja(hoja: Sheet, patron: String): BigDecimal {
        val texto = textoDeHoja(hoja, 20)
        return Regex("""$patron[:\s]+(?:RD\$?\s*)?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO
    }

    private fun extraerFechaDeHoja(hoja: Sheet, patron: String): LocalDate? {
        val texto = textoDeHoja(hoja, 20)
        return Regex("""$patron[:\s]+(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)
            ?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
    }

    private fun encontrarHeader(hoja: Sheet): Int? =
        (0..minOf(25, hoja.lastRowNum)).firstOrNull { idx ->
            val row = hoja.getRow(idx) ?: return@firstOrNull false
            val texto = (0 until row.lastCellNum)
                .joinToString(" ") { row.getCell(it)?.toString() ?: "" }.uppercase()
            texto.contains("FECHA") && (texto.contains("DESCRIPCION") || texto.contains("DESCRIPCIÓN"))
        }

    private fun extraerMovimientos(hoja: Sheet, headerIdx: Int): Triple<List<MovimientoNormalizado>, List<String>, Int> {
        val headerRow = hoja.getRow(headerIdx)
        val colMap = (0 until headerRow.lastCellNum).associate { idx ->
            idx to (headerRow.getCell(idx)?.toString()?.uppercase() ?: "")
        }
        val iColFecha = colMap.entries.firstOrNull { it.value.contains("FECHA") }?.key ?: 1
        val iColDesc = colMap.entries.firstOrNull { e ->
            e.value.contains("DESCRIPCION") || e.value.contains("DESCRIPCIÓN")
        }?.key ?: 2
        val iColDebito = colMap.entries.firstOrNull { e ->
            e.value.contains("CARGO") || e.value.contains("DEBITO") || e.value.contains("DÉBITO")
        }?.key ?: (iColDesc + 1)
        val iColCredito = colMap.entries.firstOrNull { e ->
            e.value.contains("ABONO") || e.value.contains("CREDITO") || e.value.contains("CRÉDITO")
        }?.key ?: (iColDebito + 1)
        val iColAuth = colMap.entries.firstOrNull { e ->
            e.value.contains("AUTORIZACION") || e.value.contains("AUTORIZACIÓN") || e.value.contains("REF")
        }?.key

        val movimientos = mutableListOf<MovimientoNormalizado>()
        val advertencias = mutableListOf<String>()
        var ignorados = 0

        for (rowIdx in (headerIdx + 1)..hoja.lastRowNum) {
            val row = hoja.getRow(rowIdx) ?: continue
            val celdas = (0 until row.lastCellNum).map { row.getCell(it) }
            if (celdas.all { it == null || it.toString().isBlank() }) continue

            val desc = celdas.getOrNull(iColDesc)?.toString()?.trim() ?: continue
            if (desc.isBlank()) continue
            if (desc.uppercase().contains("TOTAL") || desc.uppercase().contains("BALANCE")) break

            val celdaFecha = celdas.getOrNull(iColFecha)
            val debitoStr = celdas.getOrNull(iColDebito)?.toString()?.trim() ?: ""
            val creditoStr = celdas.getOrNull(iColCredito)?.toString()?.trim() ?: ""

            val fecha: LocalDate? = when {
                celdaFecha?.cellType == CellType.NUMERIC ->
                    runCatching { DateUtil.getJavaDate(celdaFecha.numericCellValue).toInstant().atZone(ZONA).toLocalDate() }.getOrNull()
                else ->
                    celdaFecha?.toString()?.trim()?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
            }
            if (fecha == null) { ignorados++; continue }

            val debito = debitoStr.toBigDecimalSafe()
            val credito = creditoStr.toBigDecimalSafe()
            val (monto, tipoBase) = when {
                debito != null && debito > BigDecimal.ZERO -> debito to TipoMovimiento.GASTO
                credito != null && credito > BigDecimal.ZERO -> credito to TipoMovimiento.PAGO_TARJETA
                else -> { ignorados++; continue }
            }

            val descNorm = desc.uppercase().normalizarDescripcion()
            val tipo = when {
                descNorm.contains("PAGO") -> TipoMovimiento.PAGO_TARJETA
                descNorm.contains("INTERES") -> TipoMovimiento.INTERES
                descNorm.contains("COMISION") -> TipoMovimiento.COMISION
                else -> tipoBase
            }

            movimientos.add(MovimientoNormalizado(
                fechaTransaccion = fecha,
                fechaPosteo = null,
                descripcionOriginal = desc,
                descripcionNormalizada = descNorm,
                descripcionCorta = descNorm.take(40),
                monto = monto,
                tipo = tipo,
                moneda = Moneda.DOP,
                balancePosterior = null,
                referencia = iColAuth?.let { celdas.getOrNull(it)?.toString()?.trim() },
                metadata = mapOf("fila" to rowIdx.toString()),
            ))
        }
        if (ignorados > 0) advertencias.add("$ignorados movimiento(s) ignorados.")
        return Triple(movimientos, advertencias, ignorados)
    }
}
