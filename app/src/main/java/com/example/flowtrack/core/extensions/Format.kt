package com.example.flowtrack.core.extensions

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val locale = Locale("es", "DO")
private val zoneId = ZoneId.of("America/Santo_Domingo")

private val currencyFormats = mapOf(
    "DOP" to NumberFormat.getCurrencyInstance(locale).apply { currency = java.util.Currency.getInstance("DOP") },
    "USD" to NumberFormat.getCurrencyInstance(Locale.US)
)

fun BigDecimal.formatMoney(currency: String = "DOP"): String {
    val fmt = currencyFormats[currency] ?: currencyFormats["DOP"]!!
    return fmt.format(this)
}

private val fullDateFmt   = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
private val shortDateFmt  = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

fun Instant.formatDate(): String =
    fullDateFmt.format(atZone(zoneId).toLocalDate())

fun Instant.formatDateRelative(): String {
    val date  = atZone(zoneId).toLocalDate()
    val today = LocalDate.now(zoneId)
    return when (date) {
        today            -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else             -> shortDateFmt.format(date)
    }
}
