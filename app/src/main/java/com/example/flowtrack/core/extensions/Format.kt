package com.example.flowtrack.core.extensions

import com.example.flowtrack.domain.model.Moneda
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

// ─── Formateo según preferencias del usuario (ConfiguracionUsuario) ───────────

private val ZONA_RD: ZoneId = ZoneId.of("America/Santo_Domingo")

/**
 * Formatea una fecha usando el patrón configurado por el usuario (ej. "dd/MM/yyyy",
 * "yyyy-MM-dd"). Si el patrón es inválido, cae al formato largo en español.
 */
fun formatearFecha(date: LocalDate, patron: String): String =
    runCatching { date.format(DateTimeFormatter.ofPattern(patron)) }.getOrElse { formatDate(date) }

fun formatearFecha(instant: Instant, patron: String, zona: ZoneId = ZONA_RD): String =
    formatearFecha(instant.atZone(zona).toLocalDate(), patron)

/**
 * Formatea un monto según el preset de moneda configurado. Presets soportados:
 *   "RD$ 0.00"  → prefijo con espacio   (RD$ 1,234.56)
 *   "0.00 RD$"  → sufijo                (1,234.56 RD$)
 *   "$0.00"     → prefijo sin espacio   (RD$1,234.56)
 * El símbolo concreto (RD$/US$) lo determina [moneda]; [formato] solo controla la disposición.
 */
fun formatearMoneda(
    amount: BigDecimal,
    moneda: Moneda,
    formato: String = "RD$ 0.00",
    withSign: Boolean = false,
    decimals: Int = 2,
): String {
    val simbolo = when (moneda) {
        Moneda.DOP -> "RD$"
        Moneda.USD -> "US$"
    }
    val cuerpo = NumberFormat.getNumberInstance(Locale("es", "DO")).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(amount.abs())

    val f = formato.trim()
    val idxNum = f.indexOfFirst { it.isDigit() }
    val idxSym = f.indexOf("$")
    val conEspacio = f.contains(" ")
    val sufijo = idxSym >= 0 && idxNum >= 0 && idxSym > idxNum
    val base = if (sufijo) {
        if (conEspacio) "$cuerpo $simbolo" else "$cuerpo$simbolo"
    } else {
        if (conEspacio) "$simbolo $cuerpo" else "$simbolo$cuerpo"
    }

    val signo = when {
        withSign && amount > BigDecimal.ZERO -> "+ "
        amount < BigDecimal.ZERO -> "- "
        else -> ""
    }
    return "$signo$base"
}

// ─── Parsing seguro de BigDecimal ────────────────────────────────────────────

/**
 * Convierte un String con formato bancario a BigDecimal.
 * Soporta formato US (1,234.56) y europeo (1.234,56), prefijos RD$/US$,
 * paréntesis contables (1,234.56) → negativo, y signos negativos explícitos.
 *
 * Ejemplos:
 *   "1,234.56"      → 1234.56
 *   "1.234,56"      → 1234.56
 *   "(1,234.56)"    → -1234.56
 *   "RD$ 42,850.00" → 42850.00
 */
fun String.toBigDecimalSafe(): BigDecimal? {
    var s = this
        .replace("RD\$", "").replace("US\$", "")
        .replace("RD$", "").replace("US$", "")
        .trim()

    val negativo = s.startsWith("(") && s.endsWith(")")
    if (negativo) s = s.removePrefix("(").removeSuffix(")")
    val negSign = s.startsWith("-")
    if (negSign) s = s.removePrefix("-").trim()

    // Detectar formato europeo: termina en ",XX" (coma + exactamente 2 dígitos)
    val cleaned = if (Regex(""",\d{2}$""").containsMatchIn(s)) {
        s.replace(".", "").replace(",", ".")
    } else {
        s.replace(Regex("[,\\s]"), "")
    }

    return try {
        if (cleaned.isEmpty()) null
        else {
            val valor = BigDecimal(cleaned)
            if (negativo || negSign) valor.negate() else valor
        }
    } catch (_: NumberFormatException) {
        null
    }
}
