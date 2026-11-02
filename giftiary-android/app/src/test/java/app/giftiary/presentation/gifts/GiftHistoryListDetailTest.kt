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
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
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
 * window narrows. The state is held here the way the view model holds it.
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
    private val gifts = GiftHistoryContent.Loaded(groupByYearAndMonth(listOf(book, headphones)))
    private val selectGift = "Select a gift to see its details."

    private var state by mutableStateOf(GiftHistoryUiState())
    private var widthClass by mutableStateOf(WidthClass.EXPANDED)
    private val openedGifts = mutableListOf<UUID>()

    private fun showHistory(width: WidthClass, content: GiftHistoryContent = gifts, selected: UUID? = null) {
        widthClass = width
        state = GiftHistoryUiState(content = content, selectedGiftId = selected)
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(widthOf(widthClass)).fillMaxHeight()) {
                    AdaptiveGiftHistoryScreen(
                        state = state,
                        onFilterChange = {},
                        onQueryChange = {},
                        onAddGift = {},
                        onOpenGift = { openedGifts += it },
                        onSelectGift = { state = state.copy(selectedGiftId = it.gift.id) },
                        onClearSelection = { state = state.copy(selectedGiftId = null) },
                        giftDetail = { id, onDeleted ->
                            Column {
                                Text("Gift detail " + listOf(book, headphones).first { it.gift.id == id }.gift.name)
                                TextButton(onClick = onDeleted) { Text("Delete gift") }
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
    fun `a phone opens a gift as a screen of its own, with no selection`() {
        showHistory(WidthClass.COMPACT)
        compose.onNodeWithText("Book").performClick()
        assertEquals(listOf(book.gift.id), openedGifts)
        compose.onNodeWithText("Book").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        assertNull(state.selectedGiftId)
        compose.onNodeWithText(selectGift).assertDoesNotExist()
    }

    @Test
    fun `a medium window opens the gift too, from a list at the readable width`() {
        showHistory(WidthClass.MEDIUM)

        // 640 dp centred in 800 dp starts at 80 dp; then the 22 dp gutter, 52 dp thumbnail and 14 dp gap.
        assertTrue("list capped and centred (was ${left("Book")})", left("Book") in 167.dp..169.dp)
        compose.onNodeWithText(selectGift).assertDoesNotExist()
        compose.onNodeWithText("Book").performClick()
        assertEquals(listOf(book.gift.id), openedGifts)
        assertNull(state.selectedGiftId)
    }

    @Test
    fun `an expanded window selects a gift and shows it beside the list`() {
        showHistory(WidthClass.EXPANDED)
        assertTrue("list pane at the start", left("Book") < 360.dp)
        assertTrue("placeholder beside it", left(selectGift) > 360.dp)

        compose.onNodeWithText("Book").performClick()

        assertTrue("nothing navigated", openedGifts.isEmpty())
        assertEquals(book.gift.id, state.selectedGiftId)
        assertTrue(left("Gift detail Book") > 360.dp)
        compose.onNodeWithText(selectGift).assertDoesNotExist()
        compose.onNodeWithText("Book").assertIsSelected()
        compose.onNodeWithText("Headphones").assertIsNotSelected()

        compose.onNodeWithText("Headphones").performClick()
        compose.onNodeWithText("Gift detail Headphones").assertExists()
        compose.onNodeWithText("Gift detail Book").assertDoesNotExist()
    }

    @Test
    fun `deleting the gift beside the list closes it there`() {
        showHistory(WidthClass.EXPANDED, selected = book.gift.id)
        compose.onNodeWithText("Delete gift").performClick()
        assertNull(state.selectedGiftId)
        compose.onNodeWithText(selectGift).assertExists()
        assertTrue("nothing navigated", openedGifts.isEmpty())
    }

    @Test
    fun `with nothing to choose the detail pane stays quiet`() {
        showHistory(WidthClass.EXPANDED, content = GiftHistoryContent.NoGifts)
        listOf(
            GiftHistoryContent.NoGifts,
            GiftHistoryContent.NoMatches(total = 2),
            GiftHistoryContent.Loading,
            GiftHistoryContent.LoadFailed,
        ).forEach { content ->
            state = state.copy(content = content)
            compose.onNodeWithText(selectGift).assertDoesNotExist()
        }
    }

    @Test
    fun `back on an expanded window first clears the selection`() {
        showHistory(WidthClass.EXPANDED)
        assertFalse("nothing to clear, so Back leaves the history", backIsIntercepted)

        compose.onNodeWithText("Book").performClick()
        assertTrue(backIsIntercepted)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        assertNull(state.selectedGiftId)
        compose.onNodeWithText(selectGift).assertExists()
        assertFalse(backIsIntercepted)
    }

    @Test
    fun `narrowing opens the selected gift once, as its own screen`() {
        showHistory(WidthClass.EXPANDED, selected = book.gift.id)

        widthClass = WidthClass.MEDIUM
        compose.waitForIdle()
        assertEquals(listOf(book.gift.id), openedGifts)
        assertNull(state.selectedGiftId)

        widthClass = WidthClass.EXPANDED
        compose.waitForIdle()
        widthClass = WidthClass.COMPACT
        compose.waitForIdle()
        assertEquals("opened only once", listOf(book.gift.id), openedGifts)
    }

    @Test
    fun `narrowing without a selection opens nothing`() {
        showHistory(WidthClass.EXPANDED)

        widthClass = WidthClass.MEDIUM
        compose.waitForIdle()

        assertTrue(openedGifts.isEmpty())
    }

    @Test
    fun `the detail beside the list has no back arrow, its own screen keeps it`() {
        var onBack: (() -> Unit)? by mutableStateOf(null)
        compose.setContent {
            GiftiaryTheme {
                GiftDetailScreen(
                    state = GiftDetailUiState(content = GiftDetailContent.Loaded(book)),
                    onBack = onBack,
                    onEditGift = {},
                    onOpenPerson = {},
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

    @Test
    fun `deleting a gift reports it through onDeleted, not Back`() {
        val giftRepository = FakeGiftRepository().apply { givenGifts(book) }
        val giftViewModel = GiftDetailViewModel(book.gift.id, giftRepository, FakeGiftPhotoRepository())
        var backs = 0
        val deleted = mutableListOf<String>()
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(600.dp)) {
                    GiftDetailScreen(
                        viewModel = giftViewModel,
                        onBack = { backs++ },
                        onDeleted = { deleted += "gift" },
                        onEditGift = {},
                        onOpenPerson = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.runOnIdle {
            giftViewModel.onDeleteRequested()
            giftViewModel.onDeleteConfirmed()
        }
        compose.waitForIdle()

        assertEquals(listOf("gift"), deleted)
        assertEquals(0, backs)
    }
}
