package pe.eduescale.mobile.ui

import pe.eduescale.mobile.activity.ActivityBackupEvent
import pe.eduescale.mobile.ui.files.SchoolFileImportEvent

internal class AppEventCoordinator(
    private val restoreReminders: (Boolean) -> Unit,
    private val refreshActivity: () -> Unit,
    private val fileLoaded: (SchoolFileImportEvent.Loaded) -> Unit,
    private val showMessage: (String) -> Unit,
) {
    fun onBackupEvent(event: ActivityBackupEvent) {
        when (event) {
            is ActivityBackupEvent.Message -> showMessage(event.text)
            ActivityBackupEvent.Restored -> {
                restoreReminders(true)
                refreshActivity()
            }
        }
    }

    fun onFileImportEvent(event: SchoolFileImportEvent) {
        when (event) {
            is SchoolFileImportEvent.Loaded -> fileLoaded(event)
        }
    }

    fun onResume() {
        restoreReminders(false)
        refreshActivity()
    }
}
