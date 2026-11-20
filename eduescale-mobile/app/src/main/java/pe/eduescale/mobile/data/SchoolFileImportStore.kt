package pe.eduescale.mobile.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedSchoolFile(val file: SchoolFile, val name: String)

interface SchoolFileImportStore {
    suspend fun importFile(uri: String, password: String?): ImportedSchoolFile
    suspend fun restore(): ImportedSchoolFile?
}

class AndroidSchoolFileImportStore @Inject constructor(
    @ApplicationContext context: Context,
    private val storage: SchoolFileStorage,
) : SchoolFileImportStore {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences(FILE_PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun importFile(uri: String, password: String?): ImportedSchoolFile =
        withContext(Dispatchers.IO) {
            val document = Uri.parse(uri)
            val name = displayName(document)
            val input = resolver.openInputStream(document)
                ?: throw IOException("No se pudo abrir el archivo seleccionado.")
            val file = input.use { storage.save(it, password) }
            preferences.edit().putString(LAST_FILE_NAME, name).apply()
            ImportedSchoolFile(file, name)
        }

    override suspend fun restore(): ImportedSchoolFile? = withContext(Dispatchers.IO) {
        storage.restore()?.let {
            ImportedSchoolFile(it, preferences.getString(LAST_FILE_NAME, null) ?: DEFAULT_FILE_NAME)
        }
    }

    private fun displayName(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) {
                cursor.getString(column)?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: DEFAULT_FILE_NAME
    }

    private companion object {
        const val FILE_PREFERENCES = "eduescale_file"
        const val LAST_FILE_NAME = "last_file_name"
        const val DEFAULT_FILE_NAME = "Archivo EduEscale"
    }
}
