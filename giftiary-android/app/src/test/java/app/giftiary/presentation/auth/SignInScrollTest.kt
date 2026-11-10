package app.giftiary.presentation.auth

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import app.giftiary.data.auth.ProviderSignInState
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.WithFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sign in scrolls only when it has to. 360x752dp is a 360x800dp phone less its status and gesture
 * bars; both providers are on, as in the Gate A build. Native graphics, so text has its real height.
 */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInScrollTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            GiftiaryTheme {
                WithFontScale(fontScale) {
                    SignInContent(CredentialsUiState(), {}, {}, {}, {}, {}, AuthProvider.entries, ProviderSignInState.Idle, {})
                }
            }
        }
    }

    /** How far the page can be scrolled, in px; the range is there even while scrolling is off. */
    private fun scrollRange(): Float {
        var range: Float? = null
        fun find(node: androidx.compose.ui.semantics.SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { range = it.maxValue() }
            node.children.forEach(::find)
        }
        find(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return range!!
    }

    private fun assertStill() {
        assertEquals("no scroll range", 0f, scrollRange())
        assertEquals("nothing to drag", 0, compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size)
    }

    private fun assertScrollsToTheEnd(lastLink: String) {
        assertTrue("there is more to see", scrollRange() > 0f)
        compose.onAllNodesWithText(lastLink).onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h752dp")
    fun `on an ordinary phone at normal text the page fits and does not move`() {
        show()

        assertStill()
    }

    @Test
    @Config(qualifiers = "hu-w360dp-h752dp")
    fun `in Hungarian too`() {
        show()

        assertStill()
    }

    @Test
    @Config(qualifiers = "w360dp-h560dp")
    fun `on a short window it scrolls, to the last link`() {
        show()

        assertScrollsToTheEnd("Create account")
    }

    @Test
    @Config(qualifiers = "hu-w360dp-h752dp")
    fun `at large text it scrolls, to the last link`() {
        show(fontScale = 2f)

        assertScrollsToTheEnd("Fiók létrehozása")
    }
}
