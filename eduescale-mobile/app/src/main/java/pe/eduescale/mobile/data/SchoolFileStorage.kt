package pe.eduescale.mobile.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class SchoolFileStorage(private val directory: File) {
    private val savedFile = File(directory, SAVED_FILE_NAME)

    fun save(input: InputStream, password: String? = null): SchoolFile {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("No se pudo guardar el archivo seleccionado.")
        }

        val temporaryFile = File.createTempFile("eduescale-import-", ".tmp", directory)
        val decodedFile = File.createTempFile("eduescale-decoded-", ".tmp", directory)
        try {
            FileOutputStream(temporaryFile).use { output -> input.copyTo(output) }
            FileInputStream(temporaryFile).use { source ->
                EduEscaleEncryption.openDecoded(source, password).use { decoded ->
                    FileOutputStream(decodedFile).use(decoded::copyTo)
                }
            }
            val parsedFile = FileInputStream(decodedFile).use(SchoolDataReader::read)
            replaceSavedFile(decodedFile)
            return parsedFile
        } finally {
            temporaryFile.delete()
            decodedFile.delete()
        }
    }

    fun restore(): SchoolFile? {
        if (!savedFile.isFile) {
            return null
        }
        return FileInputStream(savedFile).use(SchoolDataReader::read)
    }

    private fun replaceSavedFile(temporaryFile: File) {
        val backupFile = File(directory, "$SAVED_FILE_NAME.backup")
        if (backupFile.exists() && !backupFile.delete()) {
            throw IOException("No se pudo actualizar el archivo guardado.")
        }

        val hadSavedFile = savedFile.exists()
        if (hadSavedFile && !savedFile.renameTo(backupFile)) {
            throw IOException("No se pudo actualizar el archivo guardado.")
        }
        if (!temporaryFile.renameTo(savedFile)) {
            if (hadSavedFile && !backupFile.renameTo(savedFile)) {
                throw IOException("No se pudo reemplazar ni restaurar el archivo guardado.")
            }
            throw IOException("No se pudo guardar el archivo seleccionado.")
        }
        backupFile.delete()
    }

    private companion object {
        const val SAVED_FILE_NAME = "selected.eduescale"
    }
}
