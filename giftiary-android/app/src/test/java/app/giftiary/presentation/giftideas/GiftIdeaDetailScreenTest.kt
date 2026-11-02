package app.giftiary.presentation.giftideas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.domain.model.GiftIdea
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** Gift idea detail by status: who and how far along, the facts, then the one next step. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2000dp")
class GiftIdeaDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter")
    private val birthday = Occasion(UUID.randomUUID(), peter.id, OccasionType.BIRTHDAY, null, 12, 10, null, true, Instant.EPOCH, Instant.EPOCH)

    private fun idea(
        status: GiftIdeaStatus = GiftIdeaStatus.IDEA,
        estimate: Money? = Money(45000, "HUF"),
        paid: Money? = null,
        url: String? = "https://example.org/headphones",
        note: String? = "Something comfortable for travelling.",
    ) = GiftIdeaEntry(
        idea = GiftIdea(UUID.randomUUID(), peter.id, birthday.id, "Wireless headphones", status, estimate, paid, null, url, note, Instant.EPOCH, Instant.EPOCH),
        recipient = peter,
        occasion = birthday,
    )

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(GiftIdeaDetailUiState())
    private var shown = false

    private fun show(state: GiftIdeaDetailUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                GiftIdeaDetailScreen(
                    state = this.state,
                    onBack = { events += "back" },
                    onEdit = { events += "edit" },
                    onMarkAsGiven = { events += "given" },
                    onOpenPerson = { events += "person ${it.name}" },
                    onStatusChange = { events += "status $it" },
                    onDeleteRequested = { events += "delete" },
                    onDeleteConfirmed = { events += "confirm" },
                    onDeleteDismissed = { events += "dismiss" },
                    onWriteErrorShown = { events += "error shown" },
                )
            }
        }
    }

    private fun show(entry: GiftIdeaEntry, waitingToSync: Boolean = false) =
        show(GiftIdeaDetailUiState(content = GiftIdeaDetailContent.Loaded(entry), waitingToSync = waitingToSync))

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    private fun assertTopToBottom(texts: List<String>) {
        val tops = texts.map(::top)
        assertEquals("top to bottom: $texts", tops.sorted(), tops)
    }

    private fun openOptions() = compose.onNodeWithContentDescription("Options for Wireless headphones").performClick()

    @Test
    fun `an idea reads person, status, facts and note, then Plan with Mark as purchased below`() {
        show(idea())

        assertTopToBottom(
            listOf(
                "GIFT IDEA", "Wireless headphones", "For Peter", "Birthday · October 12", "Estimated price", "Product link",
                "Something comfortable for travelling.", "Plan this gift", "Mark as purchased",
            ),
        )
        compose.onNodeWithText("Idea", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("45,000", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Price paid").assertDoesNotExist()

        compose.onNodeWithText("Plan this gift").performClick()
        compose.onNodeWithText("Mark as purchased").performClick()
        assertEquals(listOf("status PLANNED", "status PURCHASED"), events)
    }

    @Test
    fun `an idea can still be given straight away, from its options`() {
        show(idea())

        compose.onNodeWithText("Mark as given").assertDoesNotExist()
        openOptions()
        compose.onNodeWithText("Mark as given").performClick()
        assertEquals(listOf("given"), events)
    }

    @Test
    fun `a planned idea is bought next, and can be given from below`() {
        show(idea(status = GiftIdeaStatus.PLANNED))

        compose.onNodeWithText("Planned", useUnmergedTree = true).assertExists()
        assertTopToBottom(listOf("Mark as purchased", "Mark as given"))
        compose.onNodeWithText("Mark as purchased").performClick()
        compose.onNodeWithText("Mark as given").performClick()
        assertEquals(listOf("status PURCHASED", "given"), events)
    }

    @Test
    fun `a purchased idea shows what was paid over the estimate, and is given next`() {
        show(idea(status = GiftIdeaStatus.PURCHASED, paid = Money(42990, "HUF")))

        compose.onNodeWithText("Purchased", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Price paid").assertExists()
        compose.onNodeWithText("42,990", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Estimated ~", substring = true, useUnmergedTree = true).assertExists()
        assertTopToBottom(listOf("Mark as given", "Once given, this is saved to your gift history."))
        compose.onNodeWithText("Plan this gift").assertDoesNotExist()

        compose.onNodeWithText("Mark as given").performClick()
        assertEquals(listOf("given"), events)
    }

    @Test
    fun `facts the idea does not have are left out, and there is no photo to pretend`() {
        show(idea(estimate = null, url = null, note = null))

        listOf("Estimated price", "Price paid", "Product link", "View link").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        assertEquals(0, compose.onAllNodes(hasContentDescription("photo", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun `the person leads to their page, and the link is offered by name`() {
        show(idea())

        compose.onNodeWithText("View link", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("https://example.org/headphones", substring = true).assertDoesNotExist()
        compose.onNodeWithText("For Peter").performClick()
        assertEquals(listOf("person Peter"), events)
    }

    @Test
    fun `waiting to sync is said only while a change waits`() {
        show(idea())
        compose.onNodeWithText("Waiting to sync", substring = true, useUnmergedTree = true).assertDoesNotExist()

        show(idea(), waitingToSync = true)
        compose.onNodeWithText("Waiting to sync", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `edit and delete wait in the options, and delete still asks`() {
        val entry = idea()
        show(entry)

        openOptions()
        compose.onNodeWithText("Edit").performClick()
        openOptions()
        compose.onNodeWithText("Delete gift idea").performClick()
        show(GiftIdeaDetailUiState(GiftIdeaDetailContent.Loaded(entry), GiftIdeaDetailActions(confirmingDelete = true)))
        compose.onNodeWithText("Delete Wireless headphones?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf("edit", "delete", "dismiss", "confirm"), events)
    }

    @Test
    fun `while a change is stored, the steps wait`() {
        show(GiftIdeaDetailUiState(GiftIdeaDetailContent.Loaded(idea()), GiftIdeaDetailActions(isBusy = true)))

        compose.onNodeWithText("Plan this gift").performClick()
        compose.onNodeWithText("Mark as purchased").performClick()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `an idea that is gone keeps the way back`() {
        show(GiftIdeaDetailUiState(content = GiftIdeaDetailContent.NotFound))

        compose.onNodeWithText("This gift idea is no longer available.").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h2000dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme {
                    GiftIdeaDetailScreen(
                        GiftIdeaDetailUiState(GiftIdeaDetailContent.Loaded(idea(status = GiftIdeaStatus.PURCHASED, paid = Money(42990, "HUF")))),
                        {}, {}, {}, {}, {}, {}, {}, {}, {},
                    )
                }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("AJÁNDÉKÖTLET", "Fizetett ár", "Odaadás után bekerül az ajándékelőzményeid közé.").forEach {
            val bounds = compose.onAllNodesWithText(it, useUnmergedTree = true)[0].getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
