package com.bookorbit.android

import androidx.compose.material3.ColorScheme
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

private val CharcoalColors = darkColorScheme(
    primary = Color(0xFFA8CDE0),
    onPrimary = Color(0xFF0B3445),
    primaryContainer = Color(0xFF263E49),
    onPrimaryContainer = Color(0xFFD0EAF5),
    secondary = OrbitAmber,
    onSecondary = Color(0xFF422D00),
    secondaryContainer = Color(0xFF55400D),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = Color(0xFFC7C7CC),
    onTertiary = Color(0xFF2A292D),
    background = Color(0xFF0D0D0F),
    onBackground = Color(0xFFF1F1F3),
    surface = Color(0xFF151517),
    onSurface = Color(0xFFF1F1F3),
    surfaceVariant = Color(0xFF222226),
    onSurfaceVariant = Color(0xFFC8C7CC),
    outline = Color(0xFF929198),
    outlineVariant = Color(0xFF424147),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val WarmBlackColors = darkColorScheme(
    primary = Color(0xFFB7D4E2),
    onPrimary = Color(0xFF123542),
    primaryContainer = Color(0xFF294751),
    onPrimaryContainer = Color(0xFFD2ECF7),
    secondary = OrbitAmber,
    onSecondary = Color(0xFF422D00),
    secondaryContainer = Color(0xFF57420E),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = Color(0xFFD5C4AE),
    onTertiary = Color(0xFF392F23),
    background = Color(0xFF100E0B),
    onBackground = Color(0xFFF4EEE6),
    surface = Color(0xFF191612),
    onSurface = Color(0xFFF4EEE6),
    surfaceVariant = Color(0xFF28231D),
    onSurfaceVariant = Color(0xFFD1C6B9),
    outline = Color(0xFF9D9184),
    outlineVariant = Color(0xFF4B433A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val OledBlackColors = darkColorScheme(
    primary = Color(0xFFA8CDE0),
    onPrimary = Color(0xFF073447),
    primaryContainer = Color(0xFF1C3D4A),
    onPrimaryContainer = Color(0xFFC8E8F7),
    secondary = OrbitAmber,
    onSecondary = Color(0xFF422D00),
    secondaryContainer = Color(0xFF4C3908),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = Color(0xFFCCCCD0),
    onTertiary = Color(0xFF29292C),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F6),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFF5F5F6),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFCECDD2),
    outline = Color(0xFF96959B),
    outlineVariant = Color(0xFF3E3E42),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

internal fun appColorSchemeForTheme(
    themeMode: AppThemeMode,
    systemDark: Boolean
): ColorScheme = when (themeMode) {
    AppThemeMode.FOLLOW_SYSTEM -> if (systemDark) CharcoalColors else LightColors
    AppThemeMode.LIGHT -> LightColors
    AppThemeMode.CHARCOAL -> CharcoalColors
    AppThemeMode.WARM_BLACK -> WarmBlackColors
    AppThemeMode.OLED_BLACK -> OledBlackColors
}

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
    val colors = appColorSchemeForTheme(
        themeMode = themeMode,
        systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    )
    MaterialTheme(
        colorScheme = colors,
        typography = OrbitTypography,
        shapes = OrbitShapes,
        content = content
    )
}
