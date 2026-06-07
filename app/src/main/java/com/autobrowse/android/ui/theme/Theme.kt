package com.autobrowse.android.ui.theme

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

private val TealPrimary = Color(0xFF0D7377)
private val TealLight = Color(0xFF14A085)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F7),
    onPrimary = Color(0xFF0A0A0C),
    primaryContainer = Color(0xFF2C2C36),
    onPrimaryContainer = Color(0xFFF5F5F7),
    secondary = Color(0xFF9898A0),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF6B7FD7),
    background = Color(0xFF000000),
    surface = Color(0xFF0C0C10),
    surfaceVariant = Color(0xFF1C1C24),
    surfaceContainerHigh = Color(0xFF22222C),
    surfaceContainerHighest = Color(0xFF2A2A34),
    onBackground = Color(0xFFF2F2F7),
    onSurface = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFFAEAEB8),
    outline = Color(0xFF45454F),
    outlineVariant = Color(0xFF2E2E38),
)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    secondary = TealLight,
    onSecondary = Color.White,
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E),
)

private val AppTypography = Typography(
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun AutobrowseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}