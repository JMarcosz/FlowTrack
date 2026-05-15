package com.example.flowtrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.flowtrack.ui.theme.Radii
import com.example.flowtrack.ui.theme.Spacing

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(Radii.full))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    ) {
        Text(
            text  = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
