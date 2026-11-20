package pe.eduescale.mobile.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SavedSearchFile {
    suspend fun read(uri: String): List<SavedSearch>
    suspend fun write(uri: String, searches: List<SavedSearch>)
}

class AndroidSavedSearchFile @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SavedSearchFile {
    override suspend fun read(uri: String): List<SavedSearch> = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("No se pudo leer el archivo seleccionado.")
        SavedSearchJson.decode(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    override suspend fun write(uri: String, searches: List<SavedSearch>) = withContext(Dispatchers.IO) {
        val json = SavedSearchJson.encode(searches)
        val output = context.contentResolver.openOutputStream(Uri.parse(uri))
            ?: throw IOException("No se pudo escribir el archivo seleccionado.")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }
}
