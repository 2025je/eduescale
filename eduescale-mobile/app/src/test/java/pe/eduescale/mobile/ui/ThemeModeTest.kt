package pe.eduescale.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.eduescale.mobile.ui.theme.ThemeMode

class ThemeModeTest {
    @Test
    fun systemModeFollowsTheDeviceTheme() {
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDarkTheme = false))
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDarkTheme = true))
    }

    @Test
    fun explicitModesOverrideTheDeviceTheme() {
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDarkTheme = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemDarkTheme = false))
    }
}
