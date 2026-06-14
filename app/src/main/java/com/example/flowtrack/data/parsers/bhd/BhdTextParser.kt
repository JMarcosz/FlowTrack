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
 * Soporta versiones v1 (año completo en movimientos) y v2 (año inferido del corte).
 */
internal class BhdTextParser {

    private val dateFormatterV1 = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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

        // Para v2 necesitamos llevar el balance para inferir tipos si no hay columnas claras
        var balanceRastreado = metadata.balanceInicial ?: BigDecimal.ZERO

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

            val parsed = parseMovimiento(lineas, index, metadata, balanceRastreado)
            if (parsed == null) {
                ignorados++
                index++
                continue
            }

            movimientos += parsed.movimiento
            index = parsed.siguienteIndex
            
            // Actualizar balance rastreado si el movimiento tiene balance posterior
            parsed.movimiento.balancePosterior?.let {
                balanceRastreado = it
            }
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
                "versionDetectada" to if (metadata.isV2) "v2" else "v1"
            ),
        )

        val report = ParseReport(
            parserId = if (metadata.isV2) "BHD_PDF_v2" else "BHD_PDF_v1",
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
        return (bloque.contains("ESTADO DE CUENTA") || bloque.contains("BHD")) &&
            (bloque.contains("BHD") || bloque.contains("BANCO MULTIPLE")) &&
            bloque.contains("FECHA") &&
            (bloque.contains("DETALLE") || bloque.contains("DETALLES")) &&
            bloque.contains("BALANCE")
    }

    private fun encontrarEncabezado(lineas: List<String>): Int? {
        for (index in lineas.indices) {
            val linea = lineas[index].uppercase().normalizarDescripcion()
            if (
                linea.contains("FECHA") &&
                linea.contains("REF") &&
                (linea.contains("DETALLE") || linea.contains("DETALLES")) &&
                (linea.contains("DEBITO") || linea.contains("DEBITOS")) &&
                (linea.contains("CREDITO") || linea.contains("CREDITOS")) &&
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
        metadata: MetadataBhd,
        balanceAnterior: BigDecimal
    ): MovimientoParseado? {
        val linea = lineas.getOrNull(startIndex) ?: return null

        // --- Intento v1 (Single Line con Año) ---
        val regexV1 = Regex("""^(\d{2}/\d{2}/\d{4})\s+(\S+)\s+(.+?)\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})\s+((?:\$|RD\$|US\$|)?[-0-9,]+\.\d{2})$""")
        val matchV1 = regexV1.find(linea)

        if (matchV1 != null) {
            val (fechaStr, referencia, detalle, debitoStr, creditoStr, balanceStr) = matchV1.destructured
            val fecha = parseFechaV1(fechaStr) ?: return null
            val debito = parseMonto(debitoStr) ?: BigDecimal.ZERO
            val credito = parseMonto(creditoStr) ?: BigDecimal.ZERO
            val balance = parseMonto(balanceStr) ?: BigDecimal.ZERO

            val monto = if (credito > BigDecimal.ZERO) credito else debito
            val tipo = inferirTipo(detalle, debito, credito)

            return MovimientoParseado(
                movimiento = crearMovimiento(fecha, detalle, monto, tipo, metadata.moneda, balance, referencia, debito, credito),
                siguienteIndex = startIndex + 1
            )
        }

        // --- Intento v2 (Single Line sin Año) ---
        // Formato esperado: {dd/MM} {Ref} {Detalle...} {Monto} {Balance}
        // O: {dd/MM} {Detalle...} {Monto} {Balance}
        // Usamos una regex que busca los dos montos al final.
        val regexV2 = Regex("""^(\d{2}/\d{2})\s+(.*?)\s+([-0-9,]+\.\d{2})\s+([-0-9,]+\.\d{2})$""")
        val matchV2 = regexV2.find(linea)

        if (matchV2 != null) {
            val (fechaStr, resto, montoStr, balanceStr) = matchV2.destructured
            val fecha = parseFechaV2(fechaStr, metadata.añoReferencia) ?: return null
            val monto = parseMonto(montoStr) ?: BigDecimal.ZERO
            val balance = parseMonto(balanceStr) ?: BigDecimal.ZERO
            
            // Separar referencia de detalle si existe (primera palabra si parece alfanumérica larga)
            val partesResto = resto.split(Regex("\\s+"), 2)
            val referencia = if (partesResto.size > 1 && partesResto[0].length >= 8 && partesResto[0].any { it.isDigit() }) partesResto[0] else ""
            val detalle = if (referencia.isNotEmpty()) partesResto[1] else resto

            // Inferencia de tipo por balance
            val diff = balance - balanceAnterior
            val tipo = when {
                diff.compareTo(monto) == 0 -> TipoMovimiento.INGRESO
                diff.compareTo(monto.negate()) == 0 -> TipoMovimiento.GASTO
                else -> inferirTipo(detalle, if (diff < BigDecimal.ZERO) monto else BigDecimal.ZERO, if (diff > BigDecimal.ZERO) monto else BigDecimal.ZERO)
            }

            return MovimientoParseado(
                movimiento = crearMovimiento(fecha, detalle, monto, tipo, metadata.moneda, balance, referencia),
                siguienteIndex = startIndex + 1
            )
        }

        // Fallback a multilinea
        return parseMultilinea(lineas, startIndex, metadata, balanceAnterior)
    }

    private fun parseMultilinea(lineas: List<String>, startIndex: Int, metadata: MetadataBhd, balanceAnterior: BigDecimal): MovimientoParseado? {
        val linea = lineas[startIndex]
        val fecha = parseFechaV1(linea.take(10)) ?: parseFechaV2(linea.take(5), metadata.añoReferencia) ?: return null
        
        val referencia = lineas.getOrNull(startIndex + 1)
            ?.takeIf { !esMoneyLine(it) && !esFecha(it) }
            ?.trim()
            ?: ""

        val inicioDetalle = startIndex + (if (referencia.isNotEmpty()) 2 else 1)
        val bloque = lineas.drop(inicioDetalle).take(MAX_LINEAS_POR_MOVIMIENTO)
        val inicioMontos = bloque.indexOfFirst { parseMonto(it) != null }
        if (inicioMontos == -1) return null
        
        val detalle = bloque.take(inicioMontos).joinToString(" ").trim()
        val lineasMonto = bloque.drop(inicioMontos).take(3)
        val montos = lineasMonto.mapNotNull { parseMonto(it) }
        
        if (montos.size < 2) return null
        
        val balance = montos.last()
        var debito = BigDecimal.ZERO
        var credito = BigDecimal.ZERO
        
        if (montos.size == 3) {
            debito = montos[0]
            credito = montos[1]
        } else {
            // Solo 2 montos: inferir cual es cual
            val montoReal = montos[0]
            val diff = balance - balanceAnterior
            if (diff.compareTo(montoReal) == 0) credito = montoReal
            else debito = montoReal
        }
        
        val monto = if (credito > BigDecimal.ZERO) credito else debito
        val tipo = inferirTipo(detalle, debito, credito)

        return MovimientoParseado(
            movimiento = crearMovimiento(fecha, detalle, monto, tipo, metadata.moneda, balance, referencia, debito, credito),
            siguienteIndex = inicioDetalle + inicioMontos + lineasMonto.size
        )
    }

    private fun crearMovimiento(
        fecha: LocalDate,
        detalle: String,
        monto: BigDecimal,
        tipo: TipoMovimiento,
        moneda: Moneda,
        balance: BigDecimal,
        referencia: String,
        debito: BigDecimal = BigDecimal.ZERO,
        credito: BigDecimal = BigDecimal.ZERO
    ) = MovimientoNormalizado(
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
    )

    private fun inferirTipo(
        detalle: String,
        debito: BigDecimal,
        credito: BigDecimal,
    ): TipoMovimiento {
        val normalizada = detalle.uppercase().normalizarDescripcion()
        return when {
            credito > BigDecimal.ZERO -> TipoMovimiento.INGRESO
            normalizada.contains("TRANSFERENCIA RECIBIDA") -> TipoMovimiento.INGRESO
            normalizada.contains("DEPOSITO") -> TipoMovimiento.INGRESO
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
        
        // Detectar año de corte (v2 usa yyyy-MM-dd)
        val fechaCorteStr = extraerValorDespuesDeEtiqueta(lineas, "Fecha de corte")
        val añoReferencia = if (fechaCorteStr?.matches(Regex("""\d{4}-\d{2}-\d{2}""")) == true) {
            fechaCorteStr.take(4).toInt()
        } else {
            LocalDate.now().year
        }

        val isV2 = (fechaCorteStr?.matches(Regex("""\d{4}-\d{2}-\d{2}""")) == true) || 
                   lineas.any { it.uppercase().contains("DETALLES") }

        return MetadataBhd(
            titular = titular,
            numeroCuenta = numeroCuenta,
            numeroCuentaRegional = numeroCuentaRegional,
            moneda = moneda,
            balanceInicial = balanceInicial,
            balanceFinal = balanceFinal,
            añoReferencia = añoReferencia,
            isV2 = isV2
        )
    }

    private fun extraerTitular(lineas: List<String>): String {
        val idxRnc = lineas.indexOfFirst { linea ->
            linea.uppercase().normalizarDescripcion() == "RNC"
        }
        if (idxRnc >= 0) {
            return limpiarTitular(lineas.getOrNull(idxRnc + 1) ?: "TITULAR")
        }

        val idxDocumento = lineas.indexOfFirst { linea ->
            linea.uppercase().normalizarDescripcion().contains("DOCUMENTO EMITIDO POR EL")
        }
        if (idxDocumento > 0) {
            return limpiarTitular(lineas.getOrNull(idxDocumento - 6) ?: lineas.getOrNull(idxDocumento - 1) ?: "TITULAR")
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
            linea.uppercase().normalizarDescripcion().contains(etiquetaNormalizada)
        }
        if (idx == -1) return null
        
        // En v2 la etiqueta y el valor pueden estar en la misma linea o el valor en la siguiente
        val linea = lineas[idx]
        if (linea.contains("|")) {
             val partes = linea.split("|")
             val labelIdx = partes.indexOfFirst { it.uppercase().normalizarDescripcion().contains(etiquetaNormalizada) }
             if (labelIdx != -1 && labelIdx < partes.size - 1) {
                 return partes[labelIdx + 1].trim()
             }
        }
        
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

        // En v2 el monto suele estar en la linea de abajo
        parseMonto(lineas[idx])?.let { return it }

        return lineas.drop(idx + 1)
            .take(3)
            .firstNotNullOfOrNull { linea ->
                parseMonto(linea)
            }
    }

    private fun parseFechaV1(valor: String): LocalDate? =
        runCatching { LocalDate.parse(valor.trim().take(10), dateFormatterV1) }.getOrNull()

    private fun parseFechaV2(valor: String, año: Int): LocalDate? =
        runCatching { LocalDate.parse("${valor.trim().take(5)}/$año", dateFormatterV1) }.getOrNull()

    private fun parseMonto(valor: String): BigDecimal? =
        valor.trim().toBigDecimalSafe()
            ?: valor.trim().removePrefix("$").toBigDecimalSafe()
            ?: valor.trim().replace(",", "").toBigDecimalSafe()

    private fun esFecha(valor: String): Boolean =
        valor.trim().matches(Regex("""\d{2}/\d{2}(/\d{4})?.*"""))

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
        val añoReferencia: Int,
        val isV2: Boolean
    )

    private companion object {
        const val MAX_LINEAS_POR_MOVIMIENTO = 12
    }
}
