package com.example.flowtrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design system §2 — FinanzasLightColorScheme
private val FinanzasColorScheme = lightColorScheme(
    primary            = Primary,
    onPrimary          = Color.White,
    primaryContainer   = Primary50,
    onPrimaryContainer = Ink,

    secondary          = Income,
    onSecondary        = Color.White,
    secondaryContainer = Income50,
    onSecondaryContainer = Ink,

    error              = Expense,
    onError            = Color.White,
    errorContainer     = Expense50,
    onErrorContainer   = Ink,

    background         = BgScreen,
    onBackground       = Ink,

    surface            = BgCard,
    onSurface          = Ink,
    surfaceVariant     = Line2,
    onSurfaceVariant   = Muted,

    outline            = Line,
    outlineVariant     = Line2,
)

@Composable
fun FlowTrackTheme(
    darkTheme: Boolean = false,   // DS es 100% light — solo Login fuerza oscuro localmente
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FinanzasColorScheme,
        typography  = FinanzasTypography,
        content     = content,
    )
}
