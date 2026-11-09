package pe.eduescale.mobile.data

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/** Exports and imports the JSON backup of comments and reminders. */
class ActivityBackupController(
    private val scope: LifecycleCoroutineScope,
    private val contentResolver: ContentResolver,
    private val repository: SchoolActivityRepository,
    private val showMessage: (String) -> Unit,
    private val onRestored: suspend () -> Unit,
) {
    fun export(uri: Uri) {
        scope.launch {
            try {
                val backup = repository.exportBackup()
                val output = contentResolver.openOutputStream(uri)
                    ?: throw IOException("No se pudo crear el archivo de respaldo.")
                OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                    Gson().toJson(backup, writer)
                }
                showMessage("Copia exportada correctamente.")
            } catch (error: IOException) {
                showMessage(error.message ?: "No se pudo exportar la copia.")
            } catch (error: SQLiteException) {
                showMessage(error.message ?: "No se pudo leer el historial para exportarlo.")
            } catch (error: SecurityException) {
                showMessage("Android no permitió escribir el archivo de respaldo.")
            }
        }
    }

    fun import(uri: Uri) {
        scope.launch {
            try {
                val backup = contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        Gson().fromJson(reader, SchoolActivityBackup::class.java)
                    }
                } ?: throw IOException("No se pudo abrir el archivo de respaldo.")
                repository.importBackup(backup)
                onRestored()
                showMessage("Copia restaurada; los registros se combinaron con los actuales.")
            } catch (error: JsonParseException) {
                showMessage("El archivo de respaldo no contiene un JSON válido.")
            } catch (error: IllegalArgumentException) {
                showMessage(error.message ?: "El archivo de respaldo no es compatible.")
            } catch (error: IOException) {
                showMessage(error.message ?: "No se pudo leer el archivo de respaldo.")
            } catch (error: SQLiteException) {
                showMessage(error.message ?: "No se pudo guardar la copia restaurada.")
            } catch (error: SecurityException) {
                showMessage("Android no permitió leer el archivo de respaldo.")
            }
        }
    }
}
