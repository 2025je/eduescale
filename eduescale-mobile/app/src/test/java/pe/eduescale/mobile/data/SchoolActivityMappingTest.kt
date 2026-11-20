package pe.eduescale.mobile.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import pe.eduescale.mobile.activity.SchoolActivityBackup
import pe.eduescale.mobile.activity.SchoolComment
import pe.eduescale.mobile.activity.SchoolReminder

class SchoolActivityMappingTest {
    @Test
    fun databaseEntitiesRoundTripAllFields() {
        val comment = SchoolComment("comment", "school", "nota", 10, 20)
        val reminder = SchoolReminder("reminder", "school", "Colegio", "llamar", 30, null)
        assertEquals(comment, comment.toEntity().toModel())
        assertEquals(reminder, reminder.toEntity().toModel())
        assertEquals(reminder.copy(status = SchoolReminder.STATUS_CANCELLED),
            reminder.copy(status = SchoolReminder.STATUS_CANCELLED).toEntity().toModel())
    }

    @Test
    fun backupJsonRemainsCompatible() {
        val json = """{"format":"eduescale-activity","version":1,"comments":[{"id":"c","schoolKey":"s","text":"nota","createdAtMillis":1,"updatedAtMillis":2}],"reminders":[{"id":"r","schoolKey":"s","schoolName":"Colegio","message":"llamar","scheduledAtMillis":3,"status":"CANCELLED"}]}"""
        val gson = Gson()
        val backup = gson.fromJson(json, SchoolActivityBackup::class.java)
        assertEquals(SchoolComment("c", "s", "nota", 1, 2), backup.comments.single())
        assertEquals(SchoolReminder("r", "s", "Colegio", "llamar", 3, "CANCELLED"), backup.reminders.single())
        assertEquals(gson.fromJson(json, com.google.gson.JsonObject::class.java),
            gson.toJsonTree(backup).asJsonObject)
    }
}
