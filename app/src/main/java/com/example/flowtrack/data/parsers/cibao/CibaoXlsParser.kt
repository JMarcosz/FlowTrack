package com.example.flowtrack.data.parsers.cibao

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
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parser XLS de Asociación Cibao (tarjeta de crédito).
 *
 * Nota: Solo soporta formato .xls (HSSF/Biff8) que es el que genera Cibao.
 * Si en el futuro cambian a .xlsx se deberá agregar poi-ooxml al build.
 *
 * Señales de detección:
 *   0.6 → hoja Excel contiene "CIBAO" o "ASOCIACION CIBAO"
 *   0.3 → columnas Fecha/Descripción
 *   0.1 → extensión .xls
 */
class CibaoXlsParser @Inject constructor() : BankParser {

    override val codigoBanco: String = "CIBAO"
    override val tipoDocumento: TipoDocumento = TipoDocumento.TARJETA_CREDITO
    override val version: Int = 1
    override val formatosArchivo: Set<String> = setOf("xls")   // solo HSSF en MVP

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ZONA = ZoneId.of("America/Santo_Domingo")

    override suspend fun puedeManejar(archivo: ArchivoEntrada): ConfianzaDeteccion {
        if (archivo.extension != "xls") return ConfianzaDeteccion(0f, "No es XLS")
        return try {
            val wb = abrirWorkbook(archivo) ?: return ConfianzaDeteccion(0f, "No se pudo leer el XLS")
            var confianza = 0f
            val razones = mutableListOf<String>()
            val pistas = mutableMapOf<String, String>()

            val hoja = wb.getSheetAt(0)
            val textoHoja = textoDeHoja(hoja, 20)

            if (textoHoja.contains("CIBAO") || textoHoja.contains("ASOCIACION CIBAO")) {
                confianza += 0.6f; razones.add("texto 'CIBAO'"); pistas["marca"] = "CIBAO"
            }
            if (textoHoja.contains("FECHA") && textoHoja.contains("DESCRIPCION")) {
                confianza += 0.3f; razones.add("columnas Fecha/Descripción")
            }
            confianza += 0.1f; razones.add("formato XLS")

            wb.close()
            ConfianzaDeteccion(confianza.coerceAtMost(1f), razones.joinToString(", "), pistas)
        } catch (e: Exception) { ConfianzaDeteccion(0f, "Error: ${e.message}") }
    }

    override suspend fun parsear(archivo: ArchivoEntrada, contexto: ContextoParseo): ResultadoParseo {
        return try {
            val wb = abrirWorkbook(archivo)
                ?: return ResultadoParseo.Error("No se pudo abrir el XLS de Cibao.")
            val hoja = wb.getSheetAt(0)

            val tarjeta = extraerTarjeta(hoja)
                ?: return ResultadoParseo.Error("No se pudo extraer información de tarjeta Cibao.")
            val estadoTarjeta = extraerEstadoTarjeta(hoja)
                ?: return ResultadoParseo.Error("No se pudo extraer estado de corte Cibao.")

            val headerIdx = encontrarHeader(hoja)
                ?: return ResultadoParseo.Error("No se encontró el encabezado de movimientos.")

            val (movimientos, advertencias) = extraerMovimientos(hoja, headerIdx)
            wb.close()
            ResultadoParseo.ExitoTarjeta(tarjeta, estadoTarjeta, movimientos, advertencias)
        } catch (e: Exception) {
            ResultadoParseo.Error("Error al parsear XLS de Cibao: ${e.message}", e)
        }
    }

    private fun abrirWorkbook(archivo: ArchivoEntrada): Workbook? = try {
        HSSFWorkbook(archivo.bytes.inputStream())
    } catch (_: Exception) { null }

    private fun textoDeHoja(hoja: Sheet, maxFilas: Int): String =
        (0 until minOf(maxFilas, hoja.lastRowNum + 1))
            .mapNotNull { hoja.getRow(it) }
            .flatMap { row -> (0 until row.lastCellNum).map { row.getCell(it)?.toString() ?: "" } }
            .joinToString(" ").uppercase()

    private fun extraerTarjeta(hoja: Sheet): TarjetaDetectada? {
        val texto = textoDeHoja(hoja, 15)
        val ultimos4 = Regex("""(?:\*{4}|\d{4}[\s-]\d{4}[\s-]\d{4}[\s-])(\d{4})""")
            .find(texto)?.groupValues?.getOrNull(1) ?: return null
        val titular = Regex("""(?:Titular|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\s]+)""")
            .find(texto)?.groupValues?.getOrNull(1)?.trim() ?: "TITULAR"
        val limite = Regex("""(?:Límite|Limite)[:\s]+(?:RD\$?\s*)?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO
        return TarjetaDetectada(ultimos4, titular.take(60), null, limite, Moneda.DOP, null, null, 60.0)
    }

    private fun extraerEstadoTarjeta(hoja: Sheet): EstadoTarjetaDetectado? {
        val texto = textoDeHoja(hoja, 20)
        fun monto(pat: String) = Regex("""$pat[:\s]+(?:RD\$?\s*)?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)?.toBigDecimalSafe() ?: BigDecimal.ZERO
        fun fecha(pat: String) = Regex("""$pat[:\s]+(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.getOrNull(1)
            ?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }

        val corte = fecha("Fecha de corte") ?: LocalDate.now()
        return EstadoTarjetaDetectado(
            fechaCorte = corte, fechaLimitePago = fecha("Fecha.*pago") ?: corte.plusDays(25),
            balanceAlCorte = monto("Balance al corte|Saldo"),
            balanceAnterior = monto("Balance anterior").takeIf { it > BigDecimal.ZERO },
            pagoMinimo = monto("Pago m[ií]nimo"),
            pagoTotal = monto("Pago total"),
            montoVencido = BigDecimal.ZERO, balancePromedioDiario = null,
            interesPorFinanciamiento = null, cashbackGanado = null,
        )
    }

    private fun encontrarHeader(hoja: Sheet): Int? =
        (0..minOf(15, hoja.lastRowNum)).firstOrNull { idx ->
            val row = hoja.getRow(idx) ?: return@firstOrNull false
            val texto = (0 until row.lastCellNum).joinToString(" ") { row.getCell(it)?.toString() ?: "" }.uppercase()
            texto.contains("FECHA") && texto.contains("DESCRIPCION")
        }

    private fun extraerMovimientos(hoja: Sheet, headerIdx: Int): Pair<List<MovimientoTarjetaNormalizado>, List<String>> {
        val movimientos = mutableListOf<MovimientoTarjetaNormalizado>()
        val advertencias = mutableListOf<String>()
        var fallidas = 0

        for (rowIdx in (headerIdx + 1)..hoja.lastRowNum) {
            val row = hoja.getRow(rowIdx) ?: continue
            val celdas = (0 until row.lastCellNum).map { row.getCell(it) }
            if (celdas.all { it == null || it.toString().isBlank() }) continue

            val celdaFecha = celdas.getOrNull(0)
            val desc = celdas.getOrNull(1)?.toString()?.trim() ?: continue
            if (desc.isBlank()) continue
            if (desc.uppercase().contains("TOTAL") || desc.uppercase().contains("BALANCE")) break

            val debitoStr = celdas.getOrNull(2)?.toString()?.trim() ?: ""
            val creditoStr = celdas.getOrNull(3)?.toString()?.trim() ?: ""

            var fechaParseada: LocalDate? = null
            when {
                celdaFecha?.cellType == CellType.NUMERIC -> {
                    try {
                        fechaParseada = DateUtil.getJavaDate(celdaFecha.numericCellValue)
                            .toInstant().atZone(ZONA).toLocalDate()
                    } catch (_: Exception) {}
                }
                else -> {
                    val fechaStr = celdaFecha?.toString()?.trim() ?: ""
                    fechaParseada = runCatching {
                        LocalDate.parse(fechaStr, dateFormatter)
                    }.getOrNull()
                }
            }

            if (fechaParseada == null) {
                fallidas++
                continue
            }
            val fecha: LocalDate = fechaParseada

            val debito = debitoStr.toBigDecimalSafe()
            val credito = creditoStr.toBigDecimalSafe()
            var montoRaw: BigDecimal? = null
            var tipoBaseRaw: TipoMovimientoTarjeta? = null
            if (debito != null && debito > BigDecimal.ZERO) {
                montoRaw = debito
                tipoBaseRaw = TipoMovimientoTarjeta.COMPRA
            } else if (credito != null && credito > BigDecimal.ZERO) {
                montoRaw = credito
                tipoBaseRaw = TipoMovimientoTarjeta.PAGO
            }
            
            if (montoRaw == null || tipoBaseRaw == null) {
                fallidas++
                continue
            }
            
            val monto: BigDecimal = montoRaw
            val tipoBase: TipoMovimientoTarjeta = tipoBaseRaw

            val descNorm = desc.uppercase().normalizarDescripcion()
            val tipo = when {
                descNorm.contains("PAGO") -> TipoMovimientoTarjeta.PAGO
                descNorm.contains("INTERES") -> TipoMovimientoTarjeta.INTERES
                descNorm.contains("COMISION") -> TipoMovimientoTarjeta.COMISION
                else -> tipoBase
            }

            movimientos.add(MovimientoTarjetaNormalizado(
                fechaTransaccion = fecha, fechaPosteo = null,
                descripcionOriginal = desc, monto = monto,
                tipoMovimiento = tipo, moneda = Moneda.DOP,
                numeroAutorizacion = celdas.getOrNull(4)?.toString()?.trim(),
                metadataBanco = mapOf("fila" to rowIdx.toString()),
            ))
        }
        if (fallidas > 0) advertencias.add("$fallidas movimiento(s) ignorados.")
        return Pair(movimientos, advertencias)
    }
}
