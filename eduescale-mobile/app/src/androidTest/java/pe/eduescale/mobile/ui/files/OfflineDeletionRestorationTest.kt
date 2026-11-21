package pe.eduescale.mobile.ui.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.routing.RoadRoutingState
import pe.eduescale.mobile.ui.theme.EduEscaleTheme

@OptIn(ExperimentalTestApi::class)
class OfflineDeletionRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fileScreenTitlesRemainVisibleInBothThemes() {
        val darkTheme = mutableStateOf(true)
        compose.setContent {
            EduEscaleTheme(darkTheme = darkTheme.value) {
                Scaffold(containerColor = Color.Transparent) { _ ->
                    FilesScreen(
                        roadState = RoadRoutingState(),
                        onDownloadRoadData = {},
                        onDeleteRoadData = {},
                        file = null,
                        fileName = null,
                        importing = false,
                        error = null,
                        onPickFile = {},
                        onExportActivityBackup = {},
                        onImportActivityBackup = {},
                        offlineMapAreas = emptyList(),
                        deviceAvailableBytes = 1000,
                        storageError = null,
                        offlineMapMessage = null,
                        offlineMapMessageIsError = false,
                        onToggleOfflineMapArea = { _, _ -> },
                        onDeleteOfflineMapArea = {},
                    )
                }
            }
        }
        for (dark in listOf(true, false)) {
            compose.runOnIdle { darkTheme.value = dark }
            for (title in listOf("EduEscale", "Archivo de datos", "Mapas sin conexión")) {
                val node = compose.onNodeWithText(title).performScrollTo()
                val image = node.captureToImage().toPixelMap()
                val luminances = (0 until image.height).flatMap { y ->
                    (0 until image.width).map { x -> image[x, y].luminance() }
                }
                assertTrue(
                    "$title is not readable in ${if (dark) "dark" else "light"} theme",
                    if (dark) luminances.max() > 0.45f else luminances.min() < 0.25f,
                )
            }
        }
    }

    @Test
    fun roadDownloadShowsKnownAndUnknownProgress() {
        val downloaded = mutableStateOf(0L)
        val total = mutableStateOf(0L)
        compose.setContent {
            MaterialTheme { RoadDownloadProgress(downloaded.value, total.value) }
        }
        compose.onNodeWithText("Preparando descarga de carreteras…").assertIsDisplayed()
        compose.runOnIdle { downloaded.value = 50L * 1024 * 1024 }
        compose.onNodeWithText("Descargando: 50 MB").assertIsDisplayed()
        compose.runOnIdle { total.value = 100L * 1024 * 1024 }
        compose.onNodeWithText("Descargando: 50 MB / 100 MB · 50 %").assertIsDisplayed()
    }

    @Test
    fun confirmationSurvivesRecreationAndDeletesSelectedArea() {
        var deleted: Long? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                FilesScreen(
                    roadState = RoadRoutingState(),
                    onDownloadRoadData = {},
                    onDeleteRoadData = {},
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

    @Test
    fun roadDeletionRequiresConfirmationAndSurvivesRecreation() {
        var deleted = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                FilesScreen(
                    roadState = RoadRoutingState(available = true, storedBytes = 1024),
                    onDownloadRoadData = {},
                    onDeleteRoadData = { deleted = true },
                    file = null,
                    fileName = null,
                    importing = false,
                    error = null,
                    onPickFile = {},
                    onExportActivityBackup = {},
                    onImportActivityBackup = {},
                    offlineMapAreas = emptyList(),
                    deviceAvailableBytes = 1000,
                    storageError = null,
                    offlineMapMessage = null,
                    offlineMapMessageIsError = false,
                    onToggleOfflineMapArea = { _, _ -> },
                    onDeleteOfflineMapArea = {},
                )
            }
        }
        compose.onNodeWithText("Eliminar carreteras").performScrollTo().performClick()
        assertTrue(!deleted)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Eliminar carreteras sin conexión").assertIsDisplayed()
        compose.onNode(hasText("Eliminar") and hasAnyAncestor(isDialog())).performClick()
        assertTrue(deleted)
    }
}
