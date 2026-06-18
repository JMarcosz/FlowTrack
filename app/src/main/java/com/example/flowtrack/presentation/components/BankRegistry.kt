package com.example.flowtrack.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.flowtrack.R
import com.example.flowtrack.ui.theme.BancoBanReservas
import com.example.flowtrack.ui.theme.BancoBhd
import com.example.flowtrack.ui.theme.BancoCibao
import com.example.flowtrack.ui.theme.BancoPopular
import com.example.flowtrack.ui.theme.BancoQik

// Metadata completa de cada banco: fuente unica de verdad para color, abreviatura, logo y fg.
data class BancoUI(
    val codigo: String,
    val nombre: String,
    val color: Color,
    val abbr: String,
    @DrawableRes val logoResId: Int? = null,
    val fgColor: Color = Color.White,
)

val bancoRegistry: Map<String, BancoUI> = listOf(
    BancoUI("BANRESERVAS", "BanReservas", BancoBanReservas, abbr = "BR", logoResId = R.drawable.logo_banreservas),
    BancoUI("POPULAR", "Banco Popular", BancoPopular, abbr = "BP", logoResId = R.drawable.logo_popular),
    BancoUI("QIK", "Qik", BancoQik, abbr = "QIK", logoResId = R.drawable.logo_qik),
    BancoUI("CIBAO", "Asociacion Cibao", BancoCibao, abbr = "AC", logoResId = R.drawable.logo_cibao),
    BancoUI("BHD", "BHD Leon", BancoBhd, abbr = "BHD", logoResId = R.drawable.logo_bhd),
).associateBy { it.codigo }

fun bancoPorCodigo(codigo: String): BancoUI =
    bancoRegistry[codigo.uppercase()] ?: BancoUI(codigo, codigo, Color.Gray, abbr = codigo.take(2))

// Badge de banco (fondo, texto, abreviatura) â€” usado en TarjetasScreen y UploadScreen.
data class BankBadge(val bg: Color, val fg: Color, val abbr: String)

fun bankBadge(bancoCodigo: String): BankBadge {
    val banco = bancoPorCodigo(bancoCodigo)
    val foreground = if (banco.color.luminance() > 0.62f) Color(0xFF0F172A) else Color.White
    return BankBadge(bg = banco.color, fg = foreground, abbr = banco.abbr)
}
