package com.example.flowtrack.data.parsers.banreservas

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.BankParser
import com.example.flowtrack.data.parsers.core.ConfianzaDeteccion
import com.example.flowtrack.data.parsers.core.ContextoParseo
import com.example.flowtrack.data.parsers.core.CuentaDetectada
import com.example.flowtrack.data.parsers.core.ResumenPeriodoDetectado
import com.example.flowtrack.data.parsers.core.ResultadoParseo
import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.model.TipoTransaccion
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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
 *
 * Señales de detección:
 *  - 0.4: contiene "BANRESERVAS" o "banreservas.com.do"
 *  - 0.4: contiene IBAN con regex ^DO\d{2}BRRD
 *  - 0.2: encabezados "Cheques y cargos" y "Depósitos y abonos" presentes
 *  Total posible: 1.0
 */
class BanReservasPdfParser @Inject constructor() : BankParser {

    override val codigoBanco: String = "BANRESERVAS"
    override val tipoDocumento: TipoDocumento = TipoDocumento.CUENTA_CORRIENTE
    override val version: Int = 1
    override val formatosArchivo: Set<String> = setOf("pdf")

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ibanRegex = Regex("""DO\d{2}BRRD\d+""")

    // ─── Detección ────────────────────────────────────────────────────────────

    override suspend fun puedeManejar(archivo: ArchivoEntrada): ConfianzaDeteccion {
        if (archivo.extension != "pdf") return ConfianzaDeteccion(0f, "No es PDF")

        return try {
            val texto = extraerTexto(archivo.bytes)
            val textoUpper = texto.uppercase()

            var confianza = 0f
            val pistas = mutableMapOf<String, String>()
            val razones = mutableListOf<String>()

            // Señal 1: texto de marca (0.4)
            if (textoUpper.contains("BANRESERVAS") || textoUpper.contains("BANRESERVAS.COM.DO")) {
                confianza += 0.4f
                razones.add("texto BANRESERVAS")
                pistas["marca"] = "BANRESERVAS"
            }

            // Señal 2: IBAN BanReservas (0.4)
            val ibanMatch = ibanRegex.find(texto)
            if (ibanMatch != null) {
                confianza += 0.4f
                razones.add("IBAN ${ibanMatch.value.take(12)}...")
                pistas["iban"] = ibanMatch.value
            }

            // Señal 3: encabezados de columnas (0.2)
            if (textoUpper.contains("CHEQUES Y CARGOS") || textoUpper.contains("CHEQUES Y CARGO")) {
                confianza += 0.1f
                razones.add("columna 'Cheques y cargos'")
            }
            if (textoUpper.contains("DEPÓSITOS Y ABONOS") || textoUpper.contains("DEPOSITOS Y ABONOS")) {
                confianza += 0.1f
                razones.add("columna 'Depósitos y abonos'")
            }

            ConfianzaDeteccion(
                confianza = confianza.coerceAtMost(1f),
                razon = razones.joinToString(", "),
                pistas = pistas,
            )
        } catch (e: Exception) {
            ConfianzaDeteccion(0f, "Error al leer PDF: ${e.message}")
        }
    }

    // ─── Parseo principal ─────────────────────────────────────────────────────

    override suspend fun parsear(archivo: ArchivoEntrada, contexto: ContextoParseo): ResultadoParseo {
        return try {
            val texto = extraerTexto(archivo.bytes)
            val lineas = texto.lines()

            val cuenta = extraerCuenta(texto) ?: return ResultadoParseo.Error(
                mensaje = "No se pudo extraer información de la cuenta del PDF.",
                recuperable = false,
            )

            val (transacciones, advertencias) = extraerTransacciones(lineas, contexto)
            val resumen = extraerResumen(lineas, transacciones)

            ResultadoParseo.ExitoCuenta(
                cuenta = cuenta,
                transacciones = transacciones,
                resumenPeriodo = resumen,
                advertencias = advertencias,
            )
        } catch (e: Exception) {
            ResultadoParseo.Error(
                mensaje = "Error al parsear PDF de BanReservas: ${e.message}",
                excepcion = e,
                recuperable = false,
            )
        }
    }

    // ─── Extracción de texto PDF ──────────────────────────────────────────────

    private fun extraerTexto(bytes: ByteArray): String {
        return PDDocument.load(bytes).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.getText(doc)
        }
    }

    // ─── Extracción de cuenta ─────────────────────────────────────────────────

    private fun extraerCuenta(texto: String): CuentaDetectada? {
        val iban = ibanRegex.find(texto)?.value

        // Buscar número de cuenta en patrones comunes del estado
        val numeroCuentaRegex = Regex("""(?:Cuenta|No\.?\s*Cuenta|Número de cuenta)[:\s]+(\d[\d\s\-]+)""", RegexOption.IGNORE_CASE)
        val numeroCuenta = numeroCuentaRegex.find(texto)?.groupValues?.getOrNull(1)?.trim()
            ?: iban?.takeLast(10)
            ?: "DESCONOCIDA"

        // Buscar titular
        val titularRegex = Regex("""(?:Titular|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]+)""", RegexOption.IGNORE_CASE)
        val titular = titularRegex.find(texto)?.groupValues?.getOrNull(1)?.trim()?.take(60) ?: "TITULAR"

        return CuentaDetectada(
            numeroCuenta = numeroCuenta.replace(Regex("[\\s\\-]"), "").takeLast(10),
            numeroCuentaCompleto = iban,
            titular = titular,
            tipoCuenta = TipoCuenta.CORRIENTE,
            moneda = Moneda.DOP, // BanReservas siempre DOP en este formato
            balanceAlCorte = null,
            balanceAnterior = null,
        )
    }

    // ─── Extracción de transacciones ──────────────────────────────────────────

    private fun extraerTransacciones(
        lineas: List<String>,
        contexto: ContextoParseo,
    ): Pair<List<TransaccionNormalizada>, List<String>> {
        val transacciones = mutableListOf<TransaccionNormalizada>()
        val advertencias = mutableListOf<String>()

        // Encontrar el índice del header de la tabla
        val headerIdx = lineas.indexOfFirst { linea ->
            val u = linea.uppercase()
            u.contains("FECHA") && (u.contains("REFERENCIA") || u.contains("CONCEPTO"))
        }

        if (headerIdx == -1) {
            advertencias.add("No se encontró el encabezado de la tabla de transacciones.")
            return Pair(emptyList(), advertencias)
        }

        // Detectar anchos de columna leyendo la línea de header
        // BanReservas usa formato de tabla de texto fijo con alineación por espacios
        val lineasTransaccion = lineas.subList(headerIdx + 1, lineas.size)

        // Mapa de referencia → índice de transacción (para vincular DGII)
        val referenciasIdx = mutableMapOf<String, Int>()

        for (linea in lineasTransaccion) {
            // Detener al llegar al resumen final
            val lineaUpper = linea.uppercase()
            if (lineaUpper.contains("CHEQUES PAGADOS:") ||
                lineaUpper.contains("TOTAL DÉBITOS") ||
                lineaUpper.contains("BALANCE ANTERIOR") && transacciones.isNotEmpty()
            ) break

            val tx = parsearLineaTransaccion(linea) ?: continue
            referenciasIdx[tx.referencia ?: ""] = transacciones.size
            transacciones.add(tx)
        }

        // Segunda pasada: vincular DGII con su padre
        val txConPadre = transacciones.mapIndexed { idx, tx ->
            if (tx.esDerivada && tx.referenciaPadre != null) {
                val padreIdx = referenciasIdx[tx.referenciaPadre]
                if (padreIdx != null && padreIdx < idx) tx else tx
            } else tx
        }

        return Pair(txConPadre, advertencias)
    }

    /**
     * Parsea una línea de la tabla de BanReservas.
     * Formato esperado (separado por espacios variables):
     *   dd/MM/yyyy  REFERENCIA  CONCEPTO...  MONTO_CHEQUE  MONTO_DEPOSITO  BALANCE
     *
     * Estrategia: buscar la fecha al inicio, luego los montos al final (BigDecimal),
     * y el texto del medio es el concepto.
     */
    private fun parsearLineaTransaccion(linea: String): TransaccionNormalizada? {
        val trimmed = linea.trim()
        if (trimmed.length < 20) return null

        // Intentar extraer fecha al inicio de la línea
        val fechaRegex = Regex("""^(\d{2}/\d{2}/\d{4})""")
        val fechaMatch = fechaRegex.find(trimmed) ?: return null
        val fecha = try {
            LocalDate.parse(fechaMatch.value, dateFormatter)
        } catch (e: Exception) {
            return null
        }

        // Tokens restantes después de la fecha
        val restante = trimmed.substring(fechaMatch.value.length).trim()
        val tokens = restante.split(Regex("\\s{2,}")) // separador de columnas: 2+ espacios

        if (tokens.size < 3) return null

        // Referencia: primer token (numérico largo)
        val referencia = tokens[0].trim().takeIf { it.isNotBlank() }

        // Balance: último token (siempre presente en BanReservas)
        val balanceStr = tokens.last().trim()
        val balance = balanceStr.toBigDecimalSafe()

        // Montos: penúltimo (depósitos) y antepenúltimo (cheques/cargos)
        // La columna que tiene valor define el tipo
        val montoChequesStr = if (tokens.size >= 3) tokens[tokens.size - 3].trim() else ""
        val montoDepositosStr = if (tokens.size >= 2) tokens[tokens.size - 2].trim() else ""

        val montoCheques = montoChequesStr.toBigDecimalSafe()
        val montoDepositos = montoDepositosStr.toBigDecimalSafe()

        // Concepto: tokens del medio (todo lo que no es fecha, ref ni montos)
        val conceptoTokens = when {
            tokens.size >= 4 -> tokens.subList(1, tokens.size - 3)
            tokens.size == 3 -> tokens.subList(1, 1)
            else -> emptyList()
        }
        val concepto = conceptoTokens.joinToString(" ").trim()
            .ifBlank { tokens.getOrElse(1) { "" }.trim() }

        if (concepto.isBlank()) return null

        // Determinar tipo y monto
        val (monto, tipo) = when {
            montoCheques != null && montoCheques > BigDecimal.ZERO ->
                Pair(montoCheques, TipoTransaccion.DEBITO)
            montoDepositos != null && montoDepositos > BigDecimal.ZERO ->
                Pair(montoDepositos, TipoTransaccion.CREDITO)
            else -> return null // línea sin monto válido
        }

        // Detectar DGII (transacción derivada)
        val conceptoUpper = concepto.uppercase()
        val esDGII = conceptoUpper.contains("COBRO IMP") &&
            (conceptoUpper.contains("0.15") || conceptoUpper.contains("DGII"))

        // Normalizar descripción corta
        val descripcionCorta = normalizarConcepto(concepto)

        return TransaccionNormalizada(
            fecha = fecha,
            fechaPosteo = null, // BanReservas no tiene fecha de posteo separada
            descripcionCorta = descripcionCorta,
            descripcionOriginal = concepto,
            monto = monto,
            tipo = tipo,
            moneda = Moneda.DOP,
            balanceDespues = balance,
            referencia = referencia,
            serial = null,
            esDerivada = esDGII,
            referenciaPadre = if (esDGII) referencia else null,
            metadataBanco = mapOf("lineaOriginal" to linea.trim()),
        )
    }

    // ─── Normalización de concepto ────────────────────────────────────────────

    /**
     * Mapea los conceptos crudos del banco a descripciones cortas normalizadas.
     * Tabla definida en el documento de modelado de datos §3.1.
     */
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
            else -> upper.take(40) // fallback: primeras 40 chars normalizadas
        }
    }

    // ─── Extracción de resumen ────────────────────────────────────────────────

    private fun extraerResumen(
        lineas: List<String>,
        transacciones: List<TransaccionNormalizada>,
    ): ResumenPeriodoDetectado? {
        if (transacciones.isEmpty()) return null

        val fechas = transacciones.map { it.fecha }
        val debitos = transacciones.filter { it.tipo == TipoTransaccion.DEBITO }
        val creditos = transacciones.filter { it.tipo == TipoTransaccion.CREDITO }
        val totalDebitos = debitos.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.monto }
        val totalCreditos = creditos.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.monto }
        val balanceFinal = transacciones.lastOrNull()?.balanceDespues ?: BigDecimal.ZERO

        return ResumenPeriodoDetectado(
            periodoInicio = fechas.min(),
            periodoFin = fechas.max(),
            cantidadDebitos = debitos.size,
            cantidadCreditos = creditos.size,
            totalDebitos = totalDebitos,
            totalCreditos = totalCreditos,
            balanceFinal = balanceFinal,
        )
    }
}
