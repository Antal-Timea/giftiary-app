package app.giftiary.presentation.sync

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.AttentionItem
import app.giftiary.domain.model.AttentionKind
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.SyncItem
import app.giftiary.domain.model.SyncItemType
import app.giftiary.domain.model.SyncStatus
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/** Needs attention: the task header, one row per item, and only the actions each item allows. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h1600dp")
class SyncAttentionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    // Open and dismiss; open only; dismiss only; neither.
    private val replaced = attention(AttentionKind.REPLACED_BY_OTHER_DEVICE, SyncItem(SyncItemType.PERSON, UUID.randomUUID(), "Anna"), canOpen = true)
    private val refused = attention(AttentionKind.REFUSED, SyncItem(SyncItemType.OCCASION, UUID.randomUUID(), null, OccasionType.BIRTHDAY), canOpen = true)
    private val duplicate = attention(AttentionKind.DUPLICATE_GIFT_REMOVED, SyncItem(SyncItemType.GIFT, UUID.randomUUID(), "Scarf"), canOpen = false)
    private val unavailable = attention(AttentionKind.UNAVAILABLE, SyncItem(SyncItemType.WISH, UUID.randomUUID(), null), canOpen = false)

    private fun attention(kind: AttentionKind, item: SyncItem, canOpen: Boolean) = AttentionItem(UUID.randomUUID(), kind, item, canOpen)

    private fun show(content: SyncStatusContent, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiftiaryTheme {
                    SyncAttentionScreen(
                        content = content,
                        onBack = { events += "back" },
                        onOpen = { events += "open ${it.name ?: it.type}" },
                        onDismiss = { events += "dismiss ${it.item.name ?: it.item.type}" },
                    )
                }
            }
        }
    }

    private fun loaded(vararg items: AttentionItem) = SyncStatusContent.Loaded(SyncStatus(waitingCount = 3, attention = items.toList()))

    private fun action(clickLabel: String) = compose.onNode(
        SemanticsMatcher("click label $clickLabel") { it.config.getOrNull(SemanticsActions.OnClick)?.label == clickLabel },
    )

    @Test
    fun `while loading there is only the way back`() {
        show(SyncStatusContent.Loading)

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    fun `an unreadable status says so, with only the way back`() {
        show(SyncStatusContent.Unavailable)

        compose.onNodeWithText("Sync status cannot be shown right now.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun `nothing to attend to says how saving works, not that everything is synced`() {
        show(loaded())

        compose.onNode(hasText("Needs attention") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNode(hasText("Nothing needs your attention") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithText("Your changes are saved on this device first", substring = true).assertExists()
        compose.onAllNodesWithText("synced", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun `each row names its type, the item and what happened`() {
        show(loaded(replaced, refused, duplicate, unavailable))

        listOf("Person", "Anna", "Occasion", "Birthday", "Gift", "Scarf", "Wish", "Unnamed item").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("This was changed on another device too.", substring = true).assertExists()
        compose.onNodeWithText("Your account did not accept this change. Check its details.").assertExists()
        compose.onNodeWithText("This idea was given on another device too.", substring = true).assertExists()
        compose.onNodeWithText("This change cannot reach your account.").assertExists()
    }

    @Test
    fun `Open and Got it appear only where allowed, each naming its item and acting once`() {
        show(loaded(replaced, refused, duplicate, unavailable))

        // Back, Anna's two, the birthday's Open, the scarf's Got it; nothing for the wish.
        compose.onAllNodes(hasClickAction()).assertCountEquals(5)
        compose.onAllNodesWithText("Open").assertCountEquals(2)
        compose.onAllNodesWithText("Got it").assertCountEquals(2)
        action("Open Unnamed item").assertDoesNotExist()
        action("Close notice: Unnamed item").assertDoesNotExist()
        action("Close notice: Birthday").assertDoesNotExist()
        action("Open Scarf").assertDoesNotExist()

        action("Open Anna").performClick()
        action("Close notice: Anna").performClick()
        action("Open Birthday").performClick()
        action("Close notice: Scarf").performClick()
        assertEquals(listOf("open Anna", "dismiss Anna", "open OCCASION", "dismiss Scarf"), events)
    }

    @Test
    fun `Home's attention line is a 48dp button that opens the list once`() {
        compose.setContent {
            GiftiaryTheme { SyncStatusLine(loaded(replaced, duplicate), onOpenAttention = { events += "attention" }) }
        }

        val line = compose.onNode(hasText("2 items need your attention") and hasClickAction())
        line.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        line.assert(SemanticsMatcher("open label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show what needs attention" })
        val bounds = line.getBoundsInRoot()
        assertTrue("a 48dp target", bounds.bottom - bounds.top >= 48.dp)
        compose.onNodeWithText("3 changes saved on this device, waiting to sync").assertExists()
        line.performClick()
        assertEquals(listOf("attention"), events)
    }

    @Test
    fun `back leaves once`() {
        show(loaded(replaced))

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h1600dp")
    fun `in Hungarian at large text a row and its actions stay inside the screen`() {
        show(loaded(replaced), fontScale = 2f)

        val width = compose.onRoot().getBoundsInRoot().right
        listOf(
            compose.onNodeWithText("Figyelmet igényel"),
            compose.onNodeWithText("Ezt egy másik eszközön is módosították.", substring = true),
            action("Megnyitás: Anna"),
            action("Jelzés lezárása: Anna"),
        ).forEach { node ->
            val bounds = node.getBoundsInRoot()
            assertTrue("$bounds outside the screen", bounds.left >= 0.dp && bounds.right <= width)
        }
        val open = action("Megnyitás: Anna").getBoundsInRoot()
        assertTrue(open.bottom - open.top >= 48.dp)
        action("Jelzés lezárása: Anna").performClick()
        assertEquals(listOf("dismiss Anna"), events)
    }
}
