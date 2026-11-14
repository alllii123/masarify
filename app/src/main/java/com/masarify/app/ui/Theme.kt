package com.masarify.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.masarify.app.data.ThemeColor
import com.masarify.app.data.ThemeMode
import com.masarify.app.data.UserSettings

// ── لوحة الأخضر ─────────────────────────────────────────────────────────────
private val GreenLight = lightColorScheme(
    primary = Color(0xFF0E6B52), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8F0E6), onPrimaryContainer = Color(0xFF06301F),
    secondary = Color(0xFF4C6B60), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAF3EF), onSecondaryContainer = Color(0xFF16342A),
    tertiary = Color(0xFF8A5A00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF0D2), onTertiaryContainer = Color(0xFF2C1D00),
    background = Color(0xFFF4F8F6), onBackground = Color(0xFF101E19),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF101E19),
    surfaceVariant = Color(0xFFEDF4F1), onSurfaceVariant = Color(0xFF61736C),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAFCFB),
    surfaceContainer = Color(0xFFF7FAF9), surfaceContainerHigh = Color(0xFFF1F6F3),
    surfaceContainerHighest = Color(0xFFEAF1ED),
    outline = Color(0xFFCBD8D2), outlineVariant = Color(0xFFE2EBE6),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFBE6E4)
)

private val BlueLight = lightColorScheme(
    primary = Color(0xFF124A86), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E7FA), onPrimaryContainer = Color(0xFF06203F),
    secondary = Color(0xFF51677F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9EFF7), onSecondaryContainer = Color(0xFF16293D),
    tertiary = Color(0xFF8A5A00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF0D2), onTertiaryContainer = Color(0xFF2C1D00),
    background = Color(0xFFF3F6FB), onBackground = Color(0xFF0E1A2A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0E1A2A),
    surfaceVariant = Color(0xFFEBF0F7), onSurfaceVariant = Color(0xFF60708A),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9FBFE),
    surfaceContainer = Color(0xFFF6F9FD), surfaceContainerHigh = Color(0xFFEFF4FA),
    surfaceContainerHighest = Color(0xFFE7EEF8),
    outline = Color(0xFFC7D3E3), outlineVariant = Color(0xFFDEE7F2),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFBE6E4)
)

private val GreenDark = darkColorScheme(
    primary = Color(0xFF6FD6B0), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF17513F), onPrimaryContainer = Color(0xFFCFF5E4),
    secondary = Color(0xFF9FBDB0), onSecondary = Color(0xFF0F2B22),
    secondaryContainer = Color(0xFF22362E), onSecondaryContainer = Color(0xFFD9EDE3),
    tertiary = Color(0xFFF3C275), onTertiary = Color(0xFF3E2A00),
    tertiaryContainer = Color(0xFF3A2B0B), onTertiaryContainer = Color(0xFFFFE7B8),
    background = Color(0xFF0C1411), onBackground = Color(0xFFE7F1EC),
    surface = Color(0xFF131C18), onSurface = Color(0xFFE7F1EC),
    surfaceVariant = Color(0xFF1D2925), onSurfaceVariant = Color(0xFF9BADA5),
    surfaceContainerLowest = Color(0xFF080F0C), surfaceContainerLow = Color(0xFF121B17),
    surfaceContainer = Color(0xFF16211C), surfaceContainerHigh = Color(0xFF1B2721),
    surfaceContainerHighest = Color(0xFF213029),
    outline = Color(0xFF33463D), outlineVariant = Color(0xFF24312B),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF3B1512)
)

private val BlueDark = darkColorScheme(
    primary = Color(0xFF93C3FA), onPrimary = Color(0xFF002A53),
    primaryContainer = Color(0xFF1B3E67), onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFFA8BCD4), onSecondary = Color(0xFF122437),
    secondaryContainer = Color(0xFF243448), onSecondaryContainer = Color(0xFFDCE8F7),
    tertiary = Color(0xFFF3C275), onTertiary = Color(0xFF3E2A00),
    tertiaryContainer = Color(0xFF3A2B0B), onTertiaryContainer = Color(0xFFFFE7B8),
    background = Color(0xFF0B1119), onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF121A24), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1C2735), onSurfaceVariant = Color(0xFF9DADC1),
    surfaceContainerLowest = Color(0xFF080D14), surfaceContainerLow = Color(0xFF111922),
    surfaceContainer = Color(0xFF151F2B), surfaceContainerHigh = Color(0xFF1A2534),
    surfaceContainerHighest = Color(0xFF202E40),
    outline = Color(0xFF34455C), outlineVariant = Color(0xFF22303F),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF3B1512)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, lineHeightStyle = lineHeightStyle),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, lineHeightStyle = lineHeightStyle),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, lineHeightStyle = lineHeightStyle),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, lineHeightStyle = lineHeightStyle),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = lineHeightStyle),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = lineHeightStyle),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = lineHeightStyle),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, lineHeightStyle = lineHeightStyle),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 22.sp, lineHeightStyle = lineHeightStyle),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp, lineHeightStyle = lineHeightStyle),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = lineHeightStyle),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, lineHeightStyle = lineHeightStyle),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, lineHeightStyle = lineHeightStyle)
)

@Composable
fun MasarifyTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when (settings.themeColor to dark) {
        ThemeColor.GREEN to false -> GreenLight
        ThemeColor.GREEN to true -> GreenDark
        ThemeColor.BLUE to false -> BlueLight
        ThemeColor.BLUE to true -> BlueDark
        else -> GreenLight
    }
    val context = LocalContext.current
    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        activity.window.statusBarColor = Color.Transparent.toArgb()
        activity.window.navigationBarColor = colors.background.value.toInt()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
