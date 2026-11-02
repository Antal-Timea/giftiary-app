package app.giftiary.presentation.gifts

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** The phone Gift detail: its hero, the approved order, what it leaves out, and where its actions went. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class GiftDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter", relationship = "Husband")
    private val bare = giftEntry(giftDetails(GiftDirection.GIVEN, peter, "Wireless headphones", LocalDate.of(2026, 10, 12)), peter)
    private val full = bare.copy(
        gift = bare.gift.copy(
            price = Money(42990, "HUF"),
            note = "He wanted something comfortable for travelling.",
            productUrl = "https://example.com/headphones",
            photoPath = "headphones.jpg",
        ),
        occasion = occasion(peter.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 10, null, true)),
    )
    private val actions = mutableListOf<String>()
    private var state by mutableStateOf(GiftDetailUiState())

    private var shown = false

    private fun show(state: GiftDetailUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                GiftDetailScreen(
                    state = this.state,
                    onBack = { actions += "back" },
                    onEditGift = { actions += "edit ${it.gift.name}" },
                    onOpenPerson = { actions += "person ${it.name}" },
                    onDeleteRequested = { actions += "delete requested" },
                    onDeleteConfirmed = { actions += "delete confirmed" },
                    onDeleteDismissed = { actions += "delete dismissed" },
                    onDeleteErrorShown = { actions += "error shown" },
                )
            }
        }
    }

    private fun loaded(entry: GiftEntry, photo: GiftDetailPhoto? = null, waitingToSync: Boolean = false) =
        GiftDetailUiState(content = GiftDetailContent.Loaded(entry), photo = photo, waitingToSync = waitingToSync)

    private fun top(text: String) = compose.onNodeWithText(text, substring = true).getBoundsInRoot().top

    private fun jpeg(): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF5A4032.toInt()) }
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }

    @Test
    fun `the page reads hero, person, facts, then the note`() {
        show(loaded(full, photo = GiftDetailPhoto(jpeg(), loading = false)))

        val order = listOf("Given to Peter", "Wireless headphones", "Husband", "Birthday · October 12, 2026", "42,990", "example.com", "comfortable")
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("Wireless headphones").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("Photo of the gift").assertExists()
    }

    @Test
    fun `without a photo the branded fallback keeps the hero, and nothing claims a photo`() {
        show(loaded(full.copy(gift = full.gift.copy(photoPath = null))))
        compose.onNodeWithContentDescription("Photo of the gift").assertDoesNotExist()
        compose.onNodeWithText("Wireless headphones").assertExists()

        // A photo still being read, or not readable now, holds its place with the same fallback.
        show(loaded(full, photo = GiftDetailPhoto(photo = null, loading = true)))
        compose.onNodeWithContentDescription("Photo of the gift").assertDoesNotExist()
        compose.onNodeWithText("Given to Peter").assertExists()
    }

    @Test
    fun `facts the gift does not have are left out, the date always stays`() {
        show(loaded(bare))

        compose.onNodeWithText("October 12, 2026").assertExists()
        compose.onNodeWithText("HUF", substring = true).assertDoesNotExist()
        compose.onNodeWithText("example.com", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Birthday", substring = true).assertDoesNotExist()
        compose.onNodeWithText("comfortable", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Waiting to sync", substring = true).assertDoesNotExist()
    }

    @Test
    fun `direction is stated in words, received gifts name the giver`() {
        val received = giftEntry(giftDetails(GiftDirection.RECEIVED, peter, "Perfume"), peter)
        show(loaded(received))

        compose.onNodeWithText("Received from Peter").assertExists()
    }

    @Test
    fun `the person row opens that person, and a gift between two others shows both`() {
        val grandma = person("Grandma")
        val martin = person("Martin")
        show(loaded(bare))
        compose.onNodeWithText("Peter").performClick()

        show(loaded(bare.copy(giver = grandma, recipient = martin)))
        compose.onNodeWithText("Grandma → Martin").assertExists()
        compose.onNodeWithText("Grandma").performClick()
        compose.onNodeWithText("Martin").performClick()

        assertEquals(listOf("person Peter", "person Grandma", "person Martin"), actions)
    }

    @Test
    fun `a change or a photo not yet synced says it is saved on this device`() {
        show(loaded(bare, waitingToSync = true))
        compose.onNodeWithText("Waiting to sync · Saved on this device").assertExists()

        show(loaded(full, photo = GiftDetailPhoto(jpeg(), loading = false, uploadPending = true)))
        compose.onNodeWithText("Waiting to sync · Saved on this device").assertExists()
        // Never presented as a failure: the local save succeeded.
        compose.onNodeWithText("fail", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `edit and delete wait in the hero's options, and deleting still asks first`() {
        show(loaded(bare))
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Delete gift").assertDoesNotExist()

        compose.onNodeWithContentDescription("Options for Wireless headphones").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithContentDescription("Options for Wireless headphones").performClick()
        compose.onNodeWithText("Delete gift").performClick()
        assertEquals(listOf("edit Wireless headphones", "delete requested"), actions)

        show(loaded(bare).copy(actions = GiftDetailActions(confirmingDelete = true)))
        compose.onNodeWithText("Delete Wireless headphones?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals("delete confirmed", actions.last())
    }

    @Test
    fun `a failed delete is reported and the gift stays`() {
        show(loaded(bare).copy(actions = GiftDetailActions(deleteFailed = true)))

        compose.onNodeWithText("Could not delete. Please try again.").assertExists()
        compose.onNodeWithText("Wireless headphones").assertExists()
    }

    @Test
    fun `back is in the hero on its own screen`() {
        show(loaded(bare))
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), actions)
    }

    @Test
    fun `a gift that is gone says so`() {
        show(GiftDetailUiState(content = GiftDetailContent.NotFound))
        compose.onNodeWithText("This gift no longer exists.").assertExists()
    }
}
