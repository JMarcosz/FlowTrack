package com.example.flowtrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Placeholder de logo bancario — muestra las 2 primeras letras del código banco
 * con el color de marca del DS sobre un fondo semitransparente.
 * Sprint 8: reemplazar el Text por Image cuando los assets vectoriales estén listos.
 */
@Composable
fun BankLogo(
    bancoCodigo: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val banco = bancoPorCodigo(bancoCodigo)
    val bgColor = banco.color.copy(alpha = 0.12f)
    val textColor = banco.color

    Box(
        modifier = modifier
            .size(size)
            .background(bgColor, RoundedCornerShape(size * 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = bancoCodigo.take(2),
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.28f).sp,
        )
    }
}
