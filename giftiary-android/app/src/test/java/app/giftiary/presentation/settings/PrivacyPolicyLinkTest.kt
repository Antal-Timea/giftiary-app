package app.giftiary.presentation.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyPolicyLinkTest {

    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun handler(open: (String) -> Unit) = object : UriHandler {
        override fun openUri(uri: String) = open(uri)
    }

    private fun showLink(handler: UriHandler = handler { opened += it }) {
        compose.setContent {
            GiftiaryTheme {
                CompositionLocalProvider(LocalUriHandler provides handler) { PrivacyPolicyLink() }
            }
        }
    }

    @Test
    fun `the published policy opens in the browser, at the English section`() {
        showLink()

        compose.onNodeWithText("Privacy Policy").assertHasClickAction().performClick()

        assertEquals(listOf("https://giftiary.app/privacy/#en"), opened)
    }

    @Test
    @Config(qualifiers = "hu")
    fun `in Hungarian it opens the Hungarian section`() {
        showLink()

        compose.onNodeWithText("Adatkezelési tájékoztató").performClick()

        assertEquals(listOf("https://giftiary.app/privacy/#hu"), opened)
    }

    @Test
    fun `a device without a browser does not crash the app`() {
        showLink(handler { throw IllegalStateException("no browser") })

        compose.onNodeWithText("Privacy Policy").performClick()
    }
}
