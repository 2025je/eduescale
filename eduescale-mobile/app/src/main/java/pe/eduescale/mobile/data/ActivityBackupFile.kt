package pe.eduescale.mobile.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.eduescale.mobile.activity.SchoolActivityBackup

interface ActivityBackupFile {
    suspend fun read(uri: String): SchoolActivityBackup
    suspend fun write(uri: String, backup: SchoolActivityBackup)
}

class AndroidActivityBackupFile @Inject constructor(
    @ApplicationContext context: Context,
) : ActivityBackupFile {
    private val resolver: ContentResolver = context.contentResolver
    private val gson = Gson()

    override suspend fun read(uri: String): SchoolActivityBackup = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("No se pudo abrir el archivo de respaldo.")
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, SchoolActivityBackup::class.java)
                ?: throw IllegalArgumentException("El archivo de respaldo está vacío.")
        }
    }

    override suspend fun write(uri: String, backup: SchoolActivityBackup) = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(Uri.parse(uri))
            ?: throw IOException("No se pudo crear el archivo de respaldo.")
        output.bufferedWriter(Charsets.UTF_8).use { writer -> gson.toJson(backup, writer) }
    }
}
