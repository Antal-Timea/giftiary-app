package app.giftiary.presentation.gifts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.giftiary.domain.model.GiftDirection
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.giftideas.GiftIdeaDetailContent
import app.giftiary.presentation.giftideas.GiftIdeaDetailScreen
import app.giftiary.presentation.giftideas.GiftIdeaDetailUiState
import app.giftiary.presentation.giftideas.GiftIdeaDetailViewModel
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
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
import java.time.LocalDate
import java.util.UUID

/**
 * The gift history per width class: what a tap does, the panes, Back and the hand-off when a wide
 * window narrows, for gifts and ideas alike. The state is held here the way the view model holds it.
 */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class GiftHistoryListDetailTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val anna = person("Anna")
    private val book = giftEntry(giftDetails(GiftDirection.GIVEN, anna, "Book", LocalDate.of(2026, 3, 12)), anna)
    private val headphones = giftEntry(giftDetails(GiftDirection.RECEIVED, anna, "Headphones", LocalDate.of(2025, 11, 4)), anna)
    private val scarf = ideaEntry(ideaDetails(anna, name = "Scarf"), anna)
    private val gifts = GiftHistoryContent.Loaded(listOf(book, headphones))
    private val ideaList = GiftHistoryContent.IdeasLoaded(listOf(scarf))
    private val selectGift = "Select a gift to see its details."
    private val selectIdea = "Select an idea to see its details."

    private var state by mutableStateOf(GiftHistoryUiState(GiftFilter.ALL, GiftHistoryContent.Loading))
    private var widthClass by mutableStateOf(WidthClass.EXPANDED)
    private val openedGifts = mutableListOf<UUID>()
    private val openedIdeas = mutableListOf<UUID>()

    private fun showHistory(
        width: WidthClass,
        content: GiftHistoryContent = gifts,
        selection: GiftHistorySelection? = null,
    ) {
        widthClass = width
        state = GiftHistoryUiState(
            filter = if (content is GiftHistoryContent.IdeasLoaded) GiftFilter.IDEAS else GiftFilter.ALL,
            content = content,
            selection = selection,
        )
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(widthOf(widthClass)).fillMaxHeight()) {
                    AdaptiveGiftHistoryScreen(
                        state = state,
                        onBack = null,
                        onFilterChange = {},
                        onAddGift = {},
                        onOpenGift = { openedGifts += it },
                        onAddGiftIdea = {},
                        onOpenGiftIdea = { openedIdeas += it },
                        onSelectGift = { state = state.copy(selection = GiftHistorySelection.Gift(it.gift.id)) },
                        onSelectIdea = { state = state.copy(selection = GiftHistorySelection.Idea(it.idea.id)) },
                        onClearSelection = { state = state.copy(selection = null) },
                        giftDetail = { id, onDeleted ->
                            Column {
                                Text("Gift detail " + listOf(book, headphones).first { it.gift.id == id }.gift.name)
                                TextButton(onClick = onDeleted) { Text("Delete gift") }
                            }
                        },
                        ideaDetail = { id, onDeleted ->
                            Column {
                                Text("Idea detail " + scarf.idea.name.takeIf { scarf.idea.id == id })
                                TextButton(onClick = onDeleted) { Text("Delete idea") }
                            }
                        },
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

    private val backIsIntercepted: Boolean
        get() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.hasEnabledCallbacks() }

    @Test
    fun `a phone opens a gift or an idea as a screen of its own, with no selection`() {
        showHistory(WidthClass.COMPACT)
        compose.onNodeWithText("Book").performClick()
        assertEquals(listOf(book.gift.id), openedGifts)
        compose.onNodeWithText("Book").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))

        state = state.copy(filter = GiftFilter.IDEAS, content = ideaList)
        compose.onNodeWithText("Scarf").performClick()
        assertEquals(listOf(scarf.idea.id), openedIdeas)
        assertNull(state.selection)
        compose.onNodeWithText(selectIdea).assertDoesNotExist()
    }

    @Test
    fun `a medium window opens the item too, from a list at the readable width`() {
        showHistory(WidthClass.MEDIUM)

        // 640 dp centred in 800 dp starts at 80 dp, then the row's 16 dp padding.
        assertTrue("list capped and centred (was ${left("Book")})", left("Book") in 95.dp..97.dp)
        compose.onNodeWithText(selectGift).assertDoesNotExist()
        compose.onNodeWithText("Book").performClick()
        assertEquals(listOf(book.gift.id), openedGifts)
        assertNull(state.selection)
    }

    @Test
    fun `an expanded window selects a gift and shows it beside the list`() {
        showHistory(WidthClass.EXPANDED)
        assertTrue("list pane at the start", left("Book") < 360.dp)
        assertTrue("placeholder beside it", left(selectGift) > 360.dp)

        compose.onNodeWithText("Book").performClick()

        assertTrue("nothing navigated", openedGifts.isEmpty())
        assertEquals(GiftHistorySelection.Gift(book.gift.id), state.selection)
        assertTrue(left("Gift detail Book") > 360.dp)
        compose.onNodeWithText(selectGift).assertDoesNotExist()
        compose.onNodeWithText("Book").assertIsSelected()
        compose.onNodeWithText("Headphones").assertIsNotSelected()

        compose.onNodeWithText("Headphones").performClick()
        compose.onNodeWithText("Gift detail Headphones").assertExists()
        compose.onNodeWithText("Gift detail Book").assertDoesNotExist()
    }

    @Test
    fun `an expanded window selects an idea the same way, with its own placeholder`() {
        showHistory(WidthClass.EXPANDED, content = ideaList)
        compose.onNodeWithText(selectIdea).assertExists()
        compose.onNodeWithText(selectGift).assertDoesNotExist()

        compose.onNodeWithText("Scarf").performClick()

        assertTrue(openedIdeas.isEmpty())
        assertEquals(GiftHistorySelection.Idea(scarf.idea.id), state.selection)
        compose.onNodeWithText("Idea detail Scarf").assertExists()
        compose.onNodeWithText("Scarf").assertIsSelected()
    }

    @Test
    fun `deleting the item beside the list closes it there`() {
        showHistory(WidthClass.EXPANDED, selection = GiftHistorySelection.Gift(book.gift.id))
        compose.onNodeWithText("Delete gift").performClick()
        assertNull(state.selection)
        compose.onNodeWithText(selectGift).assertExists()

        state = GiftHistoryUiState(GiftFilter.IDEAS, ideaList, GiftHistorySelection.Idea(scarf.idea.id))
        compose.onNodeWithText("Delete idea").performClick()
        assertNull(state.selection)
        assertTrue("nothing navigated", openedGifts.isEmpty() && openedIdeas.isEmpty())
    }

    @Test
    fun `with nothing to choose the detail pane stays quiet`() {
        showHistory(WidthClass.EXPANDED, content = GiftHistoryContent.NoGifts)
        listOf(
            GiftHistoryContent.NoGifts,
            GiftHistoryContent.NoMatches,
            GiftHistoryContent.NoIdeas,
            GiftHistoryContent.Loading,
            GiftHistoryContent.LoadFailed,
        ).forEach { content ->
            state = state.copy(content = content)
            compose.onNodeWithText(selectGift).assertDoesNotExist()
            compose.onNodeWithText(selectIdea).assertDoesNotExist()
        }
    }

    @Test
    fun `back on an expanded window first clears the selection`() {
        showHistory(WidthClass.EXPANDED)
        assertFalse("nothing to clear, so Back leaves the history", backIsIntercepted)

        compose.onNodeWithText("Book").performClick()
        assertTrue(backIsIntercepted)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        assertNull(state.selection)
        compose.onNodeWithText(selectGift).assertExists()
        assertFalse(backIsIntercepted)
    }

    @Test
    fun `narrowing opens the selected gift once, as its own screen`() {
        showHistory(WidthClass.EXPANDED, selection = GiftHistorySelection.Gift(book.gift.id))

        widthClass = WidthClass.MEDIUM
        compose.waitForIdle()
        assertEquals(listOf(book.gift.id), openedGifts)
        assertTrue(openedIdeas.isEmpty())
        assertNull(state.selection)

        widthClass = WidthClass.EXPANDED
        compose.waitForIdle()
        widthClass = WidthClass.COMPACT
        compose.waitForIdle()
        assertEquals("opened only once", listOf(book.gift.id), openedGifts)
    }

    @Test
    fun `narrowing opens the selected idea once, as its own screen`() {
        showHistory(WidthClass.EXPANDED, content = ideaList, selection = GiftHistorySelection.Idea(scarf.idea.id))

        widthClass = WidthClass.COMPACT
        compose.waitForIdle()

        assertEquals(listOf(scarf.idea.id), openedIdeas)
        assertTrue(openedGifts.isEmpty())
        assertNull(state.selection)
    }

    @Test
    fun `narrowing without a selection opens nothing`() {
        showHistory(WidthClass.EXPANDED)

        widthClass = WidthClass.MEDIUM
        compose.waitForIdle()

        assertTrue(openedGifts.isEmpty() && openedIdeas.isEmpty())
    }

    @Test
    fun `the details beside the list have no back arrow, their own screens keep it`() {
        var onBack: (() -> Unit)? by mutableStateOf(null)
        var showIdea by mutableStateOf(false)
        compose.setContent {
            GiftiaryTheme {
                if (showIdea) {
                    GiftIdeaDetailScreen(
                        state = GiftIdeaDetailUiState(content = GiftIdeaDetailContent.Loaded(scarf)),
                        onBack = onBack,
                        onEdit = {},
                        onMarkAsGiven = {},
                        onStatusChange = {},
                        onDeleteRequested = {},
                        onDeleteConfirmed = {},
                        onDeleteDismissed = {},
                        onWriteErrorShown = {},
                    )
                } else {
                    GiftDetailScreen(
                        state = GiftDetailUiState(content = GiftDetailContent.Loaded(book)),
                        onBack = onBack,
                        onEditGift = {},
                        onDeleteRequested = {},
                        onDeleteConfirmed = {},
                        onDeleteDismissed = {},
                        onDeleteErrorShown = {},
                    )
                }
            }
        }
        for (idea in listOf(false, true)) {
            showIdea = idea
            onBack = null
            compose.onNodeWithContentDescription("Back").assertDoesNotExist()
            onBack = {}
            compose.onNodeWithContentDescription("Back").assertExists()
        }
    }

    @Test
    fun `deleting a gift or an idea reports it through onDeleted, not Back`() {
        val giftRepository = FakeGiftRepository().apply { givenGifts(book) }
        val ideaRepository = FakeGiftIdeaRepository().apply { givenIdeas(scarf) }
        val giftViewModel = GiftDetailViewModel(book.gift.id, giftRepository, FakeGiftPhotoRepository())
        val ideaViewModel = GiftIdeaDetailViewModel(scarf.idea.id, ideaRepository)
        var backs = 0
        val deleted = mutableListOf<String>()
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(600.dp)) {
                    GiftDetailScreen(viewModel = giftViewModel, onBack = { backs++ }, onDeleted = { deleted += "gift" }, onEditGift = {})
                }
                Box(Modifier.width(600.dp)) {
                    GiftIdeaDetailScreen(
                        viewModel = ideaViewModel,
                        onBack = { backs++ },
                        onDeleted = { deleted += "idea" },
                        onEditGiftIdea = {},
                        onMarkAsGiven = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.runOnIdle {
            giftViewModel.onDeleteRequested()
            giftViewModel.onDeleteConfirmed()
            ideaViewModel.onDeleteRequested()
            ideaViewModel.onDeleteConfirmed()
        }
        compose.waitForIdle()

        assertEquals(listOf("gift", "idea"), deleted.sorted())
        assertEquals(0, backs)
    }
}
