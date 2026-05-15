package com.example.flowtrack.data.parsers.popular

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
 *
 * Señales: 0.5 "POPULAR" en cabecera + 0.3 columnas Débito/Crédito + 0.2 formato fecha
 */
class PopularCsvParser @Inject constructor() : BankParser {

    override val codigoBanco: String = "POPULAR"
    override val tipoDocumento: TipoDocumento = TipoDocumento.CUENTA_CORRIENTE
    override val version: Int = 1
    override val formatosArchivo: Set<String> = setOf("csv")

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    )

    override suspend fun puedeManejar(archivo: ArchivoEntrada): ConfianzaDeteccion {
        if (archivo.extension != "csv") return ConfianzaDeteccion(0f, "No es CSV")
        return try {
            val texto = String(archivo.bytes, Charsets.UTF_8).trimStart('\uFEFF').uppercase()
            var confianza = 0f
            val razones = mutableListOf<String>()
            val pistas = mutableMapOf<String, String>()

            if (texto.contains("BANCO POPULAR") || texto.contains("BPRD")) {
                confianza += 0.5f; razones.add("texto 'Banco Popular'"); pistas["marca"] = "POPULAR"
            }
            val primeras = texto.lines().take(5).joinToString(" ")
            if ((primeras.contains("DEBITO") || primeras.contains("DÉBITO")) &&
                (primeras.contains("CREDITO") || primeras.contains("CRÉDITO"))
            ) { confianza += 0.3f; razones.add("columnas Débito/Crédito") }

            if (Regex("""\d{2}/\d{2}/\d{4}""").containsMatchIn(texto.lines().take(10).joinToString(" "))) {
                confianza += 0.2f; razones.add("formato fecha")
            }
            ConfianzaDeteccion(confianza.coerceAtMost(1f), razones.joinToString(", ").ifEmpty { "Sin señales" }, pistas)
        } catch (e: Exception) { ConfianzaDeteccion(0f, "Error: ${e.message}") }
    }

    override suspend fun parsear(archivo: ArchivoEntrada, contexto: ContextoParseo): ResultadoParseo {
        return try {
            val texto = String(archivo.bytes, Charsets.UTF_8).trimStart('\uFEFF')
            val lineas = texto.lines()
            val headerIdx = lineas.indexOfFirst { l ->
                val u = l.uppercase()
                u.contains("FECHA") && (u.contains("DESCRIPCION") || u.contains("DESCRIPCIÓN"))
            }
            if (headerIdx == -1) return ResultadoParseo.Error("No se encontró el encabezado CSV de Popular.")

            var numeroCuenta = "DESCONOCIDA"; var titular = "TITULAR"; var moneda = Moneda.DOP
            lineas.take(headerIdx).forEach { l ->
                Regex("""(\d{8,12})""").find(l)?.let { numeroCuenta = it.value }
                Regex("""(?:Titular|Cliente|Nombre)[:\s]+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s,]+)""", RegexOption.IGNORE_CASE)
                    .find(l)?.groupValues?.getOrNull(1)?.trim()?.let { titular = it.take(60) }
                if (l.uppercase().contains("USD")) moneda = Moneda.USD
            }

            val cuenta = CuentaDetectada(numeroCuenta.takeLast(10), null, titular, TipoCuenta.CORRIENTE, moneda, null, null)
            val csvParser = CSVParserBuilder().withSeparator(',').build()
            val filas = CSVReaderBuilder(InputStreamReader(archivo.bytes.inputStream(), Charsets.UTF_8))
                .withSkipLines(headerIdx + 1).withCSVParser(csvParser).build().use { it.readAll() }

            val txs = mutableListOf<TransaccionNormalizada>(); val adv = mutableListOf<String>()
            var fallidas = 0
            for ((i, fila) in filas.withIndex()) {
                if (fila.size < 5 || fila.all { it.isBlank() }) continue
                val fechaStr = fila.getOrElse(0) { "" }.trim()
                val desc = fila.getOrElse(1) { "" }.trim()
                if (desc.isBlank()) continue
                val ref = fila.getOrElse(2) { "" }.trim().ifBlank { null }
                val debito = fila.getOrElse(3) { "" }.trim().toBigDecimalSafe()
                val credito = fila.getOrElse(4) { "" }.trim().toBigDecimalSafe()
                val balance = fila.getOrElse(5) { "" }.trim().toBigDecimalSafe()

                var fecha: LocalDate? = null
                for (fmt in dateFormats) {
                    try { fecha = LocalDate.parse(fechaStr, fmt); break } catch (_: Exception) {}
                }
                if (fecha == null) { fallidas++; continue }

                val monto: BigDecimal
                val tipo: TipoTransaccion
                when {
                    debito != null && debito > BigDecimal.ZERO -> { monto = debito; tipo = TipoTransaccion.DEBITO }
                    credito != null && credito > BigDecimal.ZERO -> { monto = credito; tipo = TipoTransaccion.CREDITO }
                    else -> { fallidas++; continue }
                }

                txs.add(TransaccionNormalizada(
                    fecha = fecha, fechaPosteo = null,
                    descripcionCorta = normalizarConceptoPopular(desc),
                    descripcionOriginal = desc, monto = monto, tipo = tipo,
                    moneda = moneda, balanceDespues = balance,
                    referencia = ref, serial = null,
                    metadataBanco = mapOf("fila" to i.toString()),
                ))
            }
            if (fallidas > 0) adv.add("$fallidas fila(s) ignoradas por datos inválidos.")

            val fechas = txs.map { it.fecha }
            val debitos = txs.filter { it.tipo == TipoTransaccion.DEBITO }
            val creditos = txs.filter { it.tipo == TipoTransaccion.CREDITO }
            val resumen = if (txs.isNotEmpty()) ResumenPeriodoDetectado(
                fechas.min(), fechas.max(), debitos.size, creditos.size,
                debitos.fold(BigDecimal.ZERO) { a, t -> a + t.monto },
                creditos.fold(BigDecimal.ZERO) { a, t -> a + t.monto },
                txs.lastOrNull()?.balanceDespues ?: BigDecimal.ZERO,
            ) else null

            ResultadoParseo.ExitoCuenta(cuenta, txs, resumen, adv)
        } catch (e: Exception) {
            ResultadoParseo.Error("Error al parsear CSV de Popular: ${e.message}", e)
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
