package pe.eduescale.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal const val THEME_PREFERENCES_NAME = "eduescale_settings"
internal const val DARK_THEME_PREFERENCE_KEY = "dark_theme"
internal const val THEME_MODE_PREFERENCE_KEY = "theme_mode"

internal enum class ThemeMode(val preferenceValue: String, val label: String) {
    SYSTEM("system", "Sistema"),
    LIGHT("light", "Claro"),
    DARK("dark", "Oscuro"),
    ;

    fun isDark(systemDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreference(value: String?): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

internal val EduGreen = Color(0xFF176B50)

internal val EduOrange = Color(0xFFD97706)

internal val EduGray = Color(0xFF7B8794)

@Composable
fun EduEscaleTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF78D9B2),
            onPrimary = Color(0xFF003826),
            primaryContainer = Color(0xFF005139),
            onPrimaryContainer = Color(0xFFB4F1D0),
            secondary = Color(0xFFFFB86B),
            onSecondary = Color(0xFF492900),
            secondaryContainer = Color(0xFF653D00),
            onSecondaryContainer = Color(0xFFFFDDB8),
            tertiary = Color(0xFFB9C8E8),
            onTertiary = Color(0xFF23314A),
            tertiaryContainer = Color(0xFF394761),
            onTertiaryContainer = Color(0xFFD9E2FF),
            surface = Color(0xFF151A18),
            onSurface = Color(0xFFE0E4E1),
            surfaceVariant = Color(0xFF414A45),
            onSurfaceVariant = Color(0xFFC0C9C3),
            background = Color(0xFF101412),
            onBackground = Color(0xFFE0E4E1),
            outline = Color(0xFF8A938D),
        )
    } else {
        lightColorScheme(
            primary = EduGreen,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB7EBD2),
            onPrimaryContainer = Color(0xFF002114),
            secondary = EduOrange,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFDDB8),
            onSecondaryContainer = Color(0xFF2E1600),
            tertiary = Color(0xFF526477),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD5E3F7),
            onTertiaryContainer = Color(0xFF0F1D2D),
            surface = Color(0xFFFFFEFC),
            onSurface = Color(0xFF1A1C1A),
            surfaceVariant = Color(0xFFE1E5E1),
            onSurfaceVariant = Color(0xFF414A44),
            background = Color(0xFFF4F7F4),
            onBackground = Color(0xFF181C19),
            outline = Color(0xFF727A74),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
