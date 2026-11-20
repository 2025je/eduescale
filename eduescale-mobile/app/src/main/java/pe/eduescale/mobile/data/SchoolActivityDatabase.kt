package pe.eduescale.mobile.data

import android.content.Context
import android.database.sqlite.SQLiteException
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
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import pe.eduescale.mobile.activity.SchoolActivityStore
import pe.eduescale.mobile.activity.ReminderStore
import pe.eduescale.mobile.activity.ActivityBackupStore
import pe.eduescale.mobile.activity.ActivityStorageException
import pe.eduescale.mobile.activity.SchoolActivityBackup
import pe.eduescale.mobile.activity.SchoolComment
import pe.eduescale.mobile.activity.SchoolReminder
import pe.eduescale.mobile.activity.reactivatedWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Entity(
    tableName = "school_comments",
    indices = [Index(value = ["school_key", "created_at_millis"])],
)
internal data class SchoolCommentEntity(
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
internal data class SchoolReminderEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "school_key") val schoolKey: String,
    @androidx.room.ColumnInfo(name = "school_name") val schoolName: String,
    val message: String,
    @androidx.room.ColumnInfo(name = "scheduled_at_millis") val scheduledAtMillis: Long,
    @androidx.room.ColumnInfo(name = "status", defaultValue = "'PENDING'")
    val status: String? = SchoolReminder.STATUS_PENDING,
)

internal fun SchoolCommentEntity.toModel() =
    SchoolComment(id, schoolKey, text, createdAtMillis, updatedAtMillis)

internal fun SchoolComment.toEntity() =
    SchoolCommentEntity(id, schoolKey, text, createdAtMillis, updatedAtMillis)

internal fun SchoolReminderEntity.toModel() =
    SchoolReminder(id, schoolKey, schoolName, message, scheduledAtMillis, status)

internal fun SchoolReminder.toEntity() =
    SchoolReminderEntity(id, schoolKey, schoolName, message, scheduledAtMillis, status)

@Dao
internal interface SchoolActivityDao {
    @Query(
        """
        SELECT * FROM school_comments
        WHERE school_key = :schoolKey
          AND text LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY created_at_millis DESC
        """,
    )
    suspend fun commentsForSchool(schoolKey: String, query: String): List<SchoolCommentEntity>

    @Query("SELECT * FROM school_comments ORDER BY created_at_millis DESC")
    suspend fun allComments(): List<SchoolCommentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComment(comment: SchoolCommentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importComments(comments: List<SchoolCommentEntity>)

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
    suspend fun remindersForSchool(schoolKey: String): List<SchoolReminderEntity>

    @Query("SELECT * FROM school_reminders ORDER BY scheduled_at_millis DESC")
    suspend fun allReminders(): List<SchoolReminderEntity>

    @Query("SELECT * FROM school_reminders WHERE id = :reminderId LIMIT 1")
    suspend fun reminderById(reminderId: String): SchoolReminderEntity?

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
    ): List<SchoolReminderEntity>

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
    suspend fun insertReminder(reminder: SchoolReminderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importReminders(reminders: List<SchoolReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: SchoolReminderEntity): Int

    @Query("DELETE FROM school_reminders WHERE id = :reminderId")
    suspend fun deleteReminder(reminderId: String)
}

@Database(
    entities = [SchoolCommentEntity::class, SchoolReminderEntity::class],
    version = 3,
    exportSchema = false,
)
internal abstract class SchoolActivityDatabase : RoomDatabase() {
    abstract fun schoolActivityDao(): SchoolActivityDao

    companion object {
        private const val DATABASE_NAME = "school-activity.db"
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE school_comments ADD COLUMN updated_at_millis INTEGER",
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
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

@Singleton
class SchoolActivityRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SchoolActivityStore, ReminderStore, ActivityBackupStore {
    private val applicationContext = context.applicationContext
    private val database = SchoolActivityDatabase.getInstance(applicationContext)
    private val dao = database.schoolActivityDao()
    private val migrationPreferences = applicationContext.getSharedPreferences(
        MIGRATION_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val migrationMutex = Mutex()

    override suspend fun commentsFor(
        schoolKey: String,
        query: String,
    ): List<SchoolComment> = storageOperation {
        migrateLegacyData()
        dao.commentsForSchool(schoolKey, query.trim()).map(SchoolCommentEntity::toModel)
    }

    override suspend fun addComment(schoolKey: String, text: String): List<SchoolComment> = storageOperation {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "Escribe un comentario antes de guardarlo." }
        migrateLegacyData()
        dao.insertComment(
            SchoolCommentEntity(
                id = UUID.randomUUID().toString(),
                schoolKey = schoolKey,
                text = normalizedText,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        dao.commentsForSchool(schoolKey, "").map(SchoolCommentEntity::toModel)
    }

    override suspend fun deleteComment(schoolKey: String, commentId: String) = storageOperation {
        migrateLegacyData()
        dao.deleteComment(schoolKey, commentId)
    }

    override suspend fun updateComment(
        schoolKey: String,
        commentId: String,
        text: String,
    ) = storageOperation {
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

    override suspend fun remindersFor(schoolKey: String): List<SchoolReminder> = storageOperation {
        migrateLegacyData()
        dao.remindersForSchool(schoolKey).map(SchoolReminderEntity::toModel)
    }

    override suspend fun allComments(): List<SchoolComment> = storageOperation {
        migrateLegacyData()
        dao.allComments().map(SchoolCommentEntity::toModel)
    }

    override suspend fun allReminders(): List<SchoolReminder> = storageOperation {
        migrateLegacyData()
        dao.allReminders().map(SchoolReminderEntity::toModel)
    }

    override suspend fun pendingReminders(): List<SchoolReminder> = storageOperation {
        expirePastReminders()
        dao.pendingReminders(
            pendingStatus = SchoolReminder.STATUS_PENDING,
            nowMillis = System.currentTimeMillis(),
        ).map(SchoolReminderEntity::toModel)
    }

    override suspend fun expirePastReminders(nowMillis: Long) = storageOperation {
        migrateLegacyData()
        dao.expirePastReminders(
            pendingStatus = SchoolReminder.STATUS_PENDING,
            expiredStatus = SchoolReminder.STATUS_EXPIRED,
            nowMillis = nowMillis,
        )
    }

    suspend fun markReminderTriggered(reminderId: String) = storageOperation {
        migrateLegacyData()
        dao.updatePendingReminderStatus(
            reminderId = reminderId,
            pendingStatus = SchoolReminder.STATUS_PENDING,
            newStatus = SchoolReminder.STATUS_TRIGGERED,
        )
    }

    override suspend fun cancelReminder(reminderId: String) = storageOperation {
        migrateLegacyData()
        dao.updatePendingReminderStatus(
            reminderId = reminderId,
            pendingStatus = SchoolReminder.STATUS_PENDING,
            newStatus = SchoolReminder.STATUS_CANCELLED,
        )
    }

    override suspend fun addReminder(
        schoolKey: String,
        schoolName: String,
        message: String,
        scheduledAtMillis: Long,
    ): SchoolReminder = storageOperation {
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
        dao.insertReminder(reminder.toEntity())
        reminder
    }

    override suspend fun updateReminder(
        reminderId: String,
        message: String,
        scheduledAtMillis: Long,
    ): SchoolReminder = storageOperation {
        val normalizedMessage = message.trim()
        require(normalizedMessage.isNotEmpty()) { "Escribe qué debes recordar." }
        require(scheduledAtMillis > System.currentTimeMillis()) {
            "La hora del recordatorio debe ser futura."
        }
        migrateLegacyData()
        val existing = dao.reminderById(reminderId)?.toModel()
            ?: throw IllegalArgumentException("No se encontró la alarma que quieres editar.")
        val updated = existing.reactivatedWith(normalizedMessage, scheduledAtMillis)
        check(dao.updateReminder(updated.toEntity()) == 1) {
            "No se pudo actualizar la alarma guardada."
        }
        updated
    }

    override suspend fun restoreReminder(reminder: SchoolReminder) = storageOperation {
        migrateLegacyData()
        check(dao.updateReminder(reminder.toEntity()) == 1) {
            "No se pudo restaurar la alarma anterior."
        }
    }

    override suspend fun deleteReminder(reminderId: String) = storageOperation {
        migrateLegacyData()
        dao.deleteReminder(reminderId)
    }

    override suspend fun exportBackup(): SchoolActivityBackup = storageOperation {
        migrateLegacyData()
        SchoolActivityBackup(
            comments = dao.allComments().map(SchoolCommentEntity::toModel),
            reminders = dao.allReminders().map(SchoolReminderEntity::toModel),
        )
    }

    override suspend fun importBackup(backup: SchoolActivityBackup) = storageOperation {
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
                dao.importComments(backup.comments.map(SchoolComment::toEntity))
            }
            if (normalizedReminders.isNotEmpty()) {
                dao.importReminders(normalizedReminders.map(SchoolReminder::toEntity))
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
                    dao.importComments(legacyData.comments.map(SchoolComment::toEntity))
                }
                if (legacyReminders.isNotEmpty()) {
                    dao.importReminders(legacyReminders.map(SchoolReminder::toEntity))
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

    private suspend fun <T> storageOperation(action: suspend () -> T): T =
        try {
            action()
        } catch (error: SQLiteException) {
            throw ActivityStorageException(error)
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
