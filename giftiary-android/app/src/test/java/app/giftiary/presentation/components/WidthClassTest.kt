package app.giftiary.presentation.components

import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiaryLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidthClassTest {

    @Test
    fun `phones and narrow windows are compact, up to 599 dp`() {
        listOf(0.dp, 320.dp, 411.dp, 599.dp, 599.9.dp).forEach {
            assertEquals("$it", WidthClass.COMPACT, WidthClass.of(it))
        }
    }

    @Test
    fun `medium starts at 600 dp and ends below 840 dp`() {
        listOf(600.dp, 720.dp, 800.dp, 839.dp, 839.9.dp).forEach {
            assertEquals("$it", WidthClass.MEDIUM, WidthClass.of(it))
        }
    }

    @Test
    fun `expanded starts at 840 dp`() {
        listOf(840.dp, 960.dp, 1280.dp, 2560.dp).forEach {
            assertEquals("$it", WidthClass.EXPANDED, WidthClass.of(it))
        }
    }

    @Test
    fun `the readable cap is wider than any compact window, so phones keep their layout`() {
        assertTrue(GiftiaryLayout.readableMaxWidth >= 600.dp)
    }
}
