package app.giftiary.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.reflect.KClass

/** Where Back leads once an idea is marked as given, from its own screen or from beside the history. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GivenGiftNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val ideaId = UUID.randomUUID().toString()
    private val giftId = UUID.randomUUID()
    private lateinit var navController: NavHostController

    private fun showNavHost() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("Home") }
                composable<GiftHistoryRoute> { Text("Gifts") }
                composable<GiftIdeaDetailRoute> { Text("Idea") }
                composable<MarkGiftIdeaGivenRoute> { Text("Mark as given") }
                composable<GiftDetailRoute> { Text("Gift") }
            }
        }
    }

    private fun stack(): List<KClass<*>> = compose.runOnIdle {
        val routes = listOf(HomeRoute::class, GiftHistoryRoute::class, GiftIdeaDetailRoute::class, MarkGiftIdeaGivenRoute::class, GiftDetailRoute::class)
        @Suppress("UNCHECKED_CAST")
        navController.currentBackStack.value
            .mapNotNull { entry -> routes.firstOrNull { entry.destination.hasRoute(it as KClass<Any>) } }
    }

    private fun navigate(vararg routes: Any) = compose.runOnIdle { routes.forEach { navController.navigate(it) } }

    @Test
    fun `from the idea's own screen, the idea goes too, as before`() {
        showNavHost()
        navigate(GiftHistoryRoute, GiftIdeaDetailRoute(ideaId), MarkGiftIdeaGivenRoute(ideaId))

        compose.runOnIdle { navController.openGivenGift(giftId) }

        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class, GiftDetailRoute::class), stack())
        compose.runOnIdle { navController.popBackStack() }
        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class), stack())
    }

    @Test
    fun `from beside the history, only the marking goes and Back returns to the history`() {
        showNavHost()
        navigate(GiftHistoryRoute, MarkGiftIdeaGivenRoute(ideaId))

        compose.runOnIdle { navController.openGivenGift(giftId) }

        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class, GiftDetailRoute::class), stack())
        compose.runOnIdle { navController.popBackStack() }
        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class), stack())
    }
}
