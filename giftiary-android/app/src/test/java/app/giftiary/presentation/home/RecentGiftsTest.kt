package app.giftiary.presentation.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.SyncStatus
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.sync.SyncStatusContent
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

/** Home's recent gifts: which gifts, their photos, and the cards that show them. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class RecentGiftsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val gifts = FakeGiftRepository()
    private val photos = FakeGiftPhotoRepository()
    private val mom = person("Mom")

    @Test
    fun `the two latest gifts are shown, with the photos that can be read`() = runTest {
        photos.stored["p/lamp.jpg"] = byteArrayOf(1)
        val old = giftEntry(giftDetails(person = mom, name = "Old", givenAt = LocalDate.of(2024, 1, 1)), mom)
        val lamp = giftEntry(giftDetails(person = mom, name = "Lamp", givenAt = LocalDate.of(2026, 5, 1)).copy(photoPath = "p/lamp.jpg"), mom)
        val scarf = giftEntry(giftDetails(person = mom, name = "Scarf", givenAt = LocalDate.of(2026, 4, 1)).copy(photoPath = "p/gone.jpg"), mom)
        gifts.givenGifts(old, scarf, lamp)
        val viewModel = RecentGiftsViewModel(gifts, photos)
        keepSubscribed(viewModel.content)

        val content = viewModel.content.value as RecentGiftsContent.Loaded
        assertEquals(listOf("Lamp", "Scarf"), content.gifts.map { it.gift.name })
        // The scarf's photo cannot be read, so its card keeps the branded fallback.
        assertEquals(setOf("p/lamp.jpg"), content.photos.keys)
    }

    @Test
    fun `the cards name the gift and the other person, open the gift, and See all opens Gifts`() {
        val lamp = giftEntry(giftDetails(person = mom, name = "Lamp"), mom)
        val book = giftEntry(giftDetails(GiftDirection.RECEIVED, mom, "Book"), mom)
        val opened = mutableListOf<UUID>()
        var openedAll = false
        showHome(RecentGiftsContent.Loaded(listOf(lamp, book)), onOpenGift = { opened += it }, onOpenAll = { openedAll = true })

        compose.onNodeWithText("Given to Mom", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Received from Mom", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Book").performClick()
        compose.onNodeWithText("See all").performClick()

        assertEquals(listOf(book.gift.id), opened)
        assertTrue(openedAll)
    }

    @Test
    fun `with no gifts yet the section says where they will appear`() {
        showHome(RecentGiftsContent.Loaded(emptyList()))

        compose.onNodeWithText("Recent gifts").assertExists()
        compose.onNodeWithText("Gifts you give and receive will be remembered here.").assertExists()
        compose.onNodeWithText("See all").assertDoesNotExist()
    }

    private fun showHome(content: RecentGiftsContent, onOpenGift: (UUID) -> Unit = {}, onOpenAll: () -> Unit = {}) {
        compose.setContent {
            GiftiaryTheme {
                HomeScreen(
                    syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 0, attention = emptyList())),
                    comingUp = ComingUpContent.Loaded(emptyList()),
                    onOpenComingUp = {},
                    reminderCard = ReminderCardState.Hidden,
                    reminderActions = ReminderCardActions({}, {}, {}, {}),
                    recentGifts = content,
                    onOpenGift = onOpenGift,
                    onOpenAllGifts = onOpenAll,
                    onOpenOwnOccasions = {},
                    onOpenSyncAttention = {},
                    onOpenSettings = {},
                    widthClass = WidthClass.COMPACT,
                )
            }
        }
    }
}
