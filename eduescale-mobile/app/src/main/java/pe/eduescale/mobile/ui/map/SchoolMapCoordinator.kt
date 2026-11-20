package pe.eduescale.mobile.ui.map

import pe.eduescale.mobile.activity.SchoolActivityViewModel
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.files.SchoolFileImportEvent

internal interface SchoolMapPort {
    fun onSchoolFileLoaded(file: SchoolFile?, schools: List<SchoolRecord>)
    fun updateMapData()
    fun focusOnNextFile()
    fun focusSelectedSchoolFromList(school: SchoolRecord)
    fun clearSelectionState()
}

internal class SchoolMapCoordinator(
    private val schools: EduEscaleViewModel,
    private val activity: SchoolActivityViewModel,
    private val map: SchoolMapPort,
) {
    private var observedFile: SchoolFile? = null
    private var observedVisibleSchools: List<SchoolRecord>? = null
    private var observedSelectedSchoolId: Int? = null

    fun synchronize(state: EduEscaleUiState) {
        activity.synchronizeSelection(state.selectedSchool)
        if (observedFile !== state.schoolFile) {
            activity.setHistorySchools(state.schoolFile)
        }
        map.onSchoolFileLoaded(state.schoolFile, state.visibleSchools)
        val selectedSchoolId = state.selectedSchool?.id
        if (observedFile !== state.schoolFile ||
            observedVisibleSchools != state.visibleSchools ||
            observedSelectedSchoolId != selectedSchoolId
        ) {
            observedFile = state.schoolFile
            observedVisibleSchools = state.visibleSchools
            observedSelectedSchoolId = selectedSchoolId
            map.updateMapData()
        }
    }

    fun fileLoaded(event: SchoolFileImportEvent.Loaded) {
        if (event.fromUser) map.focusOnNextFile()
        schools.setSchoolFile(event.result.file, event.result.name)
    }

    fun selectSchool(school: SchoolRecord, focusMap: Boolean = false) {
        schools.selectSchool(school)
        activity.selectSchool(school)
        if (focusMap) map.focusSelectedSchoolFromList(school)
    }

    fun clearSelection() {
        map.clearSelectionState()
        schools.selectSchool(null)
    }

    fun refreshActivity(schoolKey: String? = null) {
        activity.refreshActivityHistory()
        schools.state.value.selectedSchool
            ?.takeIf { schoolKey == null || it.activityKey() == schoolKey }
            ?.let(activity::refreshSelectedSchool)
    }
}
