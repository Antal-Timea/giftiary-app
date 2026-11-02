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

/** Where an editor leads once what it edits is deleted: never back to the deleted entry's page. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletedEditorNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val id = UUID.randomUUID().toString()
    private lateinit var navController: NavHostController

    private val routes = listOf(
        HomeRoute::class, PeopleRoute::class, PersonDetailRoute::class, EditPersonRoute::class,
        GiftHistoryRoute::class, GiftDetailRoute::class, EditGiftRoute::class,
        GiftIdeaDetailRoute::class, EditGiftIdeaRoute::class,
        WishlistsRoute::class, WishlistDetailRoute::class, EditWishlistRoute::class, WishDetailRoute::class, EditWishRoute::class,
    )

    private fun showNavHost() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("Home") }
                composable<PeopleRoute> { Text("People") }
                composable<PersonDetailRoute> { Text("Person") }
                composable<EditPersonRoute> { Text("Edit person") }
                composable<GiftHistoryRoute> { Text("Gifts") }
                composable<GiftDetailRoute> { Text("Gift") }
                composable<EditGiftRoute> { Text("Edit gift") }
                composable<GiftIdeaDetailRoute> { Text("Idea") }
                composable<EditGiftIdeaRoute> { Text("Edit idea") }
                composable<WishlistsRoute> { Text("Lists") }
                composable<WishlistDetailRoute> { Text("List") }
                composable<EditWishlistRoute> { Text("Edit list") }
                composable<WishDetailRoute> { Text("Wish") }
                composable<EditWishRoute> { Text("Edit wish") }
            }
        }
    }

    private fun stack(): List<KClass<*>> = compose.runOnIdle {
        @Suppress("UNCHECKED_CAST")
        navController.currentBackStack.value
            .mapNotNull { entry -> routes.firstOrNull { entry.destination.hasRoute(it as KClass<Any>) } }
    }

    private fun navigate(vararg routes: Any) = compose.runOnIdle { routes.forEach { navController.navigate(it) } }

    @Test
    fun `a person deleted from the editor opened on their page leaves both`() {
        showNavHost()
        navigate(PeopleRoute, PersonDetailRoute(id), EditPersonRoute(id))

        compose.runOnIdle { navController.leaveDeletedEditor<PersonDetailRoute>() }

        assertEquals(listOf(HomeRoute::class, PeopleRoute::class), stack())
    }

    @Test
    fun `a person deleted from the editor opened beside the list returns to the list`() {
        showNavHost()
        navigate(PeopleRoute, EditPersonRoute(id))

        compose.runOnIdle { navController.leaveDeletedEditor<PersonDetailRoute>() }

        assertEquals(listOf(HomeRoute::class, PeopleRoute::class), stack())
    }

    @Test
    fun `a deleted gift or idea leaves its editor and its page`() {
        showNavHost()
        navigate(GiftHistoryRoute, GiftDetailRoute(id), EditGiftRoute(id))
        compose.runOnIdle { navController.leaveDeletedEditor<GiftDetailRoute>() }
        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class), stack())

        navigate(GiftIdeaDetailRoute(id), EditGiftIdeaRoute(id))
        compose.runOnIdle { navController.leaveDeletedEditor<GiftIdeaDetailRoute>() }
        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class), stack())
    }

    @Test
    fun `a deleted list leaves its editor and its page`() {
        showNavHost()
        navigate(WishlistsRoute, WishlistDetailRoute(id), EditWishlistRoute(id))

        compose.runOnIdle { navController.leaveDeletedEditor<WishlistDetailRoute>() }

        assertEquals(listOf(HomeRoute::class, WishlistsRoute::class), stack())
    }

    @Test
    fun `a deleted wish leaves its page when edited from there, and only the editor when edited from the list`() {
        showNavHost()
        navigate(WishlistsRoute, WishlistDetailRoute(id), WishDetailRoute(id), EditWishRoute(id))
        compose.runOnIdle { navController.leaveDeletedEditor<WishDetailRoute>() }
        assertEquals(listOf(HomeRoute::class, WishlistsRoute::class, WishlistDetailRoute::class), stack())

        navigate(EditWishRoute(id))
        compose.runOnIdle { navController.leaveDeletedEditor<WishDetailRoute>() }
        assertEquals(listOf(HomeRoute::class, WishlistsRoute::class, WishlistDetailRoute::class), stack())
    }
}
