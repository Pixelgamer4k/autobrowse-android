package com.autobrowse.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.autobrowse.android.domain.model.ThemeMode

private val TealPrimary = Color(0xFF0D7377)
private val TealLight = Color(0xFF14A085)

private val DarkColors = darkColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF143D3F),
    onPrimaryContainer = Color(0xFFA7F0F2),
    secondary = TealLight,
    onSecondary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2C2C2E),
)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    secondary = TealLight,
    onSecondary = Color.White,
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF1F5),
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFF5A5A6E),
    outline = Color(0xFFD0D4DC),
    outlineVariant = Color(0xFFE0E4EC),
)

private val AppTypography = Typography(
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
)

@Composable
fun AutobrowseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
