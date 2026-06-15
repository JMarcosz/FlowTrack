package com.example.flowtrack.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BankLogo(
    bancoCodigo: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val banco = bancoPorCodigo(bancoCodigo)
    val shape = RoundedCornerShape(size * 0.22f)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (banco.logoResId != null) {
            Image(
                painter = painterResource(banco.logoResId),
                contentDescription = banco.nombre,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size * 0.78f)
                    .padding(2.dp),
            )
        } else {
            Text(
                text = bancoCodigo.take(2),
                color = banco.color,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.28f).sp,
            )
        }
    }
}
