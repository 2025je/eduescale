package pe.eduescale.mobile.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.eduescale.mobile.ui.schools.SavedSearchStore
import pe.eduescale.mobile.ui.theme.THEME_PREFERENCES_NAME

class SharedPreferencesSavedSearchStore @Inject constructor(
    @ApplicationContext context: Context,
) : SavedSearchStore {
    private val preferences = context.getSharedPreferences(THEME_PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): List<SavedSearch> = withContext(Dispatchers.IO) {
        preferences.getString(KEY, null)?.let(SavedSearchJson::decode) ?: emptyList()
    }

    override suspend fun save(searches: List<SavedSearch>) = withContext(Dispatchers.IO) {
        val json = SavedSearchJson.encode(searches)
        if (!preferences.edit().putString(KEY, json).commit()) {
            throw IOException("No se pudieron guardar las búsquedas.")
        }
    }

    private companion object {
        const val KEY = "saved_searches"
    }
}
