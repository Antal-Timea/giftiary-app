package app.giftiary.testing

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag

/** Photos are decoded off the main thread: waits until the image tagged [tag] is drawn. */
fun ComposeTestRule.awaitPhoto(tag: String) = waitUntil(timeoutMillis = 5_000) {
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
