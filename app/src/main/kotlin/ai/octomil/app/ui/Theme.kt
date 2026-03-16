package ai.octomil.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Brand palette ──

object OctomilColors {
    val Indigo50 = Color(0xFFEEF2FF)
    val Indigo100 = Color(0xFFE0E7FF)
    val Indigo200 = Color(0xFFC7D2FE)
    val Indigo400 = Color(0xFF818CF8)
    val Indigo500 = Color(0xFF6366F1)
    val Indigo600 = Color(0xFF4F46E5)
    val Indigo900 = Color(0xFF312E81)

    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)

    val Emerald400 = Color(0xFF34D399)
    val Emerald500 = Color(0xFF10B981)
    val Cyan400 = Color(0xFF22D3EE)
    val Amber400 = Color(0xFFFBBF24)
    val Rose400 = Color(0xFFFB7185)
    val Rose500 = Color(0xFFF43F5E)
}

// ── Color schemes ──

private val DarkColorScheme = darkColorScheme(
    primary = OctomilColors.Indigo400,
    onPrimary = Color.White,
    primaryContainer = OctomilColors.Indigo900,
    onPrimaryContainer = OctomilColors.Indigo200,
    secondary = OctomilColors.Cyan400,
    onSecondary = OctomilColors.Slate900,
    tertiary = OctomilColors.Emerald400,
    onTertiary = OctomilColors.Slate900,
    background = Color(0xFF09090F),
    onBackground = OctomilColors.Slate200,
    surface = Color(0xFF111118),
    onSurface = OctomilColors.Slate200,
    surfaceVariant = Color(0xFF1A1A23),
    onSurfaceVariant = OctomilColors.Slate400,
    surfaceContainerLowest = Color(0xFF09090F),
    surfaceContainerLow = Color(0xFF111118),
    surfaceContainer = Color(0xFF1A1A23),
    surfaceContainerHigh = Color(0xFF232330),
    surfaceContainerHighest = Color(0xFF2C2C3A),
    outline = OctomilColors.Slate700,
    outlineVariant = Color(0xFF1E293B),
    error = OctomilColors.Rose500,
    errorContainer = Color(0xFF7F1D1D),
    onError = Color.White,
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = OctomilColors.Slate200,
    inverseOnSurface = OctomilColors.Slate900,
)

private val LightColorScheme = lightColorScheme(
    primary = OctomilColors.Indigo600,
    onPrimary = Color.White,
    primaryContainer = OctomilColors.Indigo100,
    onPrimaryContainer = OctomilColors.Indigo900,
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    tertiary = OctomilColors.Emerald500,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = OctomilColors.Slate900,
    surface = Color.White,
    onSurface = OctomilColors.Slate900,
    surfaceVariant = OctomilColors.Slate100,
    onSurfaceVariant = OctomilColors.Slate500,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = OctomilColors.Slate50,
    surfaceContainer = OctomilColors.Slate100,
    surfaceContainerHigh = OctomilColors.Slate200,
    surfaceContainerHighest = OctomilColors.Slate300,
    outline = OctomilColors.Slate300,
    outlineVariant = OctomilColors.Slate200,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color.White,
    onErrorContainer = Color(0xFF991B1B),
    inverseSurface = OctomilColors.Slate900,
    inverseOnSurface = OctomilColors.Slate100,
)

// ── Typography ──

val OctomilTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)

// ── Theme composable ──

@Composable
fun OctomilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Adjust system bar icon colors to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OctomilTypography,
        content = content,
    )
}
