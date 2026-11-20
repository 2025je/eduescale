package pe.eduescale.mobile.ui.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ThemeSettingsStore {
    fun read(): ThemeMode
    fun save(mode: ThemeMode)
}

class SharedPreferencesThemeSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : ThemeSettingsStore {
    private val preferences = context.getSharedPreferences(THEME_PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): ThemeMode =
        preferences.getString(THEME_MODE_PREFERENCE_KEY, null)?.let(ThemeMode::fromPreference)
            ?: if (preferences.contains(DARK_THEME_PREFERENCE_KEY)) {
                if (preferences.getBoolean(DARK_THEME_PREFERENCE_KEY, false)) ThemeMode.DARK else ThemeMode.LIGHT
            } else {
                ThemeMode.SYSTEM
            }

    override fun save(mode: ThemeMode) {
        preferences.edit().putString(THEME_MODE_PREFERENCE_KEY, mode.preferenceValue)
            .remove(DARK_THEME_PREFERENCE_KEY).apply()
    }
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val store: ThemeSettingsStore,
) : ViewModel() {
    private val mutableMode = MutableStateFlow(store.read())
    val mode = mutableMode.asStateFlow()

    init {
        store.save(mutableMode.value)
    }

    fun setMode(mode: ThemeMode) {
        store.save(mode)
        mutableMode.value = mode
    }
}
