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
 *
 * Formato real exportado por el banco (UTF-8, separador coma):
 *   Fecha Posteo, Descripción Corta, Monto Transacción, Balance, No. Referencia, No. Serial, Descripción
 *
 * No hay columnas separadas de débito/crédito — el tipo se infiere de "Descripción Corta":
 *   - Contiene "Crédito" → INGRESO
 *   - Contiene "ATM"    → RETIRO_ATM
 *   - Contiene "Débito" → gasto (clasificado por Descripción larga)
 *   - En blanco         → COMISION (impuestos DGII, cargos)
 *
 * El archivo puede contener dos secciones (créditos y débitos) cada una con su propio
 * encabezado; el segundo encabezado falla el parse de fecha y se descarta sin problema.
 */
class PopularCsvParser @Inject constructor() : BankStatementParser {

    override val key: ParserKey = ParserKey("POPULAR", ProductoTipo.CUENTA, FileFormat.CSV)
    override val version: Int = 1

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    )

    override suspend fun parse(request: ImportRequest): ParseResult {
        return try {
            val texto = String(request.archivo.bytes, Charsets.UTF_8).trimStart('﻿')
            val lineas = texto.lines()

            // Buscar la primera línea que sea el encabezado CSV
            val headerIdx = lineas.indexOfFirst { l ->
                val u = l.uppercase()
                u.contains("FECHA") && u.contains("MONTO")
            }
            if (headerIdx == -1) return ParseResult.Error("No se encontró el encabezado CSV de Popular (Fecha, Monto).")

            // Extraer metadatos del bloque de cabecera (antes del primer header)
            var numeroCuenta = "DESCONOCIDA"
            var titular = "TITULAR"
            var moneda = Moneda.DOP
            lineas.take(headerIdx).forEach { l ->
                Regex("""Cuenta:\s*([\d]+)""").find(l)?.groupValues?.getOrNull(1)?.let { numeroCuenta = it }
                if (l.uppercase().contains("USD")) moneda = Moneda.USD
            }

            // Auto-detectar separador por la línea de encabezado
            val headerLinea = lineas[headerIdx]
            val commas = headerLinea.count { it == ',' }
            val semis  = headerLinea.count { it == ';' }
            val tabs   = headerLinea.count { it == '\t' }
            val sep = when {
                tabs > commas && tabs > semis -> '\t'
                semis > commas -> ';'
                else -> ','
            }

            val csvParser = CSVParserBuilder().withSeparator(sep).build()

            // Leer header para resolver índices de columna por nombre
            val headerRow = CSVReaderBuilder(
                InputStreamReader(request.archivo.bytes.inputStream(), Charsets.UTF_8)
            ).withSkipLines(headerIdx).withCSVParser(csvParser).build().use { it.readNext() }
                ?: return ParseResult.Error("No se pudo leer el encabezado CSV de Popular.")

            val headerNorm = headerRow.map { it.trim().uppercase().normalizarDescripcion() }

            fun colIdx(vararg names: String): Int =
                names.firstNotNullOfOrNull { n ->
                    headerNorm.indexOfFirst { it.contains(n) }.takeIf { it >= 0 }
                } ?: -1

            val iDate     = colIdx("FECHA")
            val iDescCorta = colIdx("CORTA")   // "DESCRIPCION CORTA" — indica dirección del movimiento
            val iMonto    = colIdx("MONTO")    // "MONTO TRANSACCION" — columna única de importe
            val iBalance  = colIdx("BALANCE", "SALDO")
            val iRef      = colIdx("REFERENCIA", "REF")
            // Descripción larga: última columna que sea exactamente "DESCRIPCION"
            val iDescLarga = headerNorm.indexOfLast { it == "DESCRIPCION" }.takeIf { it >= 0 }
                ?: colIdx("DESCRIPCION")

            if (iDate < 0 || iMonto < 0) {
                return ParseResult.Error("Columnas FECHA o MONTO no encontradas en CSV de Popular.")
            }

            // Leer todas las filas de datos (incluye las dos secciones del archivo)
            val filas = CSVReaderBuilder(
                InputStreamReader(request.archivo.bytes.inputStream(), Charsets.UTF_8)
            ).withSkipLines(headerIdx + 1).withCSVParser(csvParser).build().use { it.readAll() }

            val movimientos = mutableListOf<MovimientoNormalizado>()
            val advertencias = mutableListOf<String>()
            var ignorados = 0

            for ((i, fila) in filas.withIndex()) {
                if (fila.all { it.isBlank() }) continue

                val fechaStr  = fila.getOrElse(iDate) { "" }.trim()
                val descCorta = if (iDescCorta >= 0) fila.getOrElse(iDescCorta) { "" }.trim() else ""
                val montoStr  = fila.getOrElse(iMonto) { "" }.trim()
                val descLarga = if (iDescLarga >= 0) fila.getOrElse(iDescLarga) { "" }.trim() else ""
                val balance   = if (iBalance >= 0) fila.getOrElse(iBalance) { "" }.trim().toBigDecimalSafe() else null
                val ref       = if (iRef >= 0) fila.getOrElse(iRef) { "" }.trim().ifBlank { null } else null

                // Ignorar líneas que son el segundo encabezado repetido
                if (fechaStr.uppercase().contains("FECHA")) { ignorados++; continue }
                if (montoStr.isBlank()) { ignorados++; continue }

                val monto = montoStr.toBigDecimalSafe()
                if (monto == null || monto <= BigDecimal.ZERO) { ignorados++; continue }

                var fecha: LocalDate? = null
                for (fmt in dateFormats) {
                    try { fecha = LocalDate.parse(fechaStr, fmt); break } catch (_: Exception) {}
                }
                if (fecha == null) { ignorados++; continue }

                // Determinar tipo de movimiento desde Descripción Corta
                val descCortaN = descCorta.uppercase().normalizarDescripcion()
                val descLargaN = descLarga.uppercase().normalizarDescripcion()

                val tipo = when {
                    descCortaN.contains("CREDITO") -> TipoMovimiento.INGRESO
                    descCortaN.contains("ATM") || descLargaN.contains("RET DE CHK") -> TipoMovimiento.RETIRO_ATM
                    descCortaN.contains("DEBITO") -> clasificarDebito(descLargaN)
                    descCorta.isBlank() -> clasificarSinTipo(descLargaN)
                    else -> TipoMovimiento.GASTO
                }

                val descripcionCorta = normalizarConceptoPopular(descCortaN, descLargaN)

                movimientos.add(
                    MovimientoNormalizado(
                        fechaTransaccion = fecha,
                        fechaPosteo = null,
                        descripcionOriginal = descLarga.ifBlank { descCorta },
                        descripcionNormalizada = descLargaN.ifBlank { descCortaN },
                        descripcionCorta = descripcionCorta,
                        monto = monto,
                        tipo = tipo,
                        moneda = moneda,
                        balancePosterior = balance,
                        referencia = ref,
                        metadata = mapOf("fila" to i.toString()),
                    )
                )
            }

            if (ignorados > 0) advertencias.add("$ignorados fila(s) ignoradas (encabezados repetidos, montos vacíos o fechas inválidas).")

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

    private fun clasificarDebito(descLargaN: String): TipoMovimiento = when {
        descLargaN.contains("COMISION") || descLargaN.contains("CARGO MENSUAL") -> TipoMovimiento.COMISION
        descLargaN.contains("LBTR") || descLargaN.contains("MB A ") ||
            descLargaN.contains("PAGO ACH") || descLargaN.contains("ACH ") -> TipoMovimiento.TRANSFERENCIA
        descLargaN.contains("PAG ") || descLargaN.contains("PAGO ") -> TipoMovimiento.GASTO
        else -> TipoMovimiento.GASTO
    }

    private fun clasificarSinTipo(descLargaN: String): TipoMovimiento = when {
        descLargaN.contains("PAGO IMPUESTO") || descLargaN.contains("DGII") -> TipoMovimiento.COMISION
        descLargaN.contains("CARGO") || descLargaN.contains("COMISION") -> TipoMovimiento.COMISION
        else -> TipoMovimiento.GASTO
    }

    private fun normalizarConceptoPopular(descCortaN: String, descLargaN: String): String {
        return when {
            descCortaN.contains("CREDITO") -> when {
                descLargaN.contains("MB DESDE") -> "TRANSFERENCIA RECIBIDA"
                descLargaN.contains("DEPOSITO CHEQUE") || descLargaN.contains("DEPOSITO EN EFECTIVO") -> "DEPOSITO"
                descLargaN.contains("DEPOSITO EN SUBAGENTE") || descLargaN.contains("DEPOSITO SUBAGENTE") -> "DEPOSITO SUBAGENTE"
                descLargaN.contains("LBTR") -> "TRANSFERENCIA LBTR RECIBIDA"
                descLargaN.contains("COD CASH") -> "DEPOSITO CODIGO"
                else -> "CREDITO"
            }
            descCortaN.contains("ATM") || descLargaN.contains("RET DE CHK") -> "RETIRO ATM"
            descLargaN.contains("LBTR") -> "TRANSFERENCIA LBTR"
            descLargaN.contains("MB A ") -> "TRANSFERENCIA ENVIADA"
            descLargaN.contains("PAGO ACH") || descLargaN.contains("ACH ") -> "TRANSFERENCIA ACH"
            descLargaN.contains("PAGO IMPUESTO") || descLargaN.contains("DGII") -> "IMPUESTO DGII"
            descLargaN.contains("CARGO MENSUAL") -> "CARGO MENSUAL TD"
            descLargaN.contains("PAG ") || descLargaN.contains("PAGO ") -> {
                val servicio = Regex("""PAG\s+(\w+)""").find(descLargaN)?.groupValues?.getOrNull(1)
                    ?: Regex("""PAGO\s+(\w+)""").find(descLargaN)?.groupValues?.getOrNull(1)
                    ?: "SERVICIO"
                "PAGO $servicio".take(40)
            }
            else -> descLargaN.take(40).ifBlank { descCortaN.take(40) }
        }
    }
}
