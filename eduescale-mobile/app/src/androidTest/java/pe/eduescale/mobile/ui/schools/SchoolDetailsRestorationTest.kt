package pe.eduescale.mobile.ui.schools

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import pe.eduescale.mobile.activity.SchoolComment
import pe.eduescale.mobile.activity.SchoolReminder
import pe.eduescale.mobile.data.SchoolRecord

@OptIn(ExperimentalTestApi::class)
class SchoolDetailsRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editedCommentAndDialogSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                SchoolDetailsSheet(
                    school = school(),
                    userLocation = null,
                    roadState = pe.eduescale.mobile.routing.RoadRoutingState(),
                    onSheetShown = { _, _ -> },
                    onDismiss = {},
                    onDirections = {},
                    onShare = {},
                    comments = listOf(SchoolComment("comment", "school", "Anterior", 1)),
                    reminders = listOf(
                        SchoolReminder("reminder", "school", "Colegio", "Llamar", Long.MAX_VALUE),
                    ),
                    commentSearchQuery = "",
                    onAddComment = {},
                    onDeleteComment = {},
                    onEditComment = { _, _ -> },
                    onSearchComments = {},
                    onAddReminder = {},
                    reminderDatePrompt = null,
                    onReminderDateSelected = { _, _, _ -> },
                    onReminderDateCancelled = {},
                    onReminderMessage = {},
                    onDeleteReminder = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Editar comentario").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Anterior"), useUnmergedTree = true)
            .performTextReplacement("Texto editado")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Editar comentario").assertIsDisplayed()
        compose.onNodeWithText("Texto editado", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("Eliminar comentario").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Eliminar comentario").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("Cancelar recordatorio").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Cancelar recordatorio").assertIsDisplayed()
        compose.onNodeWithText("¿Quieres cancelar la alarma de “Llamar”? Se conservará en el historial.")
            .assertIsDisplayed()
    }

    private fun school() = SchoolRecord(
        id = 1,
        departamento = "Lima",
        provincia = "Lima",
        distrito = "Lima",
        nombre = "Colegio",
        direccion = null,
        localidad = null,
        cp = null,
        turno = null,
        alumnos = null,
        secciones = null,
        gestion = "publica",
        codigoLocal = "1",
        niveles = listOf("primaria"),
        longitud = -77.0,
        latitud = -12.0,
    )
}
