package com.example.flowtrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryIcon(
    categoriaId: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val cat = categoriaPorId(categoriaId)
    Box(
        modifier        = modifier
            .size(size)
            .background(cat.color.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Emoji fallback — Sprint 8 will replace with Tabler icon font
        val emoji = categoryEmoji(categoriaId)
        Text(text = emoji, fontSize = (size.value * 0.45f).sp)
    }
}

private fun categoryEmoji(id: String) = when (id) {
    "alimentacion"           -> "🍽"
    "transporte"             -> "🚗"
    "salud"                  -> "💊"
    "entretenimiento"        -> "🎬"
    "suscripciones"          -> "🔄"
    "servicios"              -> "⚡"
    "compras"                -> "🛍"
    "atm"                    -> "💵"
    "transferencia_enviada"  -> "↗"
    "impuestos"              -> "🧾"
    "intereses_comisiones"   -> "%"
    "salario"                -> "💼"
    "transferencia_recibida" -> "↙"
    "deposito"               -> "🪙"
    "cashback"               -> "🎁"
    "pago_tarjeta"           -> "💳"
    else                     -> "?"
}
