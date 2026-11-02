package app.giftiary.presentation.giftideas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The Mark as given sheet: the idea, when and for how much, then one Save. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1400dp")
class MarkGiftIdeaGivenScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val birthday = Occasion(UUID.randomUUID(), UUID.randomUUID(), OccasionType.BIRTHDAY, null, 12, 10, null, true, Instant.EPOCH, Instant.EPOCH)
    private val ready = MarkGivenUiState(
        givenAt = LocalDate.of(2026, 10, 12),
        status = MarkGivenStatus.READY,
        ideaName = "Wireless headphones",
        recipientName = "Peter",
        occasion = birthday,
        price = "42990",
        estimate = Money(45000, "HUF"),
    )

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(ready)
    private var shown = false

    private fun show(state: MarkGivenUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                MarkGiftIdeaGivenScreen(
                    state = this.state,
                    onCancel = { events += "cancel" },
                    onDateChange = { events += "date $it" },
                    onPriceChange = { events += "price $it" },
                    onConfirm = { events += "confirm" },
                )
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    @Test
    fun `reads the idea, when, the final price with its estimate, then save and cancel`() {
        show(ready)

        val order = listOf(
            "Mark as given", "Wireless headphones", "For Peter · Birthday", "When did you give it?", "October 12, 2026",
            "Final price (optional)", "42990", "Save to gift history", "Cancel",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("Estimated ~", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("The gift will be added to your history and the idea removed from your plans.").assertExists()
    }

    @Test
    fun `save and cancel reach the flow`() {
        show(ready)

        compose.onNodeWithText("Save to gift history").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("confirm", "cancel"), events)
    }

    @Test
    fun `while the gift is stored, Save cannot be pressed again`() {
        show(ready.copy(isBusy = true))

        compose.onNodeWithText("Save to gift history", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Wireless headphones").assertExists()
        compose.onNodeWithText("Save to gift history").performClick()
        assertTrue("no second conversion", "confirm" !in events)
    }

    @Test
    fun `once recorded, nothing can be sent again while the gift opens`() {
        show(ready.copy(status = MarkGivenStatus.DONE))

        compose.onNodeWithText("Save to gift history").assertIsNotEnabled()
    }

    @Test
    fun `a failed save says so, keeps the input, and can be tried again`() {
        show(ready.copy(writeFailed = true))

        compose.onNodeWithText("Could not record the gift. Please try again.").assertExists()
        compose.onNodeWithText("42990").assertExists()
        compose.onNodeWithText("Save to gift history").performClick()
        assertEquals(listOf("confirm"), events)
    }

    @Test
    fun `an idea given meanwhile is said plainly, with nothing left to save but a way out`() {
        show(ready.copy(status = MarkGivenStatus.ALREADY_GIVEN))

        compose.onNodeWithText("This idea has already been given.").assertExists()
        compose.onNodeWithText("Save to gift history").assertDoesNotExist()
        compose.onNodeWithText("When did you give it?").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun `a price error replaces the estimate under the field`() {
        show(ready.copy(price = "abc", priceError = PriceError.INVALID))

        compose.onNodeWithText("Enter a valid price", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Estimated ~", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an idea that cannot be given anymore leaves only cancel`() {
        show(MarkGivenUiState(givenAt = ready.givenAt, status = MarkGivenStatus.NOT_AVAILABLE))

        compose.onNodeWithText("This gift idea is no longer available.").assertExists()
        compose.onNodeWithText("Save to gift history").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h1400dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { MarkGiftIdeaGivenScreen(ready, {}, {}, {}, {}) }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Mikor adtad oda?", "Mentés az ajándékelőzményekbe").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
