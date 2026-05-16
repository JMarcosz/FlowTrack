package com.example.flowtrack.data.parsers.popular

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
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.InputStreamReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parser CSV del Banco Popular Dominicano.
 * Columnas: Fecha | Descripción | Referencia | Débito | Crédito | Balance
 */
class PopularCsvParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("POPULAR", ProductoTipo.CUENTA, FileFormat.CSV)
    override val version: Int = 1

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    )

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto = String(request.archivo.bytes, Charsets.UTF_8).trimStart('﻿')
            val lineas = texto.lines()
            val headerIdx = lineas.indexOfFirst { l ->
                val u = l.uppercase()
                u.contains("FECHA") && (u.contains("DESCRIPCION") || u.contains("DESCRIPCIÓN"))
            }
            if (headerIdx == -1) return ParseResult.Error("No se encontró el encabezado CSV de Popular.")

            var numeroCuenta = "DESCONOCIDA"; var titular = "TITULAR"; var moneda = Moneda.DOP
            lineas.take(headerIdx).forEach { l ->
                Regex("""(\d{8,12})""").find(l)?.let { numeroCuenta = it.value }
                Regex("""(?:Titular|Cliente|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s,]+)""", RegexOption.IGNORE_CASE)
                    .find(l)?.groupValues?.getOrNull(1)?.trim()?.let { titular = it.take(60) }
                if (l.uppercase().contains("USD")) moneda = Moneda.USD
            }

            // Auto-detectar separador por la línea de encabezado
            val headerLinea = lineas[headerIdx]
            val commas = headerLinea.count { it == ',' }
            val semis = headerLinea.count { it == ';' }
            val tabs = headerLinea.count { it == '\t' }
            val separador = when {
                tabs > commas && tabs > semis -> '\t'
                semis > commas -> ';'
                else -> ','
            }
            val csvParser = CSVParserBuilder().withSeparator(separador).build()
            // Leer el header para resolver columnas por nombre (no por posición fija)
            val headerRow = CSVReaderBuilder(InputStreamReader(request.archivo.bytes.inputStream(), Charsets.UTF_8))
                .withSkipLines(headerIdx).withCSVParser(csvParser).build().use { it.readNext() }
                ?: return ParseResult.Error("No se pudo leer el encabezado CSV de Popular.")
            val headerNorm = headerRow.map { it.trim().uppercase().normalizarDescripcion() }
            fun colIdx(vararg names: String) = names.firstNotNullOfOrNull { n -> headerNorm.indexOfFirst { it.contains(n) }.takeIf { it >= 0 } } ?: -1
            val iDate  = colIdx("FECHA")
            val iDesc  = colIdx("DESCRIPCION", "CONCEPTO")
            val iRef   = colIdx("REFERENCIA", "REF")
            val iDebit = colIdx("DEBITO", "CARGO", "DEBIT")
            val iCredit = colIdx("CREDITO", "ABONO", "CREDIT")
            val iBalance = colIdx("BALANCE", "SALDO")
            if (iDate < 0 || iDesc < 0) return ParseResult.Error("Columnas FECHA/DESCRIPCION no encontradas en CSV de Popular.")

            val filas = CSVReaderBuilder(InputStreamReader(request.archivo.bytes.inputStream(), Charsets.UTF_8))
                .withSkipLines(headerIdx + 1).withCSVParser(csvParser).build().use { it.readAll() }

            val movimientos = mutableListOf<MovimientoNormalizado>()
            val advertencias = mutableListOf<String>()
            var ignorados = 0

            for ((i, fila) in filas.withIndex()) {
                if (fila.all { it.isBlank() }) continue
                val fechaStr = if (iDate >= 0) fila.getOrElse(iDate) { "" }.trim() else continue
                val desc = if (iDesc >= 0) fila.getOrElse(iDesc) { "" }.trim() else continue
                if (desc.isBlank()) continue
                val ref = if (iRef >= 0) fila.getOrElse(iRef) { "" }.trim().ifBlank { null } else null
                val debito = if (iDebit >= 0) fila.getOrElse(iDebit) { "" }.trim().toBigDecimalSafe() else null
                val credito = if (iCredit >= 0) fila.getOrElse(iCredit) { "" }.trim().toBigDecimalSafe() else null
                val balance = if (iBalance >= 0) fila.getOrElse(iBalance) { "" }.trim().toBigDecimalSafe() else null

                var fecha: LocalDate? = null
                for (fmt in dateFormats) {
                    try { fecha = LocalDate.parse(fechaStr, fmt); break } catch (_: Exception) {}
                }
                if (fecha == null) { ignorados++; continue }

                var monto: BigDecimal? = null
                var tipoMovimiento: TipoMovimiento? = null
                if (debito != null && debito > BigDecimal.ZERO) {
                    monto = debito
                    tipoMovimiento = clasificarGasto(desc)
                } else if (credito != null && credito > BigDecimal.ZERO) {
                    monto = credito
                    tipoMovimiento = TipoMovimiento.INGRESO
                }
                if (monto == null || tipoMovimiento == null) { ignorados++; continue }

                val descripcionCorta = normalizarConceptoPopular(desc)
                movimientos.add(MovimientoNormalizado(
                    fechaTransaccion = fecha,
                    fechaPosteo = null,
                    descripcionOriginal = desc,
                    descripcionNormalizada = desc.uppercase().normalizarDescripcion(),
                    descripcionCorta = descripcionCorta,
                    monto = monto,
                    tipo = tipoMovimiento,
                    moneda = moneda,
                    balancePosterior = balance,
                    referencia = ref,
                    metadata = mapOf("fila" to i.toString()),
                ))
            }
            if (ignorados > 0) advertencias.add("$ignorados fila(s) ignoradas por datos inválidos.")

            val estado = EstadoCuentaNormalizado(
                bancoCodigo = "POPULAR",
                productoTipo = ProductoTipo.CUENTA,
                productoId = numeroCuenta.takeLast(10),
                titular = titular,
                moneda = moneda,
                fechaInicio = movimientos.minOfOrNull { it.fechaTransaccion },
                fechaFin = movimientos.maxOfOrNull { it.fechaTransaccion },
                balanceInicial = null,
                balanceFinal = movimientos.lastOrNull()?.balancePosterior,
                movimientos = movimientos,
            )

            val report = ParseReport(
                parserId = "POPULAR_CSV_v$version",
                totalDetectado = movimientos.size + ignorados,
                totalImportado = movimientos.size,
                totalIgnorado = ignorados,
                warnings = advertencias,
                errors = emptyList(),
            )

            ParseResult.Success(estado, report)
        } catch (e: Exception) {
            ParseResult.Error("Error al parsear CSV de Popular: ${e.message}", e)
        }
    }

    private fun clasificarGasto(desc: String): TipoMovimiento {
        val u = desc.uppercase().normalizarDescripcion()
        return when {
            u.contains("RETIRO ATM") || u.contains("CAJERO") -> TipoMovimiento.RETIRO_ATM
            u.contains("COMISION") -> TipoMovimiento.COMISION
            u.contains("ACH") || (u.contains("TRANSFERENCIA") && u.contains("ENVIADA")) -> TipoMovimiento.TRANSFERENCIA
            else -> TipoMovimiento.GASTO
        }
    }

    private fun normalizarConceptoPopular(desc: String): String {
        val u = desc.uppercase().normalizarDescripcion()
        return when {
            u.contains("CONSUMO TARJETA") -> "CONSUMO POS"
            u.contains("RETIRO ATM") || u.contains("CAJERO") -> "RETIRO ATM"
            u.contains("TRANSFERENCIA") && u.contains("ENVIADA") -> "TRANSFERENCIA SALIENTE"
            u.contains("TRANSFERENCIA") && u.contains("RECIBIDA") -> "TRANSFERENCIA ENTRANTE"
            u.contains("NOMINA") || u.contains("SALARIO") -> "NOMINA"
            u.contains("PAGO SERVICIO") -> "PAGO SERVICIO"
            u.contains("ACH") -> "TRANSFERENCIA ACH"
            u.contains("CHEQUE") -> "PAGO CHEQUE"
            else -> u.take(40)
        }
    }
}
