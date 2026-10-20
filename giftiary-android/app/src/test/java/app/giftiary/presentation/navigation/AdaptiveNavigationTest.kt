package app.giftiary.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.SyncStatus
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.home.ComingUpContent
import app.giftiary.presentation.home.HomeScreen
import app.giftiary.presentation.home.ReminderCardActions
import app.giftiary.presentation.home.ReminderCardState
import app.giftiary.presentation.people.PeopleScreen
import app.giftiary.presentation.people.PeopleUiState
import app.giftiary.presentation.sync.SyncStatusContent
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rail per available width, its selection, and what phones keep. Wide enough a screen to hold every width. */
@RunWith(RobolectricTestRunner::class)
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class AdaptiveNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private var width by mutableStateOf(599.dp)
    private val selections = mutableListOf<AppSection>()

    private fun showFrame(selected: AppSection? = AppSection.HOME) {
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(width).fillMaxHeight()) {
                    ProvideWidthClass {
                        AdaptiveNavigationFrame(selected = selected, onSelect = { selections += it }) {
                            Text("content")
                        }
                    }
                }
            }
        }
    }

    private fun assertRail(shown: Boolean, at: Dp) {
        width = at
        compose.waitForIdle()
        val rail = compose.onNodeWithTag(NAVIGATION_RAIL_TAG)
        if (shown) rail.assertExists("rail at $at") else rail.assertDoesNotExist()
        compose.onNodeWithText("content").assertExists()
    }

    @Test
    fun `the rail appears from 600 dp and follows the window as it is resized`() {
        showFrame()

        assertRail(shown = false, at = 599.dp)
        assertRail(shown = true, at = 600.dp)
        assertRail(shown = true, at = 839.dp)
        assertRail(shown = true, at = 840.dp)
        assertRail(shown = false, at = 599.dp)
    }

    @Test
    fun `the rail marks the current section and opens the one tapped`() {
        width = 1000.dp
        showFrame(selected = AppSection.PEOPLE)

        compose.onNodeWithText("People").assertIsSelected()
        compose.onNodeWithText("Home").assertIsNotSelected()
        compose.onNodeWithText("Settings").assertIsNotSelected()
        compose.onNodeWithText("Wishlists").performClick()
        compose.onNodeWithText("Settings").performClick()

        assertEquals(listOf(AppSection.WISHLISTS, AppSection.SETTINGS), selections)
    }

    @Test
    fun `phones keep Home's section buttons, which the rail replaces on wide windows`() {
        var showButtons by mutableStateOf(true)
        compose.setContent {
            GiftiaryTheme {
                HomeScreen(
                    syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 0, attention = emptyList())),
                    comingUp = ComingUpContent.Loaded(emptyList()),
                    onOpenComingUp = {},
                    reminderCard = ReminderCardState.Hidden,
                    reminderActions = ReminderCardActions({}, {}, {}, {}),
                    onOpenGifts = {},
                    onOpenPeople = {},
                    onOpenWishlists = {},
                    onOpenOwnOccasions = {},
                    onOpenSyncAttention = {},
                    onOpenSettings = {},
                    showSectionButtons = showButtons,
                )
            }
        }
        listOf("Gifts", "People", "Wishlists", "Settings").forEach { compose.onNodeWithText(it).assertExists() }

        showButtons = false
        compose.waitForIdle()

        listOf("Gifts", "People", "Wishlists", "Settings").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun `a section's list has a back arrow on phones and none beside the rail`() {
        var onBack: (() -> Unit)? by mutableStateOf({})
        compose.setContent {
            GiftiaryTheme {
                PeopleScreen(
                    state = PeopleUiState(),
                    onBack = onBack,
                    onAddPerson = {},
                    onOpenPerson = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteDismissed = {},
                    onDeleteErrorShown = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Back").assertExists()

        onBack = null
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}
