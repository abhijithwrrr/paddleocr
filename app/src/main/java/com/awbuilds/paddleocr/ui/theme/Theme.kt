package com.awbuilds.paddleocr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF123B5D)
private val NavyDeep = Color(0xFF0B2A45)
private val Teal = Color(0xFF167C6B)
private val Amber = Color(0xFFB26B3A)
private val Cloud = Color(0xFFF5F7FA)
private val Ink = Color(0xFF152238)
private val Line = Color(0xFFDDE3EA)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8F8),
    onPrimaryContainer = NavyDeep,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F1EB),
    onSecondaryContainer = Color(0xFF063D34),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE7D8),
    onTertiaryContainer = Color(0xFF4A250E),
    background = Cloud,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF4B5565),
    outline = Line,
    outlineVariant = Color(0xFFE8EDF2),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC8FF),
    onPrimary = Color(0xFF062033),
    primaryContainer = Color(0xFF163B5A),
    onPrimaryContainer = Color(0xFFD8E8F8),
    secondary = Color(0xFF7BD8C4),
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF0E4D43),
    onSecondaryContainer = Color(0xFFD6F1EB),
    tertiary = Color(0xFFF2B78D),
    onTertiary = Color(0xFF2D1607),
    tertiaryContainer = Color(0xFF5B321A),
    onTertiaryContainer = Color(0xFFFBE7D8),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE7EDF5),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFE7EDF5),
    surfaceVariant = Color(0xFF1A2538),
    onSurfaceVariant = Color(0xFFB9C4D3),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF253044),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun PaddleOcrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
