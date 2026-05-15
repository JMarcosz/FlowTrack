package com.example.flowtrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val FinanzasDarkColorScheme = darkColorScheme(
    primary            = Brand500,
    onPrimary          = TextPrimary,
    primaryContainer   = Brand600,
    onPrimaryContainer = TextPrimary,
    secondary          = Brand400,
    onSecondary        = BgDeep,
    background         = BgDeep,
    onBackground       = TextPrimary,
    surface            = BgSurface,
    onSurface          = TextPrimary,
    surfaceVariant     = BgCard,
    onSurfaceVariant   = TextSecondary,
    outline            = BgDivider,
    error              = SemanticExpense,
    onError            = TextPrimary,
)

private val FinanzasLightColorScheme = lightColorScheme(
    primary            = Brand500,
    onPrimary          = Color.White,
    primaryContainer   = Brand200,
    onPrimaryContainer = Color.Black,
    secondary          = Brand400,
    onSecondary        = Color.White,
    background         = Color(0xFFF3F4F6),
    onBackground       = Color(0xFF111827),
    surface            = Color.White,
    onSurface          = Color(0xFF111827),
    surfaceVariant     = Color(0xFFE5E7EB),
    onSurfaceVariant   = Color(0xFF4B5563),
    outline            = Color(0xFFD1D5DB),
    error              = SemanticExpense,
    onError            = Color.White,
)

@Composable
fun FlowTrackTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FinanzasDarkColorScheme else FinanzasLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = FinanzasTypography,
        content     = content
    )
}
