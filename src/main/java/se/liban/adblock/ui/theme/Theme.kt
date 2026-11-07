package se.liban.adblock.ui.theme

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(AdBlockPalette.GREEN),
    onPrimary = Color(AdBlockPalette.WHITE),
    primaryContainer = Color(AdBlockPalette.MINT_CONTAINER),
    onPrimaryContainer = Color(AdBlockPalette.GREEN_DARK),
    secondary = Color(AdBlockPalette.GREEN),
    onSecondary = Color(AdBlockPalette.WHITE),
    background = Color(AdBlockPalette.LIGHT_BACKGROUND),
    onBackground = Color(AdBlockPalette.LIGHT_ON_BACKGROUND),
    surface = Color(AdBlockPalette.LIGHT_BACKGROUND),
    onSurface = Color(AdBlockPalette.LIGHT_ON_BACKGROUND),
    surfaceContainerLowest = Color(AdBlockPalette.WHITE),
    surfaceContainerLow = Color(0xFFEEF3F0),
    surfaceContainer = Color(AdBlockPalette.LIGHT_SURFACE_CONTAINER),
    surfaceContainerHigh = Color(AdBlockPalette.WHITE),
    surfaceContainerHighest = Color(0xFFE7F0EB),
    surfaceVariant = Color(AdBlockPalette.LIGHT_SURFACE_VARIANT),
    onSurfaceVariant = Color(AdBlockPalette.LIGHT_ON_SURFACE_VARIANT),
    outline = Color(AdBlockPalette.LIGHT_OUTLINE),
    outlineVariant = Color(AdBlockPalette.LIGHT_OUTLINE_VARIANT)
)

private val DarkColors = darkColorScheme(
    primary = Color(AdBlockPalette.MINT),
    onPrimary = Color(AdBlockPalette.GREEN_DARK),
    primaryContainer = Color(AdBlockPalette.GREEN_DARK),
    onPrimaryContainer = Color(AdBlockPalette.MINT_CONTAINER),
    secondary = Color(AdBlockPalette.MINT),
    onSecondary = Color(AdBlockPalette.GREEN_DARK),
    background = Color(AdBlockPalette.DARK_BACKGROUND),
    onBackground = Color(AdBlockPalette.DARK_ON_BACKGROUND),
    surface = Color(AdBlockPalette.DARK_BACKGROUND),
    onSurface = Color(AdBlockPalette.DARK_ON_BACKGROUND),
    surfaceContainerLowest = Color(0xFF0B100E),
    surfaceContainerLow = Color(0xFF121A16),
    surfaceContainer = Color(AdBlockPalette.DARK_SURFACE_CONTAINER),
    surfaceContainerHigh = Color(0xFF1E2823),
    surfaceContainerHighest = Color(0xFF24302A),
    surfaceVariant = Color(AdBlockPalette.DARK_SURFACE_VARIANT),
    onSurfaceVariant = Color(AdBlockPalette.DARK_ON_SURFACE_VARIANT),
    outline = Color(AdBlockPalette.DARK_OUTLINE),
    outlineVariant = Color(AdBlockPalette.DARK_OUTLINE_VARIANT)
)

fun ThemeMode.nightMode(): Int = when (this) {
    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}

@Composable
fun SystemlessAdBlockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = themeMode.resolveDark(isSystemInDarkTheme())
    val dynamic = themeMode == ThemeMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun adBlockTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurface
)
