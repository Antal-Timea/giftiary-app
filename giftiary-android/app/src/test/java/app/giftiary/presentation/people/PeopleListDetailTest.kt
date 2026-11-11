package app.giftiary.presentation.people

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.Person
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.gifts.ShownPhotos
import app.giftiary.presentation.home.ComingUpContent
import app.giftiary.presentation.theme.GiftiaryLayout
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.assertNear
import app.giftiary.testing.centredStart
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * People per width class: what a tap does, the panes, Back and the hand-off when a wide window
 * narrows. The state is held here the way [PeopleViewModel] holds it.
 */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class PeopleListDetailTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val anna = person("Anna")
    private val bela = person("Béla")
    private val placeholder = "Select a person to see their occasions, ideas and gifts."

    private var state by mutableStateOf(PeopleUiState())
    private var widthClass by mutableStateOf(WidthClass.EXPANDED)
    private val opened = mutableListOf<UUID>()

    private fun showPeople(
        width: WidthClass,
        content: PeopleContent = PeopleContent.Loaded(listOf(anna, bela)),
        selected: Person? = null,
    ) {
        widthClass = width
        state = PeopleUiState(content = content, selectedPersonId = selected?.id)
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(widthOf(widthClass)).fillMaxHeight()) {
                    AdaptivePeopleScreen(
                        state = state,
                        photos = ShownPhotos.of(emptyMap()),
                        comingUp = ComingUpContent.Loaded(emptyList()),
                        onQueryChange = {},
                        onAddPerson = {},
                        onOpenPerson = { opened += it },
                        onSelectPerson = { state = state.copy(selectedPersonId = it.id) },
                        onClearSelection = { state = state.copy(selectedPersonId = null) },
                        personDetail = { id -> Text("Detail of " + listOf(anna, bela).first { it.id == id }.name) },
                        widthClass = widthClass,
                    )
                }
            }
        }
    }

    private fun widthOf(widthClass: WidthClass): Dp = when (widthClass) {
        WidthClass.COMPACT -> 411.dp
        WidthClass.MEDIUM -> 800.dp
        WidthClass.EXPANDED -> 1200.dp
    }

    private fun left(text: String): Dp = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().left

    // A row's name starts after the 22 dp gutter, the 48 dp avatar and the row's 14 dp gap.
    private val nameInset = GiftiarySpacing.browseGutter + 48.dp + 14.dp

    private val backIsIntercepted: Boolean
        get() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.hasEnabledCallbacks() }

    @Test
    fun `a phone opens the person as a screen of their own, with no selection`() {
        showPeople(WidthClass.COMPACT)

        compose.onNodeWithText("Anna").performClick()

        assertEquals(listOf(anna.id), opened)
        assertNull(state.selectedPersonId)
        compose.onNodeWithText(placeholder).assertDoesNotExist()
        compose.onNodeWithText("Anna").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun `a medium window opens the person too, from a list at the readable width`() {
        showPeople(WidthClass.MEDIUM)

        // The readable measure, centred in the window, then the row's own inset.
        assertNear(centredStart(800.dp, GiftiaryLayout.readableMaxWidth) + nameInset, left("Anna"), "name start")
        compose.onNodeWithText(placeholder).assertDoesNotExist()

        compose.onNodeWithText("Anna").performClick()
        assertEquals(listOf(anna.id), opened)
        assertNull(state.selectedPersonId)
    }

    @Test
    fun `an expanded window selects the person and shows them beside the list`() {
        showPeople(WidthClass.EXPANDED)

        assertNear(nameInset, left("Anna"), "name start in the list pane")
        assertTrue("placeholder in the detail pane", left(placeholder) > 360.dp)

        compose.onNodeWithText("Anna").performClick()

        assertTrue("nothing navigated", opened.isEmpty())
        assertEquals(anna.id, state.selectedPersonId)
        assertTrue("detail beside the 360 dp list", left("Detail of Anna") > 360.dp)
        compose.onNodeWithText(placeholder).assertDoesNotExist()
        compose.onNodeWithText("Anna").assertIsSelected()
        compose.onNodeWithText("Béla").assertIsNotSelected()

        compose.onNodeWithText("Béla").performClick()
        compose.onNodeWithText("Detail of Béla").assertExists()
        compose.onNodeWithText("Detail of Anna").assertDoesNotExist()
    }

    @Test
    fun `with nobody to choose the detail pane stays quiet`() {
        showPeople(WidthClass.EXPANDED, content = PeopleContent.Loaded(emptyList()))
        compose.onNodeWithText(placeholder).assertDoesNotExist()

        state = PeopleUiState(content = PeopleContent.Loading)
        compose.onNodeWithText(placeholder).assertDoesNotExist()
    }

    @Test
    fun `back on an expanded window first clears the selection`() {
        showPeople(WidthClass.EXPANDED)
        assertFalse("nothing to clear, so Back leaves People", backIsIntercepted)

        compose.onNodeWithText("Anna").performClick()
        assertTrue(backIsIntercepted)

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        assertNull(state.selectedPersonId)
        compose.onNodeWithText(placeholder).assertExists()
        assertFalse(backIsIntercepted)
    }

    @Test
    fun `narrowing with a selection opens that person once and clears the selection`() {
        showPeople(WidthClass.EXPANDED, selected = anna)

        widthClass = WidthClass.MEDIUM
        compose.waitForIdle()
        assertEquals(listOf(anna.id), opened)
        assertNull(state.selectedPersonId)

        widthClass = WidthClass.EXPANDED
        compose.waitForIdle()
        widthClass = WidthClass.COMPACT
        compose.waitForIdle()
        assertEquals("opened only once", listOf(anna.id), opened)
    }

    @Test
    fun `narrowing without a selection opens nothing`() {
        showPeople(WidthClass.EXPANDED)

        widthClass = WidthClass.MEDIUM
        compose.waitForIdle()

        assertTrue(opened.isEmpty())
    }

    @Test
    fun `the detail beside the list has no back arrow, the detail screen keeps it`() {
        var onBack: (() -> Unit)? by mutableStateOf(null)
        compose.setContent {
            GiftiaryTheme {
                PersonDetailScreen(
                    state = PersonDetailUiState.Loaded(anna, emptyList(), emptyList(), emptyList(), emptyList()),
                    onBack = onBack,
                    onEditPerson = {},
                    onAddOccasion = {},
                    onEditOccasion = {},
                    onPlanGift = {},
                    onAddGift = {},
                    onOpenGift = {},
                    onAddGiftIdea = {},
                    onOpenGiftIdea = {},
                    onAddWishlist = {},
                    onOpenWishlist = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteDismissed = {},
                    onDeleteErrorShown = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()

        onBack = {}
        compose.onNodeWithContentDescription("Back").assertExists()
    }
}
