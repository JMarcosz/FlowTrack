package com.example.flowtrack.presentation.components

import androidx.compose.ui.graphics.Color
import com.example.flowtrack.ui.theme.BancoBanReservas
import com.example.flowtrack.ui.theme.BancoBhd
import com.example.flowtrack.ui.theme.BancoCibao
import com.example.flowtrack.ui.theme.BancoPopular
import com.example.flowtrack.ui.theme.BancoQik
import com.example.flowtrack.ui.theme.BgDark

// Metadata completa de cada banco — fuente única de verdad para color, abreviatura y fg.
data class BancoUI(
    val codigo: String,
    val nombre: String,
    val color: Color,
    val abbr: String,
    val fgColor: Color = Color.White,
)

val bancoRegistry: Map<String, BancoUI> = listOf(
    BancoUI("BANRESERVAS", "BanReservas",         BancoBanReservas, abbr = "BR"),
    BancoUI("POPULAR",     "Banco Popular",        BancoPopular,     abbr = "BP"),
    BancoUI("QIK",         "Qik",                 BancoQik,         abbr = "QIK", fgColor = BgDark),
    BancoUI("CIBAO",       "Asociación Cibao",    BancoCibao,       abbr = "AC"),
    BancoUI("BHD",         "BHD León",            BancoBhd,         abbr = "BHD"),
).associateBy { it.codigo }

fun bancoPorCodigo(codigo: String): BancoUI =
    bancoRegistry[codigo.uppercase()] ?: BancoUI(codigo, codigo, Color.Gray, abbr = codigo.take(2))

// Badge de banco (fondo, texto, abreviatura) — usado en TarjetasScreen y UploadScreen.
data class BankBadge(val bg: Color, val fg: Color, val abbr: String)

fun bankBadge(bancoCodigo: String): BankBadge {
    val banco = bancoPorCodigo(bancoCodigo)
    return BankBadge(bg = banco.color, fg = banco.fgColor, abbr = banco.abbr)
}
