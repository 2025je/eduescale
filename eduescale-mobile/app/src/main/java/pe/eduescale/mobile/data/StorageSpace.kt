package pe.eduescale.mobile.data

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface StorageSpace {
    suspend fun availableBytes(): Long
}

class AndroidStorageSpace @Inject constructor(
    @ApplicationContext context: Context,
) : StorageSpace {
    private val path = context.filesDir.path

    override suspend fun availableBytes(): Long = withContext(Dispatchers.IO) {
        try {
            StatFs(path).availableBytes
        } catch (error: IllegalArgumentException) {
            throw IOException("No se pudo consultar el almacenamiento disponible.", error)
        }
    }
}
