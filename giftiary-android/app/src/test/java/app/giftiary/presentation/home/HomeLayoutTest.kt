package app.giftiary.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.SyncStatus
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.sync.SyncStatusContent
import app.giftiary.presentation.theme.GiftiaryLayout
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.WithFontScale
import app.giftiary.testing.assertNear
import app.giftiary.testing.assertNoClippedText
import app.giftiary.testing.centredStart
import app.giftiary.testing.lineCount
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Where Home puts its parts at each width class, measured on the laid-out screen. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class HomeLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val title = "Who are we making happy next?"
    private val comingUp = "Coming up"
    private val ownOccasions = "Your occasions"
    private val reminder = "Get a reminder on the day of an occasion and a week before."
    private val sync = "No changes waiting to sync"
    private val recentGifts = "Recent gifts"

    private fun showHome(
        width: Dp,
        widthClass: WidthClass,
        reminderCard: ReminderCardState = ReminderCardState.Off,
        comingUp: ComingUpContent = ComingUpContent.Loaded(emptyList()),
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            WithFontScale(fontScale) {
                GiftiaryTheme {
                    Box(Modifier.width(width).fillMaxHeight()) {
                        HomeScreen(
                            syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 0, attention = emptyList())),
                            comingUp = comingUp,
                            onOpenComingUp = {},
                            reminderCard = reminderCard,
                            reminderActions = ReminderCardActions({}, {}, {}, {}),
                            recentGifts = RecentGiftsContent.Loaded(emptyList()),
                            onOpenGift = {},
                            onOpenAllGifts = {},
                            onOpenOwnOccasions = {},
                            onOpenSyncAttention = {},
                            accountEmail = "anna@example.com",
                            onOpenProfile = {},
                            widthClass = widthClass,
                        )
                    }
                }
            }
        }
    }

    private fun bounds(text: String): DpRect = compose.onNodeWithText(text).getBoundsInRoot()

    @Test
    fun `a phone shows the hero, sync status, coming up, the reminder and recent gifts, top to bottom`() {
        showHome(width = 411.dp, widthClass = WidthClass.COMPACT)

        val order = listOf(title, sync, comingUp, ownOccasions, reminder, recentGifts)
        val tops = order.map { bounds(it).top }
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        // The hero and the band run edge to edge; their text keeps the browse gutter.
        assertNear(GiftiarySpacing.browseGutter, bounds(comingUp).left, "coming up start")
        assertNear(GiftiarySpacing.browseGutter, bounds(recentGifts).left, "recent gifts start")
        // The profile avatar where the board has it: top right, inside the hero, a 48dp target.
        val profile = compose.onNodeWithContentDescription("Your profile").getBoundsInRoot()
        assertTrue("profile in the hero", profile.bottom < bounds(title).bottom && profile.right > 300.dp)
        assertNear(48.dp, profile.right - profile.left, "profile target width")
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    // Real text measurement, so a squeezed or clipped word shows.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian at large text the hero grows instead of clipping, and a plan keeps what is bought`() {
        val anna = person("Anna")
        val birthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 3, 11, 1990, true))
        val today = ComingUpItem(UpcomingOccasion(birthday, anna, LocalDate.of(2026, 11, 3), 0, 36), OccasionPlan(ideas = 2, bought = 1))
        showHome(width = 390.dp, widthClass = WidthClass.COMPACT, comingUp = ComingUpContent.Loaded(listOf(today)), fontScale = 2f)

        compose.onRoot(useUnmergedTree = true).assertNoClippedText()
        assertEquals(1, compose.onNodeWithText("1 megvéve", useUnmergedTree = true).lineCount())
        val ideas = compose.onNodeWithText("2 ötlet", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("bought under the ideas", compose.onNodeWithText("1 megvéve", useUnmergedTree = true).getBoundsInRoot().top >= ideas.bottom)
    }

    @Test
    fun `a medium window shows the same column at a readable, centred width, with the profile entry and no Settings link`() {
        showHome(width = 800.dp, widthClass = WidthClass.MEDIUM)

        val start = centredStart(800.dp, GiftiaryLayout.readableMaxWidth) + GiftiarySpacing.browseGutter
        assertNear(start, bounds(comingUp).left, "coming up start")
        assertNear(start, bounds(recentGifts).left, "recent gifts start")
        val tops = listOf(title, sync, comingUp, ownOccasions, reminder, recentGifts).map { bounds(it).top }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Your profile").assertExists()
    }

    @Test
    fun `an expanded window puts coming up and the reminder, sync status and recent gifts side by side under the header`() {
        showHome(width = 1200.dp, widthClass = WidthClass.EXPANDED)

        val header = bounds(title)
        val left = bounds(comingUp)
        val card = bounds(reminder)
        val status = bounds(sync)
        val recent = bounds(recentGifts)
        val start = centredStart(1200.dp, GiftiaryLayout.wideMaxWidth) + GiftiarySpacing.xl
        assertNear(start, header.left, "header start")
        assertTrue("both columns below the header", left.top > header.bottom && card.top > header.bottom)
        assertTrue("the reminder beside coming up, not below it", card.top < bounds(ownOccasions).bottom)
        // 3:2 columns with a 32 dp gap: the second starts about 3/5 into the 992 dp row.
        assertTrue("reminder in the second column (was ${card.left})", card.left > left.left + 550.dp)
        assertTrue("sync status under the reminder", status.left > left.left + 550.dp && status.top > card.bottom)
        assertTrue("recent gifts under the sync status", recent.left > left.left + 550.dp && recent.top > status.bottom)
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        assertTrue("profile entry in the header", compose.onNodeWithContentDescription("Your profile").getBoundsInRoot().top < header.bottom)
    }

    @Test
    fun `without a reminder card the sync status heads the second column`() {
        showHome(width = 1200.dp, widthClass = WidthClass.EXPANDED, reminderCard = ReminderCardState.Hidden)

        compose.onNodeWithText(reminder).assertDoesNotExist()
        val status = bounds(sync)
        assertTrue("sync status in the second column", status.left > bounds(comingUp).left + 550.dp)
    }
}
