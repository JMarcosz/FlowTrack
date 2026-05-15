package com.example.flowtrack.presentation.components

import androidx.compose.ui.graphics.Color

data class BancoUI(
    val codigo: String,
    val nombre: String,
    val color: Color,
)

val bancoRegistry: Map<String, BancoUI> = listOf(
    BancoUI("BANRESERVAS", "BanReservas",      Color(0xFF005DA8)),
    BancoUI("POPULAR",     "Banco Popular",    Color(0xFF005CAA)),
    BancoUI("QIK",         "Qik",              Color(0xFF0099E5)),
    BancoUI("CIBAO",       "Asociación Cibao", Color(0xFFE30613)),
    BancoUI("BHD",         "BHD",              Color(0xFF003F7F)),
).associateBy { it.codigo }

fun bancoPorCodigo(codigo: String): BancoUI =
    bancoRegistry[codigo] ?: BancoUI(codigo, codigo, Color(0xFF4A5A70))
