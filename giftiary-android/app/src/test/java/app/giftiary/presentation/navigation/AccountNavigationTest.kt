package app.giftiary.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.AccountDeletionState
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Home → Profile → Settings and back, Settings opened from the rail, and what the auth gate shows
 * in each account state.
 */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private fun showNavHost(widthClass: WidthClass) {
        compose.setContent {
            CompositionLocalProvider(LocalWidthClass provides widthClass) {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = HomeRoute) {
                    composable<HomeRoute> { Text("Home") }
                    composable<ProfileRoute> { Text("Profile") }
                    composable<SettingsRoute> { entry ->
                        val back = navController.settingsBack(entry)
                        if (back != null) GiftiaryTextAction(text = "Settings back", onClick = back) else Text("Settings, no back")
                    }
                }
            }
        }
    }

    private fun stack(): List<KClass<*>> = compose.runOnIdle {
        val routes = listOf(HomeRoute::class, ProfileRoute::class, SettingsRoute::class)
        @Suppress("UNCHECKED_CAST")
        navController.currentBackStack.value
            .mapNotNull { entry -> routes.firstOrNull { entry.destination.hasRoute(it as KClass<Any>) } }
    }

    private fun navigate(vararg routes: Any) = compose.runOnIdle { routes.forEach { navController.navigate(it) } }

    @Test
    fun `on a phone, Back goes from Settings to Profile and from Profile to Home`() {
        showNavHost(WidthClass.COMPACT)
        navigate(ProfileRoute, SettingsRoute)

        compose.onNodeWithText("Settings back").performClick()
        assertEquals(listOf(HomeRoute::class, ProfileRoute::class), stack())
        compose.runOnIdle { navController.popBackStack() }
        assertEquals(listOf(HomeRoute::class), stack())
    }

    @Test
    fun `beside the rail, Settings opened from Profile still leads back to it`() {
        showNavHost(WidthClass.EXPANDED)
        navigate(ProfileRoute, SettingsRoute)

        compose.onNodeWithText("Settings back").performClick()
        assertEquals(listOf(HomeRoute::class, ProfileRoute::class), stack())
    }

    @Test
    fun `Settings opened from the rail is a section of its own, with nothing to go back to`() {
        showNavHost(WidthClass.EXPANDED)

        compose.runOnIdle { navController.navigateToSection(AppSection.SETTINGS) }

        compose.onNodeWithText("Settings, no back").assertExists()
    }

    @Test
    fun `a Settings reached on a phone without Profile still goes back safely`() {
        showNavHost(WidthClass.COMPACT)
        navigate(SettingsRoute)

        compose.onNodeWithText("Settings back").performClick()
        assertEquals(listOf(HomeRoute::class), stack())
    }

    // ---- the gate ----

    private val anna = AuthUser(UUID.randomUUID(), "anna@example.com")

    private fun gate(
        session: OwnerSessionState = OwnerSessionState.SignedOut,
        recovery: RecoveryState = RecoveryState.Idle,
        passwordChanged: Boolean = false,
        deletion: AccountDeletionState = AccountDeletionState.Idle,
        showOnboarding: Boolean = false,
    ) = gateScreen(session, recovery, passwordChanged, deletion, showOnboarding)

    @Test
    fun `only an open account gets the app graph, signed out it is sign-in or the introduction`() {
        assertEquals(GateScreen.App(anna), gate(OwnerSessionState.Ready(anna), showOnboarding = true))
        assertEquals(GateScreen.SignIn, gate(OwnerSessionState.SignedOut))
        assertEquals(GateScreen.Onboarding, gate(OwnerSessionState.SignedOut, showOnboarding = true))
        assertEquals(GateScreen.Startup, gate(OwnerSessionState.Restoring, showOnboarding = true))
    }

    @Test
    fun `a recovery link and its result come before the introduction and any account`() {
        assertEquals(GateScreen.Recovery, gate(recovery = RecoveryState.Checking, showOnboarding = true))
        assertEquals(GateScreen.Recovery, gate(OwnerSessionState.Ready(anna), recovery = RecoveryState.ChoosingPassword()))
        assertEquals(GateScreen.PasswordChanged, gate(OwnerSessionState.Ready(anna), passwordChanged = true))
    }

    @Test
    fun `a deleted account covers the app until acknowledged, then sign-in follows`() {
        val ready = OwnerSessionState.Ready(anna)
        assertEquals(GateScreen.FinishingDeletion(null), gate(ready, deletion = AccountDeletionState.Finishing))
        assertEquals(
            GateScreen.FinishingDeletion(AccountDeletionFailure.NETWORK),
            gate(ready, deletion = AccountDeletionState.FinishFailed(AccountDeletionFailure.NETWORK)),
        )
        assertEquals(GateScreen.AccountDeleted, gate(OwnerSessionState.SignedOut, deletion = AccountDeletionState.Deleted))
        assertEquals(GateScreen.SignIn, gate(OwnerSessionState.SignedOut))
    }
}
