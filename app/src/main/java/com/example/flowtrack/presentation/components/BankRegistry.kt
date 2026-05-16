package com.example.flowtrack.presentation.components

import androidx.compose.ui.graphics.Color
import com.example.flowtrack.ui.theme.BancoBanReservas
import com.example.flowtrack.ui.theme.BancoBhd
import com.example.flowtrack.ui.theme.BancoCibao
import com.example.flowtrack.ui.theme.BancoPopular
import com.example.flowtrack.ui.theme.BancoQik
import com.example.flowtrack.ui.theme.Muted

data class BancoUI(
    val codigo: String,
    val nombre: String,
    val color: Color,
)

val bancoRegistry: Map<String, BancoUI> = listOf(
    BancoUI("BANRESERVAS", "BanReservas",      BancoBanReservas),
    BancoUI("POPULAR",     "Banco Popular",    BancoPopular),
    BancoUI("QIK",         "Qik",              BancoQik),
    BancoUI("CIBAO",       "Asociación Cibao", BancoCibao),
    BancoUI("BHD",         "BHD (próximamente)", BancoBhd),
).associateBy { it.codigo }

fun bancoPorCodigo(codigo: String): BancoUI =
    bancoRegistry[codigo] ?: BancoUI(codigo, codigo, Muted)
