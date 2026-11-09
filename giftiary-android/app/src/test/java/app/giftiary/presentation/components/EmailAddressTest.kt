package app.giftiary.presentation.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** An address too wide for its line breaks before the "@" and keeps its domain whole. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmailAddressTest {

    @get:Rule
    val compose = createComposeRule()

    private var email by mutableStateOf("")
    private var width by mutableStateOf(300.dp)
    private var lineHeight = 0

    private fun show(email: String, width: Dp) {
        this.email = email
        this.width = width
        compose.setContent {
            GiftiaryTheme {
                val measurer = rememberTextMeasurer()
                lineHeight = measurer.measure("bob@example.com", GiftiaryType.rowTitle).size.height
                EmailAddress(email = this.email, style = GiftiaryType.rowTitle, color = Color.Black, modifier = Modifier.width(this.width))
            }
        }
    }

    private fun lines(email: String): Int {
        val node = compose.onNodeWithText(email).fetchSemanticsNode()
        return (node.size.height.toFloat() / lineHeight).roundToInt()
    }

    @Test
    fun `an address that fits stays on one line`() {
        show("bob@example.com", 300.dp)

        assertEquals(1, lines("bob@example.com"))
    }

    @Test
    fun `an address too wide moves its domain, whole, to a line of its own`() {
        val long = "bartholomew.longname.family@example.com"
        show(long, 200.dp)

        // One ellipsized line would have cut the domain instead.
        assertEquals(2, lines(long))
    }

    @Test
    fun `it is read out as one address, never as two halves`() {
        val long = "bartholomew.longname.family@example.com"
        show(long, 120.dp)

        compose.onNodeWithText(long).assertExists()
        compose.onAllNodesWithText("@example.com").assertCountEquals(0)
        compose.onAllNodesWithText("bartholomew", substring = true).assertCountEquals(1)
    }
}
