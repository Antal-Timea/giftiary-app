package app.giftiary.presentation.occasions

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.usecase.OccasionInputError
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class OccasionEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val repeats = mutableListOf<Boolean>()

    private val actions = OccasionEditorActions(
        onBack = { calls += "back" },
        onTypeChange = {},
        onTitleChange = {},
        onDayChange = {},
        onMonthChange = {},
        onYearChange = {},
        onRepeatsYearlyChange = { repeats += it },
        onSave = { calls += "save" },
        onDeleteRequested = { calls += "delete" },
        onDeleteConfirmed = { calls += "confirm" },
        onDeleteDismissed = { calls += "dismiss" },
        onErrorShown = {},
    )

    private fun show(state: OccasionEditorUiState) {
        compose.setContent { GiftiaryTheme { OccasionEditorScreen(state = state, actions = actions) } }
    }

    private fun editing(isNew: Boolean, type: OccasionType, vararg errors: OccasionInputError) = OccasionEditorUiState(
        isNew = isNew,
        status = OccasionEditorStatus.EDITING,
        type = type,
        errors = errors.toSet(),
    )

    @Test
    fun `Add says what it is for and has no menu`() {
        show(editing(isNew = true, type = OccasionType.BIRTHDAY))
        compose.onNodeWithText("Add occasion").assertExists()
        compose.onNodeWithText("Mark a day worth planning a gift for.").assertExists()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun `Edit deletes only through the menu and the confirmation`() {
        show(editing(isNew = false, type = OccasionType.CHRISTMAS))
        compose.onNodeWithText("Edit occasion").assertExists()
        compose.onNodeWithText("Delete occasion").assertDoesNotExist()

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete occasion").performClick()
        assertEquals(listOf("delete"), calls)
    }

    @Test
    fun `the confirmation deletes on Delete`() {
        show(editing(isNew = false, type = OccasionType.CHRISTMAS).copy(confirmingDelete = true))

        compose.onNodeWithText("Delete Christmas?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `a custom occasion asks for its name and whether it repeats`() {
        show(editing(isNew = true, type = OccasionType.CUSTOM))
        compose.onNodeWithText("Name").assertExists()
        compose.onNodeWithText("Repeats every year").assertExists()
    }

    @Test
    fun `a birthday always repeats, so it has no name or repeat control`() {
        show(editing(isNew = true, type = OccasionType.BIRTHDAY))
        compose.onNodeWithText("Name").assertDoesNotExist()
        compose.onNodeWithText("Repeats every year").assertDoesNotExist()
        compose.onNodeWithText("Year (optional)").assertExists()
    }

    // Tall enough for the whole form, so every field and message has bounds.
    @Test
    @Config(qualifiers = "w390dp-h1400dp")
    fun `each error is shown under its own field`() {
        show(
            editing(
                isNew = true,
                type = OccasionType.CUSTOM,
                OccasionInputError.TITLE_REQUIRED,
                OccasionInputError.DAY_REQUIRED,
                OccasionInputError.MONTH_REQUIRED,
                OccasionInputError.YEAR_REQUIRED,
            ).copy(repeatsYearly = false),
        )

        listOf(
            "Name" to "Enter a name for this occasion",
            "Day" to "Enter a day",
            "Month" to "Choose a month",
            "Year" to "A one-time occasion needs a year",
        ).forEach { (field, error) ->
            val label = compose.onNodeWithText(field, useUnmergedTree = true).getBoundsInRoot()
            val message = compose.onNodeWithText(error, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$error is below $field", message.top > label.top)
        }
        // Each error sits under its own field, above the next field's label.
        val dayError = compose.onNodeWithText("Enter a day", useUnmergedTree = true).getBoundsInRoot()
        val month = compose.onNodeWithText("Month", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(dayError.top < month.top)
    }

    @Test
    fun `the whole repeat row is one switch`() {
        show(editing(isNew = true, type = OccasionType.ANNIVERSARY))

        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        val row = compose.onNode(isToggleable())
        row.assertIsOn()
        assertEquals(Role.Switch, row.fetchSemanticsNode().config[SemanticsProperties.Role])
        row.performClick()
        assertEquals(listOf(false), repeats)
    }

    @Test
    fun `while saving, Save and Delete cannot be pressed again`() {
        show(editing(isNew = false, type = OccasionType.ANNIVERSARY).copy(isBusy = true))

        compose.onNode(hasText("Save") and hasClickAction()).assertIsNotEnabled().performClick()
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete occasion").performClick()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a missing occasion says so and keeps the way back`() {
        show(OccasionEditorUiState(isNew = false, status = OccasionEditorStatus.NOT_FOUND))

        compose.onNodeWithText("This occasion no longer exists.").assertExists()
        compose.onAllNodesWithText("Save").assertCountEquals(0)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}
