package app.giftiary.presentation.people

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Adding and editing a person as a task flow: header, the two fields a person has, and Save. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1400dp")
class PersonEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<String>()
    private var state by mutableStateOf(PersonEditorUiState(isNewPerson = true, status = EditorStatus.EDITING))
    private var shown = false

    private fun show(state: PersonEditorUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                PersonEditorScreen(
                    state = this.state,
                    onBack = { actions += "back" },
                    onNameChange = { actions += "name $it" },
                    onRelationshipChange = { actions += "relationship $it" },
                    onSave = { actions += "save" },
                    onSaveErrorShown = {},
                    deletion = PersonEditorDeletion(
                        onRequested = { actions += "delete" },
                        onDismissed = { actions += "dismiss" },
                        onConfirmed = { actions += "confirm" },
                        onErrorShown = { actions += "delete error shown" },
                    ),
                )
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    @Test
    fun `adding reads header, name, relationship, then save`() {
        show(PersonEditorUiState(isNewPerson = true, status = EditorStatus.EDITING))

        val order = listOf("GIFTIARY", "Add a person", "Keep the people you care about close.", "Name", "Relationship (optional)", "Save person")
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("Add a person").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `editing is titled so and keeps the person's values, which stay labelled`() {
        show(PersonEditorUiState(isNewPerson = false, status = EditorStatus.EDITING, name = "Peter", relationship = "Brother"))

        compose.onNodeWithText("Edit person").assertExists()
        compose.onNodeWithText("Add a person").assertDoesNotExist()
        compose.onNodeWithText("Peter").assertExists()
        compose.onNodeWithText("Brother").assertExists()
        compose.onNodeWithText("Name", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Relationship (optional)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `typing, saving and going back reach the editor`() {
        show(PersonEditorUiState(isNewPerson = true, status = EditorStatus.EDITING))

        compose.onNodeWithText("Name").performTextInput("Anna")
        compose.onNodeWithText("Save person").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("name Anna", "save", "back"), actions)
    }

    @Test
    fun `a missing name is said under the field`() {
        show(PersonEditorUiState(isNewPerson = true, status = EditorStatus.EDITING, showNameRequired = true))

        compose.onNodeWithText("Enter a name", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `there is no person photo to pretend`() {
        show(PersonEditorUiState(isNewPerson = false, status = EditorStatus.EDITING, name = "Peter"))

        listOf("Add photo", "Important dates", "Birthday").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `adding offers no delete`() {
        show(PersonEditorUiState(isNewPerson = true, status = EditorStatus.EDITING, name = "Peter"))

        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
    }

    @Test
    fun `editing keeps delete in the header's options, not beside Save`() {
        show(PersonEditorUiState(isNewPerson = false, status = EditorStatus.EDITING, name = "Pete", savedName = "Peter"))

        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
        val more = compose.onNodeWithContentDescription("More options").getBoundsInRoot()
        assertTrue("the options sit in the header, above the title", more.bottom <= top("Edit person"))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete Peter").performClick()
        assertEquals(listOf("delete"), actions)
    }

    @Test
    fun `the confirmation names the person, and cancel and delete reach the editor`() {
        show(PersonEditorUiState(isNewPerson = false, status = EditorStatus.EDITING, name = "Pete", savedName = "Peter", confirmingDelete = true))

        compose.onNodeWithText("Delete Peter?").assertExists()
        compose.onNodeWithText("They will be removed from your people.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("dismiss", "confirm"), actions)
    }

    @Test
    fun `a failed delete is said and the form stays`() {
        show(PersonEditorUiState(isNewPerson = false, status = EditorStatus.EDITING, name = "Peter", savedName = "Peter", deleteFailed = true))

        compose.onNodeWithText("Could not delete. Please try again.").assertExists()
        compose.onNodeWithText("Save person").assertExists()
        compose.mainClock.advanceTimeBy(10_000)
        assertTrue("the error is marked shown", "delete error shown" in actions)
    }

    @Test
    fun `a person that cannot be loaded keeps the way back`() {
        show(PersonEditorUiState(isNewPerson = false, status = EditorStatus.NOT_FOUND))

        compose.onNodeWithText("This person no longer exists.").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), actions)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h1400dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme {
                    PersonEditorScreen(PersonEditorUiState(isNewPerson = true, status = EditorStatus.EDITING), {}, {}, {}, {}, {}, PersonEditorDeletion({}, {}, {}, {}))
                }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Tartsd közel azokat, akik fontosak neked.", "Kapcsolat (nem kötelező)", "Személy mentése").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
