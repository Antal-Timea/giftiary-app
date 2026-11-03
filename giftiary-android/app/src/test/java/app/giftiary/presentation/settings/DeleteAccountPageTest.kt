package app.giftiary.presentation.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.giftiary.domain.repository.AccountDeletionFailure
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
import org.robolectric.annotation.GraphicsMode

/** Deleting the account from Settings: asked on its own page, one destructive action, Cancel until it runs. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class DeleteAccountPageTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()
    private var settings by mutableStateOf(SettingsUiState())
    private var shown = false

    private fun show(state: SettingsUiState, fontScale: Float = 1f) {
        settings = state
        if (shown) return
        shown = true
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiftiaryTheme {
                    SettingsContent(
                        state = settings,
                        email = "anna@example.com",
                        reminderState = ReminderCardState.Off,
                        reminderActions = ReminderCardActions({}, {}, {}, {}),
                        actions = SettingsActions(
                            onBack = { events += "settings back" },
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

    private fun deleteButton() = compose.onNode(hasText("Delete account") and hasClickAction())

    private fun systemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `the question replaces Settings, saying what goes`() {
        show(SettingsUiState(deleteDialog = DeleteAccountDialog()))

        compose.onNode(hasText("Delete your account?") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithText("deleted for good", substring = true).assertExists()
        compose.onNodeWithText("This can’t be undone.", substring = true).assertExists()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
        compose.onAllNodesWithText("anna@example.com", substring = true).assertCountEquals(0)
    }

    @Test
    fun `Cancel, the back circle and system back each cancel without deleting`() {
        show(SettingsUiState(deleteDialog = DeleteAccountDialog()))

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        systemBack()

        assertEquals(listOf("dismiss", "dismiss", "dismiss"), events)
    }

    @Test
    fun `Delete account asks to delete once`() {
        show(SettingsUiState(deleteDialog = DeleteAccountDialog()))

        deleteButton().assertIsEnabled().performClick()

        assertEquals(listOf("confirm"), events)
    }

    @Test
    fun `while deleting there is no second delete and no way to cancel`() {
        show(SettingsUiState(deleteDialog = DeleteAccountDialog(deleting = true)))

        deleteButton().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        // Back still reaches the view model, which ignores it while the deletion runs.
        systemBack()
        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun `a failure is said, and deleting again or cancelling are both offered`() {
        show(SettingsUiState(deleteDialog = DeleteAccountDialog(failure = AccountDeletionFailure.NETWORK)))

        compose.onNodeWithText("Couldn’t reach Giftiary. Check your connection and try again.").assertExists()
        deleteButton().assertIsEnabled().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("confirm", "dismiss"), events)

        show(SettingsUiState(deleteDialog = DeleteAccountDialog(failure = AccountDeletionFailure.SERVER)))
        compose.onNodeWithText("Something went wrong. Please try again.").assertExists()
    }

    @Test
    fun `closing the question shows Settings again`() {
        show(SettingsUiState(deleteDialog = DeleteAccountDialog()))
        show(SettingsUiState())

        compose.onNodeWithText("Delete your account?").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertExists()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h2000dp")
    fun `in Hungarian at large text the page stays inside the screen`() {
        show(SettingsUiState(deleteDialog = DeleteAccountDialog(failure = AccountDeletionFailure.NETWORK)), fontScale = 2f)

        val width = compose.onRoot().getBoundsInRoot().right
        listOf(
            compose.onNodeWithText("Törlöd a fiókodat?"),
            compose.onNodeWithText("Ez nem vonható vissza.", substring = true),
            compose.onNodeWithText("Nem sikerült elérni a Giftiaryt", substring = true),
            compose.onNode(hasText("Fiók törlése") and hasClickAction()),
            compose.onNode(hasText("Mégse") and hasClickAction()),
        ).forEach { node ->
            val bounds = node.getBoundsInRoot()
            assertTrue("$bounds outside the screen", bounds.left >= 0.dp && bounds.right <= width)
            assertTrue("$bounds lower than a target", bounds.bottom - bounds.top >= 20.dp)
        }
        val delete = compose.onNode(hasText("Fiók törlése") and hasClickAction()).getBoundsInRoot()
        assertTrue(delete.bottom - delete.top >= 48.dp)
    }
}
