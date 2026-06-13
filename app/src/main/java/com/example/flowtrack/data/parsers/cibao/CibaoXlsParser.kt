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
import org.apache.poi.EncryptedDocumentException
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parser XLS de Asociación Cibao (tarjeta de crédito VISA).
 *
 * Formato real (hoja `CreditCardDetail`):
 *  - Metadata en layout COLUMNAR: una fila de etiquetas seguida de la fila de valores.
 *    Ej: fila "Número de tarjeta | Alias | Fecha de corte | Fecha de vencimiento | Límite (US$) | Límite ($)"
 *    y debajo "XXXX...8763 | VISA CLASICA | 15/03/2026 | 12/04/2026 | 300.00 | 42,000.00".
 *  - Cada métrica viene en dos monedas: columna "(US$)" y columna "($)" (DOP). Se usa la DOP como base.
 *  - Tabla de movimientos: "Fecha | Número de tarjeta | Número de autorización | Descripción |
 *    Monto local | Monto en dólares". Las dos últimas son montos PARALELOS (DOP / USD), no débito/crédito:
 *    cada movimiento trae su monto en DOP (`monto`) y, si aplica, el equivalente/cargo en USD (`montoUsd`).
 */
class CibaoXlsParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("CIBAO", ProductoTipo.TARJETA, FileFormat.XLS)
    override val version: Int = 2

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ZONA = ZoneId.of("America/Santo_Domingo")

    // Filas a inspeccionar para metadata (antes de la tabla de movimientos).
    private val MAX_FILA_META = 12

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val wb = try {
                request.archivo.bytes.inputStream().use { input ->
                    request.claveDocumento?.let { WorkbookFactory.create(input, it) }
                        ?: WorkbookFactory.create(input)
                }
            } catch (e: EncryptedDocumentException) {
                return if (request.claveDocumento == null) ParseResult.ClaveRequerida
                else ParseResult.ClaveIncorrecta
            } catch (e: Exception) {
                return ParseResult.Error("No se pudo abrir el archivo de Cibao (XLS/XLSX).", e)
            }

            val hoja = wb.getSheetAt(0)

            val tarjetaTxt = valorAbajoDe(hoja) { it.contains("NUMERO DE TARJETA") }
            val ultimos4 = tarjetaTxt?.let { extraerUltimos4(it) }
                ?: run { wb.close(); return ParseResult.Error("No se pudo extraer número de tarjeta Cibao.") }

            val titular = extraerTitular(hoja)

            val corte = valorAbajoDe(hoja) { it.contains("FECHA DE CORTE") }
                ?.let { parseFecha(it) } ?: LocalDate.now()
            val fechaPago = valorAbajoDe(hoja) { it.contains("VENCIMIENTO") || it.contains("FECHA DE PAGO") }
                ?.let { parseFecha(it) } ?: corte.plusDays(25)

            // Montos en la columna DOP ("($)" y NO "(US$)").
            val limite        = montoDopAbajoDe(hoja) { it.contains("LIMITE DE CREDITO") }
            val balanceAnterior = montoDopAbajoDe(hoja) { it.contains("SALDO DEL CORTE ANTERIOR") }
                .takeIf { it > BigDecimal.ZERO }
            val pagoMinimo    = montoDopAbajoDe(hoja) { it.contains("PAGO MINIMO") }
            val pagoContado   = montoDopAbajoDe(hoja) { it.contains("PAGO CONTADO") || it.contains("PAGO TOTAL") }
            val montoVencido  = montoDopAbajoDe(hoja) { it.contains("MONTO VENCIDO") }

            val headerIdx = encontrarHeaderMovimientos(hoja)
                ?: run { wb.close(); return ParseResult.Error("No se encontró el encabezado de movimientos.") }
            val (movimientos, advertencias, ignorados) = extraerMovimientos(hoja, headerIdx)
            wb.close()

            // En tarjetas, el balance al corte es el total a pagar (pago contado).
            val balanceCorte = pagoContado

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
                pagoTotal = pagoContado,
                montoVencido = montoVencido,
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

    // ─── Helpers de metadata columnar (label en una fila, valor en la de abajo) ──

    /** Texto de una celda como string, manejando fechas/numéricos de Excel. */
    private fun textoCelda(cell: Cell?): String = when {
        cell == null -> ""
        cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell) ->
            runCatching {
                DateUtil.getJavaDate(cell.numericCellValue).toInstant().atZone(ZONA).toLocalDate().format(dateFormatter)
            }.getOrDefault("")
        else -> cell.toString()
    }.trim()

    /** Versión ASCII en mayúsculas para comparar etiquetas sin depender de acentos. */
    private fun String.sinAcentos(): String = uppercase()
        .replace(Regex("[ÁÀÄÂ]"), "A").replace(Regex("[ÉÈËÊ]"), "E")
        .replace(Regex("[ÍÌÏÎ]"), "I").replace(Regex("[ÓÒÖÔ]"), "O")
        .replace(Regex("[ÚÙÜÛ]"), "U").replace(Regex("Ñ"), "N")

    /**
     * Busca (en las primeras [MAX_FILA_META] filas) la primera celda cuya etiqueta cumpla [pred]
     * y devuelve el texto de la celda inmediatamente debajo (misma columna).
     */
    private fun valorAbajoDe(hoja: Sheet, pred: (String) -> Boolean): String? {
        for (r in 0..minOf(MAX_FILA_META, hoja.lastRowNum)) {
            val row = hoja.getRow(r) ?: continue
            for (c in 0 until row.lastCellNum) {
                val etiqueta = textoCelda(row.getCell(c))
                if (etiqueta.isNotEmpty() && pred(etiqueta.sinAcentos())) {
                    val abajo = textoCelda(hoja.getRow(r + 1)?.getCell(c))
                    if (abajo.isNotBlank()) return abajo
                }
            }
        }
        return null
    }

    /**
     * Igual que [valorAbajoDe] pero restringido a la columna DOP: la etiqueta debe cumplir [pred],
     * contener "($)" y NO "(US$)". Si solo hay una variante, igual la toma. Devuelve 0 si no encuentra.
     */
    private fun montoDopAbajoDe(hoja: Sheet, pred: (String) -> Boolean): BigDecimal {
        // Primero intenta la columna DOP explícita.
        val dop = valorAbajoDe(hoja) { it.contains("($)") && !it.contains("(US$)") && pred(it) }
        if (dop != null) return dop.toBigDecimalSafe() ?: BigDecimal.ZERO
        // Fallback: cualquier variante que cumpla el predicado.
        val cualquiera = valorAbajoDe(hoja, pred)
        return cualquiera?.toBigDecimalSafe() ?: BigDecimal.ZERO
    }

    private fun extraerUltimos4(texto: String): String? =
        listOf(
            Regex("""(?:[*Xx]{4}[\s\-]?){3}(\d{4})(?!\d)"""),
            Regex("""[*Xx]{4}[\s\-](\d{4})(?!\d)"""),
            Regex("""[*Xx]{4}(\d{4})(?!\d)"""),
            Regex("""\d{4}[\s\-]\d{4}[\s\-]\d{4}[\s\-](\d{4})"""),
            Regex("""(\d{4})(?!\d)\s*$"""),
        ).firstNotNullOfOrNull { it.find(texto)?.groupValues?.getOrNull(1) }

    private fun extraerTitular(hoja: Sheet): String =
        valorAbajoDe(hoja) { it.trim() == "TITULAR" || it.contains("CUENTAHABIENTE") }
            ?.takeIf { it.isNotBlank() } ?: "TITULAR"

    private fun parseFecha(s: String): LocalDate? =
        runCatching { LocalDate.parse(s.trim(), dateFormatter) }.getOrNull()

    // ─── Movimientos ─────────────────────────────────────────────────────────

    private fun encontrarHeaderMovimientos(hoja: Sheet): Int? =
        (0..hoja.lastRowNum).firstOrNull { idx ->
            val row = hoja.getRow(idx) ?: return@firstOrNull false
            val texto = (0 until row.lastCellNum)
                .joinToString(" ") { textoCelda(row.getCell(it)) }.sinAcentos()
            texto.contains("FECHA") && texto.contains("DESCRIPCION") &&
                (texto.contains("MONTO LOCAL") || texto.contains("MONTO EN DOLAR"))
        }

    private fun extraerMovimientos(hoja: Sheet, headerIdx: Int): Triple<List<MovimientoNormalizado>, List<String>, Int> {
        val headerRow = hoja.getRow(headerIdx)
        val colMap = (0 until headerRow.lastCellNum).associate { idx ->
            idx to textoCelda(headerRow.getCell(idx)).sinAcentos()
        }
        fun col(vararg claves: String): Int? =
            colMap.entries.firstOrNull { e -> claves.any { e.value.contains(it) } }?.key

        val iFecha = col("FECHA") ?: 0
        val iDesc  = col("DESCRIPCION") ?: 3
        val iLocal = col("MONTO LOCAL") ?: (iDesc + 1)
        val iUsd   = col("MONTO EN DOLAR", "DOLARES") ?: (iLocal + 1)
        val iAuth  = col("AUTORIZACION", "REFERENCIA")

        val movimientos = mutableListOf<MovimientoNormalizado>()
        val advertencias = mutableListOf<String>()
        var ignorados = 0

        for (rowIdx in (headerIdx + 1)..hoja.lastRowNum) {
            val row = hoja.getRow(rowIdx) ?: continue
            if ((0 until row.lastCellNum).all { textoCelda(row.getCell(it)).isBlank() }) continue

            val desc = textoCelda(row.getCell(iDesc))
            if (desc.isBlank()) continue
            val descUp = desc.sinAcentos()
            if (descUp.contains("TOTAL") || descUp.startsWith("BALANCE")) break

            val fecha = textoCelda(row.getCell(iFecha)).let { parseFecha(it) }
            if (fecha == null) { ignorados++; continue }

            val montoLocal = textoCelda(row.getCell(iLocal)).toBigDecimalSafe() ?: BigDecimal.ZERO
            val montoUsd   = textoCelda(row.getCell(iUsd)).toBigDecimalSafe() ?: BigDecimal.ZERO

            // Una fila es válida si tiene monto en alguna de las dos monedas.
            if (montoLocal <= BigDecimal.ZERO && montoUsd <= BigDecimal.ZERO) { ignorados++; continue }

            val descNorm = desc.normalizarDescripcion()
            val tipo = when {
                descUp.contains("PAGO") && !descUp.contains("PAGOS EN") -> TipoMovimiento.PAGO_TARJETA
                descUp.contains("INTERES")                              -> TipoMovimiento.INTERES
                descUp.contains("COMISION")                             -> TipoMovimiento.COMISION
                descUp.contains("CASHBACK") || descUp.contains("REBATE") ||
                    descUp.contains("RECOMPENSA")                       -> TipoMovimiento.CASHBACK
                descUp.contains("DEVOLUCION") || descUp.contains("REVERSO") ||
                    descUp.contains("ABONO")                            -> TipoMovimiento.DEVOLUCION
                else                                                    -> TipoMovimiento.GASTO
            }

            movimientos.add(MovimientoNormalizado(
                fechaTransaccion = fecha,
                fechaPosteo = null,
                descripcionOriginal = desc,
                descripcionNormalizada = descNorm,
                descripcionCorta = descNorm.take(40),
                monto = montoLocal,
                montoUsd = montoUsd.takeIf { it > BigDecimal.ZERO },
                tipo = tipo,
                moneda = Moneda.DOP,
                balancePosterior = null,
                referencia = iAuth?.let { textoCelda(row.getCell(it)).takeIf { s -> s.isNotBlank() } },
                metadata = mapOf("fila" to rowIdx.toString()),
            ))
        }
        if (ignorados > 0) advertencias.add("$ignorados movimiento(s) ignorados.")
        return Triple(movimientos, advertencias, ignorados)
    }
}
