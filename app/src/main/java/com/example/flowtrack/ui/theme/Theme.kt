package com.example.flowtrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val success: Color,
    val warning: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        success = Color.Unspecified,
        warning = Color.Unspecified
    )
}

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}

private val lightExtendedColors = ExtendedColors(
    success = Success,
    warning = Warning
)

private val darkExtendedColors = ExtendedColors(
    success = SuccessDark,
    warning = WarningDark
)

// Design system §2 — FinanzasLightColorScheme
private val FinanzasColorScheme = lightColorScheme(
    primary            = Primary,
    onPrimary          = Color.White,
    primaryContainer   = Primary50,
    onPrimaryContainer = Ink,

    secondary          = Success,
    onSecondary        = Color.White,
    secondaryContainer = Success50,
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

    outline            = OutlineCustom,
    outlineVariant     = Line2,
    scrim              = ScrimCustom,
    surfaceContainer   = SurfaceContainer,
)

private val FinanzasDarkColorScheme = darkColorScheme(
    primary            = PrimaryDark,
    onPrimary          = OnPrimaryDark,
    primaryContainer   = PrimaryContainerDark,
    onPrimaryContainer = OnBackgroundDark,

    secondary          = SuccessDark,
    onSecondary        = BackgroundDark,
    secondaryContainer = SuccessDark.copy(alpha = 0.2f),
    onSecondaryContainer = SuccessDark,

    error              = ErrorDark,
    onError            = BackgroundDark,
    errorContainer     = ErrorDark.copy(alpha = 0.2f),
    onErrorContainer   = ErrorDark,

    background         = BackgroundDark,
    onBackground       = OnBackgroundDark,

    surface            = SurfaceDark,
    onSurface          = OnBackgroundDark,
    surfaceVariant     = LineDark,
    onSurfaceVariant   = MutedDark,

    outline            = OutlineDark,
    outlineVariant     = LineDark,
    scrim              = ScrimDark,
    surfaceContainer   = SurfaceContainerDark,
)

@Composable
fun FlowTrackTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FinanzasDarkColorScheme else FinanzasColorScheme
    val extendedColors = if (darkTheme) darkExtendedColors else lightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = FinanzasTypography,
            content     = content,
        )
    }
}
