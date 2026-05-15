package com.example.flowtrack.core.extensions

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// ─── Normalización de texto para matching ────────────────────────────────────

/**
 * Normaliza una descripción de transacción bancaria para comparación.
 * Convierte a mayúsculas, elimina acentos y caracteres especiales.
 * Resultado: solo A-Z, 0-9 y espacios simples.
 */
fun String.normalizarDescripcion(): String {
    return this
        .trim()
        .uppercase()
        .replace(Regex("[ÁÀÄÂ]"), "A")
        .replace(Regex("[ÉÈËÊ]"), "E")
        .replace(Regex("[ÍÌÏÎ]"), "I")
        .replace(Regex("[ÓÒÖÔ]"), "O")
        .replace(Regex("[ÚÙÜÛ]"), "U")
        .replace("Ñ", "N")
        .replace(Regex("[^A-Z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

// ─── Formateo de moneda ───────────────────────────────────────────────────────

fun formatMoney(
    amount: BigDecimal,
    moneda: com.example.flowtrack.domain.model.Moneda = com.example.flowtrack.domain.model.Moneda.DOP,
    withSign: Boolean = false,
    decimals: Int = 2,
): String {
    val prefix = when (moneda) {
        com.example.flowtrack.domain.model.Moneda.DOP -> "RD$"
        com.example.flowtrack.domain.model.Moneda.USD -> "US$"
    }
    val abs = amount.abs()
    val formatted = NumberFormat.getNumberInstance(Locale("es", "DO")).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(abs)

    return when {
        withSign && amount > BigDecimal.ZERO -> "+ $prefix $formatted"
        amount < BigDecimal.ZERO -> "- $prefix $formatted"
        else -> "$prefix $formatted"
    }
}

// ─── Formateo de fechas ───────────────────────────────────────────────────────

fun formatDate(date: LocalDate): String {
    val months = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    return "${date.dayOfMonth} ${months[date.monthValue - 1]}, ${date.year}"
}

fun formatDateRelative(date: LocalDate): String {
    val today = LocalDate.now(ZoneId.of("America/Santo_Domingo"))
    return when {
        date == today -> "Hoy"
        date == today.minusDays(1) -> "Ayer"
        else -> formatDate(date)
    }
}

// ─── Parsing seguro de BigDecimal ────────────────────────────────────────────

/**
 * Convierte un String con formato bancario a BigDecimal.
 * Maneja: comas como separador de miles, punto decimal, espacios y símbolo de moneda.
 * Ejemplo: "1,234.56" → BigDecimal("1234.56")
 * Ejemplo: "RD$ 42,850.00" → BigDecimal("42850.00")
 */
fun String.toBigDecimalSafe(): BigDecimal? {
    // Quitar prefijos de moneda (RD$, US$), comas y espacios
    val cleaned = this
        .replace("RD$", "").replace("US$", "")
        .replace(Regex("[,\\s]"), "")
        .trim()
    return try {
        if (cleaned.isEmpty()) null else BigDecimal(cleaned)
    } catch (e: NumberFormatException) {
        null
    }
}
