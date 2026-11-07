package com.masarify.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.masarify.app.data.ThemeColor
import com.masarify.app.data.ThemeMode
import com.masarify.app.data.UserSettings

private val GreenLight = lightColorScheme(
    primary = Color(0xFF135F4C), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2EB), onPrimaryContainer = Color(0xFF103C31),
    secondary = Color(0xFF527066), surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17231F), onSurfaceVariant = Color(0xFF71817A),
    background = Color(0xFFF5F7F6), onBackground = Color(0xFF17231F),
    surfaceVariant = Color(0xFFE9F1ED), outline = Color(0xFFD9E2DD)
)
private val BlueLight = lightColorScheme(
    primary = Color(0xFF183F70), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EDF8), onPrimaryContainer = Color(0xFF102B4C),
    secondary = Color(0xFF5D6F86), surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17243A), onSurfaceVariant = Color(0xFF738096),
    background = Color(0xFFF3F5F9), onBackground = Color(0xFF17243A),
    surfaceVariant = Color(0xFFE8EDF4), outline = Color(0xFFD9E0EA)
)
private val GreenDark = darkColorScheme(
    primary = Color(0xFF72D0AB), onPrimary = Color(0xFF093328),
    primaryContainer = Color(0xFF214B3D), onPrimaryContainer = Color(0xFFD9F4E8),
    secondary = Color(0xFF9AB8AC), surface = Color(0xFF18211D),
    onSurface = Color(0xFFEDF4F0), onSurfaceVariant = Color(0xFF9DADA5),
    background = Color(0xFF101613), onBackground = Color(0xFFEDF4F0),
    surfaceVariant = Color(0xFF24312B), outline = Color(0xFF35463E)
)
private val BlueDark = darkColorScheme(
    primary = Color(0xFF8AB8F4), onPrimary = Color(0xFF102C50),
    primaryContainer = Color(0xFF243E60), onPrimaryContainer = Color(0xFFE0ECFC),
    secondary = Color(0xFF9AACC3), surface = Color(0xFF19212D),
    onSurface = Color(0xFFEDF2F9), onSurfaceVariant = Color(0xFFA0ADBE),
    background = Color(0xFF111721), onBackground = Color(0xFFEDF2F9),
    surfaceVariant = Color(0xFF273345), outline = Color(0xFF3B4A60)
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
        activity.window.navigationBarColor = colors.background.toArgb()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
