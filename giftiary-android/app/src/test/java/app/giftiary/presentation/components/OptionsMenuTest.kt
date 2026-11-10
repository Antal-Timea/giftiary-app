package app.giftiary.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The divider above a destructive option only appears when there is something above it. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OptionsMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private fun open(items: @Composable OptionsMenuScope.() -> Unit) {
        compose.setContent { GiftiaryTheme { OptionsMenu(R.drawable.ic_more, "Options", items = items) } }
        compose.onNodeWithContentDescription("Options").performClick()
    }

    @Test
    fun `a destructive option below others is set apart by a divider`() {
        open {
            Option("Edit", R.drawable.ic_edit, {})
            Option("Delete", R.drawable.ic_delete, {}, quiet = true)
        }

        compose.onNodeWithText("Delete").assertExists()
        compose.onNodeWithTag(OPTIONS_DIVIDER_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a destructive option alone has no divider above it`() {
        open { Option("Leave this wishlist", R.drawable.ic_close, {}, quiet = true, alone = true) }

        compose.onNodeWithText("Leave this wishlist").assertExists()
        compose.onNodeWithTag(OPTIONS_DIVIDER_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}
