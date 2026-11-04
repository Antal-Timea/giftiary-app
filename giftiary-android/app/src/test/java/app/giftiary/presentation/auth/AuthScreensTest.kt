package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.data.auth.ProviderSignInState
import app.giftiary.data.auth.RecoveryFailure
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.lineCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The auth screens in the Sign in board's order, with the same actions and answers as before. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1600dp")
class AuthScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private var credentials by mutableStateOf(CredentialsUiState())
    private var providerState by mutableStateOf<ProviderSignInState>(ProviderSignInState.Idle)

    private fun showSignIn(providers: List<AuthProvider> = listOf(AuthProvider.GOOGLE), joinPending: Boolean = false) {
        compose.setContent {
            GiftiaryTheme {
                SignInContent(
                    state = credentials,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = { events += "sign in" },
                    onCreateAccount = { events += "create account" },
                    onForgotPassword = { events += "forgot" },
                    providers = providers,
                    providerState = providerState,
                    onProvider = { events += "provider $it" },
                    joinPending = joinPending,
                )
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    @Test
    fun `sign-in reads as the board, the provider first, and every action reaches its flow`() {
        showSignIn()

        val order = listOf(
            "GIFTIARY", "Welcome to Giftiary", "Continue with Google", "or", "Email", "Password", "Forgot password?", "Sign in",
            "New to Giftiary?",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)

        compose.onNodeWithText("Continue with Google").performClick()
        compose.onNodeWithText("Forgot password?").performClick()
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Create account").performClick()
        assertEquals(listOf("provider GOOGLE", "forgot", "sign in", "create account"), events)
        compose.onNodeWithText("Continue with Facebook").assertDoesNotExist()
    }

    @Test
    fun `Facebook is offered only when this build enables it, the same way as Google`() {
        showSignIn(providers = AuthProvider.entries)

        assertTrue(top("Continue with Google") < top("Continue with Facebook"))
        assertTrue(top("Continue with Facebook") < top("or"))
        compose.onNodeWithText("Continue with Facebook").performClick()
        assertEquals(listOf("provider FACEBOOK"), events)
    }

    @Test
    fun `without providers there is no divider, only the email form`() {
        showSignIn(providers = emptyList())

        compose.onNodeWithText("or").assertDoesNotExist()
        compose.onNodeWithText("Email").assertExists()
    }

    @Test
    fun `while signing in, the other ways wait, and a failure is said`() {
        credentials = CredentialsUiState(email = "anna@example.com", isSubmitting = true)
        showSignIn()

        compose.onNodeWithText("Forgot password?").assertIsNotEnabled()
        compose.onNodeWithText("Create account").assertIsNotEnabled()
        compose.onNodeWithText("Continue with Google").assertIsNotEnabled()

        credentials = CredentialsUiState(email = "anna@example.com", authError = AuthError.INVALID_CREDENTIALS)
        compose.onNodeWithText("The email or password is incorrect.").assertExists()
    }

    @Test
    fun `a provider sign-in under way or failed is said under its buttons`() {
        providerState = ProviderSignInState.Exchanging
        showSignIn()
        compose.onNodeWithText("Signing you in…").assertExists()

        providerState = ProviderSignInState.Failed(AuthError.CANCELLED)
        compose.onNodeWithText("Sign-in was cancelled.").assertExists()
    }

    @Test
    fun `an invite waiting for sign-in is still said`() {
        showSignIn(joinPending = true)

        compose.onNodeWithText("Sign in to open the wishlist you were invited to.").assertExists()
    }

    @Test
    fun `create account keeps back, providers, the repeated password and its submit`() {
        compose.setContent {
            GiftiaryTheme {
                CreateAccountContent(
                    state = CreateAccountUiState(),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onPasswordConfirmationChange = {},
                    onSubmit = { events += "create" },
                    onBack = { events += "back" },
                    providers = listOf(AuthProvider.GOOGLE),
                    providerState = ProviderSignInState.Idle,
                    onProvider = { events += "provider $it" },
                )
            }
        }

        val order = listOf("Continue with Google", "Email", "Password", "Confirm password")
        val tops = order.map(::top)
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithContentDescription("Back").performClick()
        // The title and, below the form, its submit.
        compose.onAllNodesWithText("Create account")[1].performClick()
        assertEquals(listOf("back", "create"), events)
    }

    @Test
    fun `check email names the address and leads back to sign-in`() {
        compose.setContent { GiftiaryTheme { CheckEmailScreen(email = "anna@example.com", onBackToSignIn = { events += "sign in" }) } }

        compose.onNodeWithText("Check your email").assertExists()
        compose.onNodeWithText("anna@example.com", substring = true).assertExists()
        compose.onNodeWithText("Back to sign in").performClick()
        assertEquals(listOf("sign in"), events)
    }

    @Test
    fun `forgot password asks for the email, then says where the link went`() {
        var state by mutableStateOf(ForgotPasswordUiState(email = "anna@example.com"))
        compose.setContent {
            GiftiaryTheme { ForgotPasswordContent(state, {}, { events += "send" }, { events += "back" }) }
        }

        compose.onNodeWithText("Reset your password").assertExists()
        compose.onNodeWithText("Send link").performClick()
        state = state.copy(sent = true)
        compose.onNodeWithText("If there is an account for anna@example.com", substring = true).assertExists()
        compose.onNodeWithText("Back to sign in").performClick()
        assertEquals(listOf("send", "back"), events)
    }

    @Test
    fun `recovery keeps the new password form, its cancel, and each failure's way out`() {
        var state by mutableStateOf<RecoveryState>(RecoveryState.ChoosingPassword())
        compose.setContent {
            GiftiaryTheme {
                RecoveryContent(
                    state = state,
                    input = NewPasswordInput(),
                    actions = RecoveryActions({}, {}, { events += "save" }, { events += "retry" }, { events += "leave" }, { events += "sign out" }),
                )
            }
        }

        compose.onNodeWithText("Choose a new password").assertExists()
        compose.onNodeWithText("Save new password").performClick()
        compose.onNodeWithText("Cancel").performClick()
        state = RecoveryState.Failed(RecoveryFailure.NETWORK)
        compose.onNodeWithText("Try again").performClick()
        state = RecoveryState.Failed(RecoveryFailure.SIGNED_IN)
        compose.onNodeWithText("Sign out").performClick()

        assertEquals(listOf("save", "leave", "retry", "sign out"), events)
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the sign-in words stay clear of the corner gift, at large text too`() {
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                GiftiaryTheme {
                    SignInContent(CredentialsUiState(), {}, {}, {}, {}, {}, AuthProvider.entries, ProviderSignInState.Idle, {})
                }
            }
        }
        listOf(1f, 2f).forEach { scale ->
            fontScale = scale
            compose.waitForIdle()
            assertClearOfArt("Welcome to Giftiary", "Keep your gift memories, plans and wishlists together.")
        }
    }

    @Test
    @Config(qualifiers = "hu-w360dp-h1600dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian at large text the brand stays whole and the title clear of the corner gift`() {
        var fontScale by mutableFloatStateOf(1f)
        var width by mutableStateOf(360.dp)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                GiftiaryTheme {
                    Box(Modifier.width(width)) {
                        SignInContent(CredentialsUiState(), {}, {}, {}, {}, {}, AuthProvider.entries, ProviderSignInState.Idle, {})
                    }
                }
            }
        }
        listOf(360.dp, 390.dp).forEach { w ->
            listOf(1f, 1.3f, 2f).forEach { scale ->
                width = w
                fontScale = scale
                compose.waitForIdle()
                val title = "Üdv a Giftiaryben"
                val results = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
                    .config[SemanticsActions.GetTextLayoutResult].action!!(results)
                val layout = results.single()
                val lines = (0 until layout.lineCount).map { title.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim() }
                assertTrue("$w at ${scale}x: $lines", lines.any { it.endsWith("Giftiaryben") })
                assertClearOfArt(title, right = w)
            }
        }
    }

    /**
     * The large cut-out shows 60% of its 240dp from the screen's end edge and reaches about 210dp
     * down: each text either ends before it or starts below it.
     */
    private fun assertClearOfArt(vararg texts: String, right: Dp = compose.onRoot().getBoundsInRoot().right) {
        val artStart = right - 144.dp
        texts.forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it ends at ${bounds.right} before $artStart or starts below the art", bounds.right <= artStart || bounds.top >= 210.dp)
        }
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h1600dp")
    // Real text measurement, so a squeezed or clipped word shows.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `Hungarian sign-in at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme {
                    SignInContent(CredentialsUiState(), {}, {}, {}, {}, {}, AuthProvider.entries, ProviderSignInState.Idle, {})
                }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Üdv a Giftiaryben", "Új vagy a Giftiaryben?", "Fiók létrehozása").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.left} >= 0, ${bounds.right} <= $width", bounds.left >= 0.dp && bounds.right <= width)
        }
        // The link moves under the question rather than being squeezed beside it.
        assertEquals(1, compose.onNodeWithText("Fiók létrehozása", useUnmergedTree = true).lineCount())
        assertEquals(1, compose.onNodeWithText("Új vagy a Giftiaryben?", useUnmergedTree = true).lineCount())
    }
}
