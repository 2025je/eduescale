package pe.eduescale.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeViewModelTest {
    @Test
    fun readsAndPersistsThemeChangesThroughStore() {
        val store = FakeStore(ThemeMode.DARK)
        val viewModel = ThemeViewModel(store)
        assertEquals(ThemeMode.DARK, viewModel.mode.value)
        viewModel.setMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, viewModel.mode.value)
        assertEquals(ThemeMode.LIGHT, store.mode)
        assertEquals(ThemeMode.LIGHT, ThemeViewModel(store).mode.value)
    }

    private class FakeStore(var mode: ThemeMode) : ThemeSettingsStore {
        override fun read() = mode
        override fun save(mode: ThemeMode) { this.mode = mode }
    }
}
