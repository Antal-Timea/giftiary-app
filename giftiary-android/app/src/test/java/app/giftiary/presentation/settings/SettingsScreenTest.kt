package app.giftiary.presentation.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.BuildConfig
import app.giftiary.presentation.home.ReminderCardActions
import app.giftiary.presentation.home.ReminderCardState
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The board's groups with what Giftiary has: reminders, privacy, version, the account. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2000dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private var reminders by mutableStateOf(ReminderCardState.Off)
    private var settings by mutableStateOf(SettingsUiState())
    private var onBack: (() -> Unit)? by mutableStateOf({ events += "back" })
    private var shown = false

    private val uriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            events += "open $uri"
        }
    }

    private fun show(reminderState: ReminderCardState = reminders) {
        reminders = reminderState
        if (shown) return
        shown = true
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                GiftiaryTheme {
                    SettingsContent(
                        state = settings,
                        email = "anna@example.com",
                        reminderState = reminders,
                        reminderActions = ReminderCardActions(
                            onTurnOn = { events += "on" },
                            onTurnOff = { events += "off" },
                            onAllow = { events += "allow" },
                            onOpenSettings = { events += "system settings" },
                        ),
                        actions = SettingsActions(
                            onBack = onBack,
                            onSignOut = { events += "sign out" },
                            onDeleteAccount = { events += "delete" },
                            onDeleteAccountDismiss = { events += "dismiss" },
                            onDeleteAccountConfirm = { events += "confirm" },
                        ),
                    )
                }
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    @Test
    fun `the groups follow the board, each holding what exists`() {
        show()

        val order = listOf(
            "Settings", "NOTIFICATIONS", "Occasion reminders", "PRIVACY & DATA", "Privacy Policy", "ABOUT", "Version",
            "ACCOUNT", "Sign out", "Delete account",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertExists()
        compose.onNodeWithText("Signed in as anna@example.com").assertExists()
        // The name others see moved to Edit profile; nothing here pretends to more settings.
        listOf("Your name", "Language", "Appearance", "Your data", "Gift planning reminders").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `the reminder switch turns daily reminders on and off`() {
        show()
        compose.onNode(isToggleable()).assertIsOff().performClick()

        show(ReminderCardState.On)
        compose.onNode(isToggleable()).assertIsOn().performClick()

        assertEquals(listOf("on", "off"), events)
    }

    @Test
    fun `reminders Android does not allow say so, with the way to fix it`() {
        show(ReminderCardState.NeedsPermission)
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText("Allow notifications to get reminders.").assertExists()
        compose.onNodeWithText("Allow").performClick()

        show(ReminderCardState.Blocked)
        compose.onNodeWithText("Notifications are turned off for Giftiary.").assertExists()
        compose.onNodeWithText("Allow").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()

        assertEquals(listOf("allow", "system settings"), events)
    }

    @Test
    fun `reminders the account cannot have are not offered`() {
        show(ReminderCardState.Hidden)

        compose.onNodeWithText("NOTIFICATIONS").assertDoesNotExist()
        compose.onNodeWithText("Privacy Policy").assertExists()
    }

    @Test
    fun `privacy, sign out and delete account reach their flows, and delete still asks`() {
        show()

        compose.onNodeWithText("Privacy Policy").performClick()
        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Delete account").performClick()
        settings = SettingsUiState(deleteDialog = DeleteAccountDialog())
        compose.onNodeWithText("Delete your account?").assertExists()

        assertEquals(listOf("open https://giftiary.app/privacy/#en", "sign out", "delete"), events)
    }

    @Test
    fun `the back circle is there only when there is somewhere to go back to`() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)

        onBack = null
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h2000dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme {
                    SettingsContent(
                        SettingsUiState(),
                        "anna@example.com",
                        ReminderCardState.Blocked,
                        ReminderCardActions({}, {}, {}, {}),
                        SettingsActions({}, {}, {}, {}, {}),
                    )
                }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("ADATVÉDELEM ÉS ADATOK", "A Giftiary értesítései ki vannak kapcsolva.", "Fiók törlése", "Beállítások megnyitása").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
