package pe.eduescale.mobile.data

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Imports and restores the school spreadsheet off the main thread. */
class SchoolImportController(
    private val contentResolver: ContentResolver,
    private val storage: SchoolFileStorage,
    private val preferences: SharedPreferences,
    private val isActive: () -> Boolean,
    private val onLoaded: (file: SchoolFile, fileName: String, fromUser: Boolean) -> Unit,
    private val onImporting: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun import(uri: Uri) {
        onImporting()
        executor.execute {
            try {
                val fileName = displayName(uri)
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("No se pudo abrir el archivo seleccionado.")
                val parsed = input.use(storage::save)
                preferences.edit().putString(LAST_FILE_NAME, fileName).apply()
                deliver(parsed, fileName, fromUser = true)
            } catch (error: IOException) {
                onError(error.message ?: "No se pudo leer el archivo.")
            } catch (error: IllegalArgumentException) {
                onError(error.message ?: "El archivo no es v\u00e1lido.")
            } catch (error: SecurityException) {
                onError("Android no permiti\u00f3 leer el archivo seleccionado.")
            }
        }
    }

    fun restore() {
        executor.execute {
            try {
                val parsed = storage.restore() ?: return@execute
                val fileName = preferences.getString(LAST_FILE_NAME, null) ?: DEFAULT_FILE_NAME
                deliver(parsed, fileName, fromUser = false)
            } catch (error: IOException) {
                onError(error.message ?: "No se pudo restaurar el archivo guardado.")
            } catch (error: IllegalArgumentException) {
                onError(error.message ?: "El archivo guardado no es v\u00e1lido.")
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun deliver(file: SchoolFile, fileName: String, fromUser: Boolean) {
        mainHandler.post {
            if (isActive()) {
                onLoaded(file, fileName, fromUser)
            }
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameColumn)?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_FILE_NAME
    }

    companion object {
        const val FILE_PREFERENCES = "eduescale_file"
        const val LAST_FILE_NAME = "last_file_name"
        private const val DEFAULT_FILE_NAME = "Archivo EduScale"
    }
}
