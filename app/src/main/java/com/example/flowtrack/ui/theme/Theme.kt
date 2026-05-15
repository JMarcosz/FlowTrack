package com.example.flowtrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FinanzasColorScheme = darkColorScheme(
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

@Composable
fun FlowTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FinanzasColorScheme,
        typography  = FinanzasTypography,
        content     = content
    )
}
