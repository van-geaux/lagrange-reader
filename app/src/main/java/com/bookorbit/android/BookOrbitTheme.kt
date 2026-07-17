package com.bookorbit.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OrbitNavy = Color(0xFF102A43)
private val OrbitNavyDeep = Color(0xFF071B2B)
private val OrbitAmber = Color(0xFFF0B429)
private val OrbitPaper = Color(0xFFFAF6ED)
private val OrbitSurface = Color(0xFFFFFCF6)
private val OrbitSlate = Color(0xFF486581)
private val OrbitMist = Color(0xFFD9E2EC)
private val OrbitNightSurface = Color(0xFF102B38)
private val OrbitNightSurfaceHigh = Color(0xFF193846)
private val OrbitNightText = Color(0xFFE9F1F5)

private val LightColors = lightColorScheme(
    primary = OrbitNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAF2),
    onPrimaryContainer = OrbitNavyDeep,
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA1),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = OrbitSlate,
    onTertiary = Color.White,
    background = OrbitPaper,
    onBackground = OrbitNavyDeep,
    surface = OrbitSurface,
    onSurface = OrbitNavyDeep,
    surfaceVariant = Color(0xFFEAF0F3),
    onSurfaceVariant = Color(0xFF40515D),
    outline = Color(0xFF71828D),
    outlineVariant = OrbitMist,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8CDE0),
    onPrimary = Color(0xFF073447),
    primaryContainer = Color(0xFF214C60),
    onPrimaryContainer = Color(0xFFC8E8F7),
    secondary = OrbitAmber,
    onSecondary = Color(0xFF422D00),
    secondaryContainer = Color(0xFF604500),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = Color(0xFFB7CAD4),
    onTertiary = Color(0xFF22343D),
    background = OrbitNavyDeep,
    onBackground = OrbitNightText,
    surface = OrbitNightSurface,
    onSurface = OrbitNightText,
    surfaceVariant = OrbitNightSurfaceHigh,
    onSurfaceVariant = Color(0xFFC4D2D9),
    outline = Color(0xFF8E9EA6),
    outlineVariant = Color(0xFF3D4C53),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val OrbitTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp
    )
)

private val OrbitShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
)

@Composable
fun BookOrbitTheme(
    themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkColors = when (themeMode) {
        AppThemeMode.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = if (useDarkColors) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = OrbitTypography,
        shapes = OrbitShapes,
        content = content
    )
}
