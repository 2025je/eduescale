package pe.eduescale.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.eduescale.mobile.activity.ActivityBackupEvent
import pe.eduescale.mobile.data.ImportedSchoolFile
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.ui.files.SchoolFileImportEvent

class AppEventCoordinatorTest {
    @Test
    fun routesRestorationImportAndResumeInOrder() {
        val calls = mutableListOf<String>()
        val coordinator = AppEventCoordinator(
            restoreReminders = { calls.add("restore:$it") },
            refreshActivity = { calls.add("refresh") },
            fileLoaded = { calls.add("file:${it.result.name}:${it.fromUser}") },
            showMessage = { calls.add("message:$it") },
        )
        val file = SchoolFile(0, 0, emptyList(), formatVersion = 1)
        coordinator.onFileImportEvent(SchoolFileImportEvent.Loaded(ImportedSchoolFile(file, "test"), true))
        coordinator.onBackupEvent(ActivityBackupEvent.Restored)
        coordinator.onBackupEvent(ActivityBackupEvent.Message("listo"))
        coordinator.onResume()
        assertEquals(
            listOf("file:test:true", "restore:true", "refresh", "message:listo", "restore:false", "refresh"),
            calls,
        )
    }
}
