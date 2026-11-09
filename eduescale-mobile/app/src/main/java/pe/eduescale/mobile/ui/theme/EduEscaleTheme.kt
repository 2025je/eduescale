package pe.eduescale.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal val EduGreen = Color(0xFF176B50)

internal val EduOrange = Color(0xFFD97706)

internal val EduGray = Color(0xFF7B8794)

@Composable
fun EduEscaleTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF70D5AB),
            secondary = Color(0xFFFFB86B),
            surface = Color(0xFF151A18),
            background = Color(0xFF101412),
        )
    } else {
        lightColorScheme(
            primary = EduGreen,
            secondary = EduOrange,
            surface = Color(0xFFFFFEFC),
            background = Color(0xFFF4F7F4),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
