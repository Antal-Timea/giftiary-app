package app.giftiary.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What covers the app once the server deleted the account: finishing, not finished yet, deleted. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class AccountDeletionScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiftiaryTheme { content() }
            }
        }
    }

    private fun heading(text: String) = compose.onNode(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

    @Test
    fun `while finishing it says so and offers nothing to press`() {
        show { FinishingAccountDeletionScreen(failure = null, onRetry = { events += "retry" }) }

        heading("Deleting your account…").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `when finishing failed, Try again is the one action and asks once`() {
        show { FinishingAccountDeletionScreen(failure = AccountDeletionFailure.NETWORK, onRetry = { events += "retry" }) }

        heading("Your account couldn’t be removed from this device yet.").assertExists()
        compose.onNodeWithText("Couldn’t reach Giftiary. Check your connection and try again.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNode(hasText("Try again") and hasClickAction()).performClick()

        assertEquals(listOf("retry"), events)
    }

    @Test
    fun `deleted says where it was deleted, and Continue is the one action`() {
        show { AccountDeletedScreen(onContinue = { events += "continue" }) }

        heading("Account deleted").assertExists()
        compose.onNodeWithText("Your Giftiary account and its data have been deleted from Giftiary and from this device.").assertExists()
        compose.onAllNodesWithText("all devices", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNode(hasText("Continue") and hasClickAction()).performClick()

        assertEquals(listOf("continue"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `in Hungarian at large text the failure stays inside the screen`() {
        show(fontScale = 2f) { FinishingAccountDeletionScreen(failure = AccountDeletionFailure.SERVER, onRetry = {}) }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf(
            compose.onNodeWithText("A fiókodat még nem sikerült eltávolítani erről az eszközről."),
            compose.onNodeWithText("Valami hiba történt. Próbáld újra."),
            compose.onNode(hasText("Újra") and hasClickAction()),
        ).forEach { node ->
            val bounds = node.getBoundsInRoot()
            assertTrue("$bounds outside the screen", bounds.left >= 0.dp && bounds.right <= width)
        }
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `in Hungarian at large text the deleted state keeps Continue reachable`() {
        show(fontScale = 2f) { AccountDeletedScreen(onContinue = { events += "continue" }) }

        compose.onNode(hasText("Tovább") and hasClickAction()).performClick()
        assertEquals(listOf("continue"), events)
        val message = compose.onNodeWithText("erről az eszközről", substring = true).getBoundsInRoot()
        assertTrue(message.right <= compose.onRoot().getBoundsInRoot().right)
    }
}
