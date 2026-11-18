package app.giftiary.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.SyncStatus
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.gifts.ShownPhotos
import app.giftiary.presentation.home.ComingUpContent
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.home.HomeScreen
import app.giftiary.presentation.home.ReminderCardActions
import app.giftiary.presentation.home.RecentGiftsContent
import app.giftiary.presentation.home.ReminderCardState
import app.giftiary.presentation.people.PeopleContent
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
    fun `phones show the bar only where the route asks for it, and wide windows never`() {
        var showBar by mutableStateOf(true)
        var added = 0
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(width).fillMaxHeight()) {
                    ProvideWidthClass {
                        AdaptiveNavigationFrame(
                            selected = AppSection.PEOPLE,
                            onSelect = { selections += it },
                            showBar = showBar,
                            onAdd = { added++ },
                        ) {
                            Text("content")
                        }
                    }
                }
            }
        }
        val add = "Add a gift, idea, wishlist item or person"

        compose.onNodeWithText("Lists").assertExists()
        compose.onNodeWithText("People").assertIsSelected()
        compose.onNodeWithContentDescription(add).performClick()
        assertEquals(1, added)
        assertEquals(emptyList<AppSection>(), selections)

        showBar = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription(add).assertDoesNotExist()
        compose.onNodeWithText("content").assertExists()

        showBar = true
        width = 900.dp
        compose.waitForIdle()
        compose.onNodeWithContentDescription(add).assertDoesNotExist()
        compose.onNodeWithTag(NAVIGATION_RAIL_TAG).assertExists()
    }

    @Test
    fun `the rail marks the current section and opens the one tapped`() {
        width = 1000.dp
        showFrame(selected = AppSection.PEOPLE)

        compose.onNodeWithText("People").assertIsSelected()
        compose.onNodeWithText("Home").assertIsNotSelected()
        compose.onNodeWithText("Settings").assertIsNotSelected()
        compose.onNodeWithText("Lists").performClick()
        compose.onNodeWithText("Settings").performClick()

        assertEquals(listOf(AppSection.WISHLISTS, AppSection.SETTINGS), selections)
    }

    @Test
    fun `Home's section buttons are gone, and its profile entry stays at every width, as the rail has no Profile`() {
        var widthClass by mutableStateOf(WidthClass.COMPACT)
        var profileOpened = 0
        compose.setContent {
            GiftiaryTheme {
                HomeScreen(
                    syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 0, attention = emptyList())),
                    comingUp = ComingUpContent.Loaded(emptyList()),
                    onOpenComingUp = {},
                    reminderCard = ReminderCardState.Hidden,
                    reminderActions = ReminderCardActions({}, {}, {}, {}),
                    recentGifts = RecentGiftsContent.Loaded(emptyList()),
                    onOpenGift = {},
                    onOpenAllGifts = {},
                    onOpenOwnOccasions = {},
                    onOpenSyncAttention = {},
                    accountName = "Anna",
                    onOpenProfile = { profileOpened++ },
                    widthClass = widthClass,
                )
            }
        }
        listOf("Gifts", "People", "Lists").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()

        WidthClass.entries.forEach {
            widthClass = it
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Your profile").performClick()
        }
        assertEquals(WidthClass.entries.size, profileOpened)
    }

    @Test
    fun `People leaves adding to the navigation bar on a phone and keeps its own add button beside the rail`() {
        var showAddButton by mutableStateOf(false)
        compose.setContent {
            GiftiaryTheme {
                PeopleScreen(
                    state = PeopleUiState(content = PeopleContent.Loaded(emptyList())),
                    photos = ShownPhotos.of(emptyMap()),
                    comingUp = ComingUpContent.Loaded(emptyList()),
                    onQueryChange = {},
                    onAddPerson = {},
                    onOpenPerson = {},
                    showAddButton = showAddButton,
                )
            }
        }
        // Only the empty state's own invitation, no floating button.
        compose.onAllNodesWithText("Add person").assertCountEquals(1)

        showAddButton = true
        compose.waitForIdle()

        compose.onAllNodesWithText("Add person").assertCountEquals(2)
    }
}
