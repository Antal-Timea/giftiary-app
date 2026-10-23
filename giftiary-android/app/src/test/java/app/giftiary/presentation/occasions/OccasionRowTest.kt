package app.giftiary.presentation.occasions

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.R
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.occasion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An occasion row announces its click the way its caller describes it: open on one screen, edit on another. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OccasionRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))

    @Test
    fun `the click is announced with the caller's label and runs the caller's action`() {
        var clicks = 0
        compose.setContent {
            GiftiaryTheme {
                OccasionRow(occasion = christmas, onClick = { clicks++ }, clickLabel = R.string.occasion_edit)
            }
        }

        val row = compose.onNodeWithText("Christmas")
        assertEquals("Edit", row.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        row.performClick()
        assertEquals(1, clicks)
    }
}
