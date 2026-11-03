package app.giftiary.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.giftiary.domain.model.InviteToken
import app.giftiary.presentation.sharing.JoinPhase
import app.giftiary.presentation.sharing.JoinWishlistViewModel
import app.giftiary.presentation.sharing.PendingJoin
import app.giftiary.presentation.sharing.PendingJoinViewModel
import app.giftiary.presentation.sharing.PendingWishlistJoin
import app.giftiary.testing.FakeWishlistSharingRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An opened invite link ends up joining on exactly one join screen, wherever the app was. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JoinLinkNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val sharing = FakeWishlistSharingRepository().apply { gate = CompletableDeferred() }
    private val pending = PendingWishlistJoin()
    private val token = InviteToken.of("A".repeat(43))!!
    private lateinit var navController: NavHostController
    private val joinScreens = mutableListOf<JoinWishlistViewModel>()

    private fun showNavHost(start: Any) {
        val pendingJoin = PendingJoinViewModel(pending)
        compose.setContent {
            navController = rememberNavController()
            val joinPending by pendingJoin.isPending.collectAsStateWithLifecycle()
            OpenJoinScreenForLink(navController, joinPending)
            NavHost(navController = navController, startDestination = start) {
                composable<HomeRoute> { Text("Home") }
                composable<JoinWishlistRoute> {
                    val joinScreen = viewModel { JoinWishlistViewModel(sharing, pending) }
                    if (joinScreen !in joinScreens) joinScreens += joinScreen
                    Text("Join")
                }
            }
        }
    }

    private fun assertJoiningOnOneScreen() = compose.runOnIdle {
        assertEquals(listOf(token), sharing.redeemedTokens)
        assertEquals("one join screen", 1, joinScreens.size)
        assertEquals(JoinPhase.Joining, joinScreens.single().state.value.phase)
        assertEquals(1, navController.currentBackStack.value.count { it.destination.hasRoute<JoinWishlistRoute>() })
    }

    @Test
    fun `a link opened elsewhere in the app opens the join screen, which joins`() {
        showNavHost(start = HomeRoute)

        compose.runOnIdle { pending.offer(PendingJoin.Invite(token)) }

        assertJoiningOnOneScreen()
    }

    @Test
    fun `a link opened over a join screen restored with the app joins there, not on an empty one`() {
        // The process was killed with the join screen open; the link arrives as the app comes back.
        pending.offer(PendingJoin.Invite(token))

        showNavHost(start = JoinWishlistRoute)

        assertJoiningOnOneScreen()
    }

    @Test
    fun `a link opened while the join screen is open joins there`() {
        showNavHost(start = JoinWishlistRoute)

        compose.runOnIdle { pending.offer(PendingJoin.Invite(token)) }

        assertJoiningOnOneScreen()
    }
}
