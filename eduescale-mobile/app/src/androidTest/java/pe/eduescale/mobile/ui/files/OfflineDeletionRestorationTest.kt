package pe.eduescale.mobile.ui.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.routing.RoadRoutingState

@OptIn(ExperimentalTestApi::class)
class OfflineDeletionRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirmationSurvivesRecreationAndDeletesSelectedArea() {
        var deleted: Long? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                FilesScreen(
                    roadState = RoadRoutingState(),
                    onDownloadRoadData = {},
                    file = null,
                    fileName = null,
                    importing = false,
                    error = null,
                    onPickFile = {},
                    onExportActivityBackup = {},
                    onImportActivityBackup = {},
                    offlineMapAreas = listOf(OfflineMapAreaUi(7, "Mi mapa", 1, 5, 10, 10, 100, false, true)),
                    deviceAvailableBytes = 1000,
                    storageError = null,
                    offlineMapMessage = null,
                    offlineMapMessageIsError = false,
                    onToggleOfflineMapArea = { _, _ -> },
                    onDeleteOfflineMapArea = { deleted = it },
                )
            }
        }
        compose.onNodeWithText("Eliminar").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Eliminar mapa sin conexión").assertIsDisplayed()
        compose.onNodeWithText("¿Quieres eliminar “Mi mapa” del dispositivo?").assertIsDisplayed()
        compose.onNode(hasText("Eliminar") and hasAnyAncestor(isDialog()))
            .performClick()
        assertEquals(7L, deleted)
    }
}
