package com.example.flowtrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO Sprint 8: reemplazar FontFamily.Default por Inter via ui-text-google-fonts

/** Estilo para cifras monetarias — alineación tabular (DS §3.3). */
val TabularNumber = TextStyle(fontFeatureSettings = "tnum, ss01")

// Design system §3.2 — escala tipográfica
val FinanzasTypography = Typography(
    displayLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Bold,
        fontSize      = 36.sp,
        lineHeight    = 40.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Bold,
        fontSize      = 30.sp,
        lineHeight    = 36.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = (-0.6).sp,
    ),
    titleLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 17.sp,
        lineHeight    = 22.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 15.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 14.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 15.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 13.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.6.sp,
    ),
    labelMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 10.sp,
        lineHeight    = 13.sp,
        letterSpacing = 0.sp,
    ),
)
