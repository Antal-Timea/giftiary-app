package app.giftiary.presentation.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
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
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Home reminder row: each state's words and exactly its own actions, mapped as before. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class ReminderCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(ReminderCardState.Hidden)
    private var shown = false

    private val actions = ReminderCardActions(
        onTurnOn = { events += "on" },
        onTurnOff = { events += "off" },
        onAllow = { events += "allow" },
        onOpenSettings = { events += "settings" },
    )

    private fun show(state: ReminderCardState, fontScale: Float = 1f) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiftiaryTheme { ReminderCard(state = this.state, actions = actions) }
            }
        }
    }

    private fun press(label: String) = compose.onNode(hasText(label) and hasClickAction()).performClick()

    @Test
    fun `hidden shows nothing at all`() {
        show(ReminderCardState.Hidden)

        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.onAllNodesWithText("reminder", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun `off offers Turn on only`() {
        show(ReminderCardState.Off)

        compose.onNodeWithText("Get a reminder on the day of an occasion and a week before.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        press("Turn on")
        assertEquals(listOf("on"), events)
    }

    @Test
    fun `on offers Turn off only`() {
        show(ReminderCardState.On)

        compose.onNodeWithText("Reminders are on, on the day and a week before.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        press("Turn off")
        assertEquals(listOf("off"), events)
    }

    @Test
    fun `waiting for permission offers Allow and Open settings`() {
        show(ReminderCardState.NeedsPermission)

        compose.onNodeWithText("Allow notifications to get reminders.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        press("Allow")
        press("Open settings")
        assertEquals(listOf("allow", "settings"), events)
    }

    @Test
    fun `blocked offers Open settings only`() {
        show(ReminderCardState.Blocked)

        compose.onNodeWithText("Notifications are turned off for Giftiary.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        press("Open settings")
        assertEquals(listOf("settings"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `in Hungarian at large text both permission actions are reachable and apart`() {
        show(ReminderCardState.NeedsPermission, fontScale = 2f)

        val width = compose.onRoot().getBoundsInRoot().right
        val allow = compose.onNode(hasText("Engedélyezés") and hasClickAction()).getBoundsInRoot()
        val settings = compose.onNode(hasText("Beállítások megnyitása") and hasClickAction()).getBoundsInRoot()
        listOf(allow, settings).forEach {
            assertTrue("$it outside the screen", it.left >= 0.dp && it.right <= width)
            assertTrue(it.bottom - it.top >= 48.dp)
        }
        val overlap = allow.left < settings.right && settings.left < allow.right && allow.top < settings.bottom && settings.top < allow.bottom
        assertTrue("the actions overlap: $allow, $settings", !overlap)
        press("Engedélyezés")
        press("Beállítások megnyitása")
        assertEquals(listOf("allow", "settings"), events)
    }
}
