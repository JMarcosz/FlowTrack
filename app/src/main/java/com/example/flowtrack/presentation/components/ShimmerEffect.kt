package com.example.flowtrack.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.shimmerEffect(): Modifier = composed {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate_anim"
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnim.value, y = translateAnim.value)
        )
    )
}

@Composable
fun TransactionShimmerItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
    }
}

@Composable
fun DashboardStatShimmerCard() {
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .shimmerEffect()
    )
}

@Composable
fun CreditCardShimmerItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .shimmerEffect()
    )
}

@Composable
fun ShimmerHistoryItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .shimmerEffect()
    )
}

@Composable
fun ResumenShimmerItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .shimmerEffect()
    )
}
