package il.ozer.koli

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val LocalIsDark = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = Color(0xFF15803D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E0), onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF4D7C5F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBE1), onSecondaryContainer = Color(0xFF0E2A1A),
    tertiary = Color(0xFFB7791F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEFC7), onTertiaryContainer = Color(0xFF3B2800),
    background = Color(0xFFF3F9F5), onBackground = Color(0xFF10201A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF10201A),
    surfaceVariant = Color(0xFFE1EEE6), onSurfaceVariant = Color(0xFF3F544A),
    outline = Color(0xFF6B8577), outlineVariant = Color(0xFFC3D8CB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7FCF9),
    surfaceContainer = Color(0xFFEDF6F0), surfaceContainerHigh = Color(0xFFE5F1EA),
    surfaceContainerHighest = Color(0xFFDDEBE3)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80), onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF14532D), onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFFA5D0B2), onSecondary = Color(0xFF103822),
    secondaryContainer = Color(0xFF2B4A38), onSecondaryContainer = Color(0xFFD7EBDD),
    tertiary = Color(0xFFF5C451), onTertiary = Color(0xFF3B2800),
    tertiaryContainer = Color(0xFF574000), onTertiaryContainer = Color(0xFFFFEFC7),
    background = Color(0xFF0B1410), onBackground = Color(0xFFE0EDE5),
    surface = Color(0xFF101C16), onSurface = Color(0xFFE0EDE5),
    surfaceVariant = Color(0xFF1F2E26), onSurfaceVariant = Color(0xFFB4C8BC),
    outline = Color(0xFF7F9A8B), outlineVariant = Color(0xFF2E4237),
    surfaceContainerLowest = Color(0xFF08100C), surfaceContainerLow = Color(0xFF111D17),
    surfaceContainer = Color(0xFF16241D), surfaceContainerHigh = Color(0xFF1C2D24),
    surfaceContainerHighest = Color(0xFF233629)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** mode: "system" / "light" / "dark" */
fun isDarkMode(mode: String, systemDark: Boolean) = when (mode) { "dark" -> true; "light" -> false; else -> systemDark }

@Composable
fun AppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = isDarkMode(mode, isSystemInDarkTheme())
    val base = Typography()
    val typo = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold)
    )
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = AppShapes, typography = typo, content = content)
    }
}
