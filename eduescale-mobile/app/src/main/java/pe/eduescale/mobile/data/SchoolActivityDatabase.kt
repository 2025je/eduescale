package pe.eduescale.mobile.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Entity(
    tableName = "school_comments",
    indices = [Index(value = ["school_key", "created_at_millis"])],
)
data class SchoolComment(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "school_key") val schoolKey: String,
    val text: String,
    @androidx.room.ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @androidx.room.ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long? = null,
)

@Entity(
    tableName = "school_reminders",
    indices = [Index(value = ["school_key", "scheduled_at_millis"])],
)
data class SchoolReminder(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "school_key") val schoolKey: String,
    @androidx.room.ColumnInfo(name = "school_name") val schoolName: String,
    val message: String,
    @androidx.room.ColumnInfo(name = "scheduled_at_millis") val scheduledAtMillis: Long,
    @androidx.room.ColumnInfo(name = "status", defaultValue = "'PENDING'")
    val status: String? = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_TRIGGERED = "TRIGGERED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_EXPIRED = "EXPIRED"
        val VALID_STATUSES = setOf(
            STATUS_PENDING,
            STATUS_TRIGGERED,
            STATUS_CANCELLED,
            STATUS_EXPIRED,
        )
    }
}

internal fun SchoolReminder.reactivatedWith(
    message: String,
    scheduledAtMillis: Long,
): SchoolReminder = copy(
    message = message,
    scheduledAtMillis = scheduledAtMillis,
    status = SchoolReminder.STATUS_PENDING,
)

data class SchoolActivityBackup(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val comments: List<SchoolComment> = emptyList(),
    val reminders: List<SchoolReminder> = emptyList(),
) {
    companion object {
        const val BACKUP_FORMAT = "eduescale-activity"
        const val BACKUP_VERSION = 1
    }
}

@Dao
interface SchoolActivityDao {
    @Query(
        """
        SELECT * FROM school_comments
        WHERE school_key = :schoolKey
          AND text LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY created_at_millis DESC
        """,
    )
    suspend fun commentsForSchool(schoolKey: String, query: String): List<SchoolComment>

    @Query("SELECT * FROM school_comments ORDER BY created_at_millis DESC")
    suspend fun allComments(): List<SchoolComment>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComment(comment: SchoolComment)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importComments(comments: List<SchoolComment>)

    @Query(
        "DELETE FROM school_comments WHERE school_key = :schoolKey AND id = :commentId",
    )
    suspend fun deleteComment(schoolKey: String, commentId: String)

    @Query(
        """
        UPDATE school_comments
        SET text = :text, updated_at_millis = :updatedAtMillis
        WHERE school_key = :schoolKey AND id = :commentId
        """,
    )
    suspend fun updateComment(
        schoolKey: String,
        commentId: String,
        text: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        SELECT * FROM school_reminders
        WHERE school_key = :schoolKey
        ORDER BY scheduled_at_millis DESC
        """,
    )
    suspend fun remindersForSchool(schoolKey: String): List<SchoolReminder>

    @Query("SELECT * FROM school_reminders ORDER BY scheduled_at_millis DESC")
    suspend fun allReminders(): List<SchoolReminder>

    @Query("SELECT * FROM school_reminders WHERE id = :reminderId LIMIT 1")
    suspend fun reminderById(reminderId: String): SchoolReminder?

    @Query(
        """
        SELECT * FROM school_reminders
        WHERE status = :pendingStatus AND scheduled_at_millis > :nowMillis
        ORDER BY scheduled_at_millis
        """,
    )
    suspend fun pendingReminders(
        pendingStatus: String,
        nowMillis: Long,
    ): List<SchoolReminder>

    @Query(
        """
        UPDATE school_reminders
        SET status = :newStatus
        WHERE id = :reminderId AND status = :pendingStatus
        """,
    )
    suspend fun updatePendingReminderStatus(
        reminderId: String,
        pendingStatus: String,
        newStatus: String,
    )

    @Query(
        """
        UPDATE school_reminders
        SET status = :expiredStatus
        WHERE status = :pendingStatus AND scheduled_at_millis <= :nowMillis
        """,
    )
    suspend fun expirePastReminders(
        pendingStatus: String,
        expiredStatus: String,
        nowMillis: Long,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminder(reminder: SchoolReminder)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importReminders(reminders: List<SchoolReminder>)

    @Update
    suspend fun updateReminder(reminder: SchoolReminder): Int

    @Query("DELETE FROM school_reminders WHERE id = :reminderId")
    suspend fun deleteReminder(reminderId: String)
}

@Database(
    entities = [SchoolComment::class, SchoolReminder::class],
    version = 3,
    exportSchema = false,
)
abstract class SchoolActivityDatabase : RoomDatabase() {
    abstract fun schoolActivityDao(): SchoolActivityDao

    companion object {
        private const val DATABASE_NAME = "school-activity.db"
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE school_comments ADD COLUMN updated_at_millis INTEGER",
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE school_reminders ADD COLUMN status TEXT DEFAULT 'PENDING'",
                )
            }
        }

        @Volatile
        private var instance: SchoolActivityDatabase? = null

        fun getInstance(context: Context): SchoolActivityDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SchoolActivityDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}

class SchoolActivityRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val database = SchoolActivityDatabase.getInstance(applicationContext)
    private val dao = database.schoolActivityDao()
    private val migrationPreferences = applicationContext.getSharedPreferences(
        MIGRATION_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val migrationMutex = Mutex()

    suspend fun commentsFor(
        schoolKey: String,
        query: String = "",
    ): List<SchoolComment> {
        migrateLegacyData()
        return dao.commentsForSchool(schoolKey, query.trim())
    }

    suspend fun addComment(schoolKey: String, text: String): List<SchoolComment> {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "Escribe un comentario antes de guardarlo." }
        migrateLegacyData()
        dao.insertComment(
            SchoolComment(
                id = UUID.randomUUID().toString(),
                schoolKey = schoolKey,
                text = normalizedText,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        return dao.commentsForSchool(schoolKey, "")
    }

    suspend fun deleteComment(schoolKey: String, commentId: String) {
        migrateLegacyData()
        dao.deleteComment(schoolKey, commentId)
    }

    suspend fun updateComment(
        schoolKey: String,
        commentId: String,
        text: String,
    ) {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "El comentario no puede quedar vacío." }
        migrateLegacyData()
        dao.updateComment(
            schoolKey = schoolKey,
            commentId = commentId,
            text = normalizedText,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun remindersFor(schoolKey: String): List<SchoolReminder> {
        migrateLegacyData()
        return dao.remindersForSchool(schoolKey)
    }

    suspend fun allComments(): List<SchoolComment> {
        migrateLegacyData()
        return dao.allComments()
    }

    suspend fun allReminders(): List<SchoolReminder> {
        migrateLegacyData()
        return dao.allReminders()
    }

    suspend fun pendingReminders(): List<SchoolReminder> {
        expirePastReminders()
        return dao.pendingReminders(
            pendingStatus = SchoolReminder.STATUS_PENDING,
            nowMillis = System.currentTimeMillis(),
        )
    }

    suspend fun expirePastReminders(nowMillis: Long = System.currentTimeMillis()) {
        migrateLegacyData()
        dao.expirePastReminders(
            pendingStatus = SchoolReminder.STATUS_PENDING,
            expiredStatus = SchoolReminder.STATUS_EXPIRED,
            nowMillis = nowMillis,
        )
    }

    suspend fun markReminderTriggered(reminderId: String) {
        migrateLegacyData()
        dao.updatePendingReminderStatus(
            reminderId = reminderId,
            pendingStatus = SchoolReminder.STATUS_PENDING,
            newStatus = SchoolReminder.STATUS_TRIGGERED,
        )
    }

    suspend fun cancelReminder(reminderId: String) {
        migrateLegacyData()
        dao.updatePendingReminderStatus(
            reminderId = reminderId,
            pendingStatus = SchoolReminder.STATUS_PENDING,
            newStatus = SchoolReminder.STATUS_CANCELLED,
        )
    }

    suspend fun addReminder(
        schoolKey: String,
        schoolName: String,
        message: String,
        scheduledAtMillis: Long,
    ): SchoolReminder {
        val normalizedMessage = message.trim()
        require(normalizedMessage.isNotEmpty()) { "Escribe qué debes recordar." }
        require(scheduledAtMillis > System.currentTimeMillis()) {
            "La hora del recordatorio debe ser futura."
        }
        migrateLegacyData()
        val reminder = SchoolReminder(
            id = UUID.randomUUID().toString(),
            schoolKey = schoolKey,
            schoolName = schoolName,
            message = normalizedMessage,
            scheduledAtMillis = scheduledAtMillis,
        )
        dao.insertReminder(reminder)
        return reminder
    }

    suspend fun updateReminder(
        reminderId: String,
        message: String,
        scheduledAtMillis: Long,
    ): SchoolReminder {
        val normalizedMessage = message.trim()
        require(normalizedMessage.isNotEmpty()) { "Escribe qué debes recordar." }
        require(scheduledAtMillis > System.currentTimeMillis()) {
            "La hora del recordatorio debe ser futura."
        }
        migrateLegacyData()
        val existing = dao.reminderById(reminderId)
            ?: throw IllegalArgumentException("No se encontró la alarma que quieres editar.")
        val updated = existing.reactivatedWith(normalizedMessage, scheduledAtMillis)
        check(dao.updateReminder(updated) == 1) {
            "No se pudo actualizar la alarma guardada."
        }
        return updated
    }

    suspend fun restoreReminder(reminder: SchoolReminder) {
        migrateLegacyData()
        check(dao.updateReminder(reminder) == 1) {
            "No se pudo restaurar la alarma anterior."
        }
    }

    suspend fun deleteReminder(reminderId: String) {
        migrateLegacyData()
        dao.deleteReminder(reminderId)
    }

    suspend fun exportBackup(): SchoolActivityBackup {
        migrateLegacyData()
        return SchoolActivityBackup(
            comments = dao.allComments(),
            reminders = dao.allReminders(),
        )
    }

    suspend fun importBackup(backup: SchoolActivityBackup) {
        require(backup.format == SchoolActivityBackup.BACKUP_FORMAT) {
            "El archivo no es una copia de seguridad de EduEscale."
        }
        require(backup.version == SchoolActivityBackup.BACKUP_VERSION) {
            "La versión de la copia de seguridad no es compatible."
        }
        require(backup.comments.all {
            it.id.isNotBlank() && it.schoolKey.isNotBlank() && it.text.isNotBlank()
        }) {
            "La copia de seguridad contiene comentarios no válidos."
        }
        require(backup.reminders.all {
            it.id.isNotBlank() &&
                it.schoolKey.isNotBlank() &&
                it.schoolName.isNotBlank() &&
                it.message.isNotBlank() &&
                it.scheduledAtMillis > 0 &&
                (it.status == null || it.status in SchoolReminder.VALID_STATUSES)
        }) {
            "La copia de seguridad contiene recordatorios no válidos."
        }
        val normalizedReminders = backup.reminders.map(::normalizeReminderStatus)

        migrateLegacyData()
        database.withTransaction {
            if (backup.comments.isNotEmpty()) {
                dao.importComments(backup.comments)
            }
            if (normalizedReminders.isNotEmpty()) {
                dao.importReminders(normalizedReminders)
            }
        }
    }

    private suspend fun migrateLegacyData() {
        migrationMutex.withLock {
            if (migrationPreferences.getBoolean(MIGRATION_COMPLETE, false)) {
                return
            }

            val legacyData = withContext(Dispatchers.IO) { readLegacyData() }
            val legacyReminders = legacyData.reminders.map(::normalizeReminderStatus)
            database.withTransaction {
                if (legacyData.comments.isNotEmpty()) {
                    dao.importComments(legacyData.comments)
                }
                if (legacyReminders.isNotEmpty()) {
                    dao.importReminders(legacyReminders)
                }
            }
            val saved = withContext(Dispatchers.IO) {
                migrationPreferences.edit()
                    .putBoolean(MIGRATION_COMPLETE, true)
                    .commit()
            }
            if (!saved) {
                throw IOException("No se pudo completar la migración de los comentarios guardados.")
            }
        }
    }

    private fun readLegacyData(): LegacySchoolActivityData {
        val legacyFile = File(
            File(applicationContext.filesDir, LEGACY_DIRECTORY),
            LEGACY_FILE_NAME,
        )
        if (!legacyFile.isFile) {
            return LegacySchoolActivityData()
        }
        try {
            return FileInputStream(legacyFile).use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                    Gson().fromJson(reader, LegacySchoolActivityData::class.java)
                        ?: LegacySchoolActivityData()
                }
            }
        } catch (error: JsonParseException) {
            throw IOException("No se pudieron migrar los comentarios guardados.", error)
        }
    }

    private fun normalizeReminderStatus(reminder: SchoolReminder): SchoolReminder {
        val status = reminder.status
            ?.takeIf { it in SchoolReminder.VALID_STATUSES }
            ?: SchoolReminder.STATUS_PENDING
        return if (reminder.status == status) reminder else reminder.copy(status = status)
    }

    private data class LegacySchoolActivityData(
        val comments: List<SchoolComment> = emptyList(),
        val reminders: List<SchoolReminder> = emptyList(),
    )

    private companion object {
        const val MIGRATION_PREFERENCES = "school_activity_migration"
        const val MIGRATION_COMPLETE = "legacy_json_imported"
        const val LEGACY_DIRECTORY = "activity"
        const val LEGACY_FILE_NAME = "school-activity.json"
    }
}

fun SchoolRecord.activityKey(): String {
    val localCode = codigoLocal?.trim()?.takeIf(String::isNotEmpty)
    val identityParts = if (localCode != null) {
        listOf(
            "codigo_local",
            localCode,
            departamento,
            provincia,
            distrito,
        )
    } else {
        listOf(
            "colegio",
            departamento,
            provincia,
            distrito,
            nombre,
            direccion,
            localidad,
            cp,
            gestion,
            longitud.toString(),
            latitud.toString(),
        )
    }
    val identity = identityParts.joinToString("\u001f") { it?.trim().orEmpty() }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
