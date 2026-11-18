package app.giftiary.presentation.giftideas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.usecase.GiftIdeaInputError
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The gift idea editor: what, for whom and when, then the optional details, as on the board. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class GiftIdeaEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter")
    private val birthday = occasion(peter.id)
    private val adding = GiftIdeaEditorUiState(
        isNew = true,
        status = GiftIdeaEditorStatus.EDITING,
        recipientId = peter.id,
        activePeople = listOf(peter),
        peopleLoaded = true,
        recipientOccasions = listOf(birthday),
    )
    private val editing = adding.copy(
        isNew = false,
        name = "Wireless headphones",
        savedName = "Wireless headphones",
        occasionId = birthday.id,
        ideaStatus = GiftIdeaStatus.PLANNED,
        estimatedPrice = "45000",
        productUrl = "https://example.org/h",
        note = "For travelling",
    )

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(adding)
    private var shown = false

    private val actions = GiftIdeaEditorActions(
        onBack = { events += "back" },
        onOpenPeople = { events += "people" },
        onRecipientChange = { events += "recipient ${it.name}" },
        onOccasionChange = { events += "occasion" },
        onNameChange = { events += "name $it" },
        onStatusChange = { events += "status $it" },
        onEstimatedPriceChange = { events += "estimate $it" },
        onActualPriceChange = { events += "actual $it" },
        onProductUrlChange = { events += "url $it" },
        onNoteChange = { events += "note $it" },
        onSave = { events += "save" },
        onWriteErrorShown = { events += "error shown" },
        onDeleteRequested = { events += "delete" },
        onDeleteDismissed = { events += "dismiss" },
        onDeleteConfirmed = { events += "confirm" },
    )

    private fun show(state: GiftIdeaEditorUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent { GiftiaryTheme { GiftIdeaEditorScreen(state = this.state, actions = actions) } }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    private fun assertTopToBottom(texts: List<String>) {
        val tops = texts.map(::top)
        assertEquals("top to bottom: $texts", tops.sorted(), tops)
    }

    @Test
    fun `adding reads idea, for whom, occasion, the photo, then the details toggle and save`() {
        show(adding)

        assertTopToBottom(
            listOf(
                "Add a gift idea", "Save an idea for someone, before you forget it.", "Gift idea", "For", "Peter",
                "Occasion (optional)", "Photo", "Add more details (optional)", "Save idea",
            ),
        )
        compose.onNodeWithText("Add photo").performScrollTo().performClick()
        compose.onNodeWithText("Take photo").assertExists()
        compose.onNodeWithText("Estimated price", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the details keep every current field, with the photo above them`() {
        show(adding.copy(estimatedPrice = "45000"))

        compose.onNodeWithText("Add more details (optional)").performClick()
        assertTopToBottom(listOf("Photo", "Fewer details", "Status", "Estimated price", "Product link", "Note", "Save idea"))

        compose.onNodeWithText("Fewer details").performClick()
        compose.onNodeWithText("Add more details (optional)").performClick()
        compose.onNodeWithText("45000").assertExists()
    }

    @Test
    fun `editing opens with its values and details, including a bought idea's actual price`() {
        show(editing.copy(ideaStatus = GiftIdeaStatus.PURCHASED, actualPrice = "42990"))

        compose.onNodeWithText("Edit gift idea").assertExists()
        listOf("Wireless headphones", "Peter", "45000", "42990", "https://example.org/h", "For travelling").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        assertTopToBottom(listOf("Estimated price", "Actual price", "Product link"))
    }

    @Test
    fun `fields, status and save reach the editor`() {
        show(editing)

        compose.onNodeWithText("Gift idea").performTextInput("Big ")
        compose.onNodeWithText("Purchased").performClick()
        compose.onNodeWithText("Save idea").performClick()
        assertEquals(listOf("name Big Wireless headphones", "status PURCHASED", "save"), events)
    }

    @Test
    fun `errors stay with their fields, and a price error opens the details`() {
        show(adding.copy(errors = setOf(GiftIdeaInputError.NAME_REQUIRED), estimatedPrice = "x", estimatedPriceError = PriceError.INVALID))

        compose.onNodeWithText("Enter the idea", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Enter a valid price", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `adding offers no delete`() {
        show(adding)

        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
    }

    @Test
    fun `editing keeps delete in the header's options, not beside Save`() {
        show(editing)

        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
        val more = compose.onNodeWithContentDescription("More options").getBoundsInRoot()
        assertTrue("the options sit in the header, above the title", more.bottom <= top("Edit gift idea"))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete gift idea").performClick()
        assertEquals(listOf("delete"), events)
    }

    @Test
    fun `while an edit is saved, Save and Delete cannot be pressed again`() {
        show(editing.copy(isBusy = true))

        compose.onNode(hasText("Save idea") and hasClickAction()).assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete gift idea").assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `the confirmation names the idea, and cancel and delete reach the editor`() {
        show(editing.copy(name = "Headphones", confirmingDelete = true))

        compose.onNodeWithText("Delete Wireless headphones?").assertExists()
        compose.onNodeWithText("This idea will be removed from your plans.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("dismiss", "confirm"), events)
    }

    @Test
    fun `a failed delete is said and the form stays`() {
        show(editing.copy(writeError = GiftIdeaWriteError.DELETE_FAILED))

        compose.onNodeWithText("Could not delete. Please try again.").assertExists()
        compose.onNodeWithText("Save idea").assertExists()
        compose.mainClock.advanceTimeBy(10_000)
        assertTrue("the error is marked shown", "error shown" in events)
    }

    @Test
    fun `with nobody to plan for, the editor sends the user to People, and back works`() {
        show(adding.copy(activePeople = emptyList(), recipientId = null))

        compose.onNodeWithText("Go to People").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("people", "back"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { GiftIdeaEditorScreen(state = editing, actions = actions) }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Ajándékötlet szerkesztése", "Alkalom (nem kötelező)", "Kevesebb részlet", "Ötlet mentése").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
