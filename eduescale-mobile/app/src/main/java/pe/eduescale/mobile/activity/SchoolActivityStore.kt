package pe.eduescale.mobile.activity

interface SchoolActivityStore {
    suspend fun commentsFor(schoolKey: String, query: String = ""): List<SchoolComment>
    suspend fun addComment(schoolKey: String, text: String): List<SchoolComment>
    suspend fun deleteComment(schoolKey: String, commentId: String)
    suspend fun updateComment(schoolKey: String, commentId: String, text: String)
    suspend fun remindersFor(schoolKey: String): List<SchoolReminder>
    suspend fun allComments(): List<SchoolComment>
    suspend fun allReminders(): List<SchoolReminder>
    suspend fun expirePastReminders(nowMillis: Long = System.currentTimeMillis())
}
