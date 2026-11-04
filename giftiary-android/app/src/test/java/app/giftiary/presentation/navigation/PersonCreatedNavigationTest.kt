package app.giftiary.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.reflect.KClass

/** A person just created opens on their own page, where their occasions are added. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonCreatedNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private val routes = listOf(HomeRoute::class, PeopleRoute::class, AddPersonRoute::class, PersonDetailRoute::class)

    private fun showNavHost() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("Home") }
                composable<PeopleRoute> { Text("People") }
                composable<AddPersonRoute> { Text("Add person") }
                composable<PersonDetailRoute> { Text("Person") }
            }
        }
    }

    private fun stack(): List<KClass<*>> = compose.runOnIdle {
        @Suppress("UNCHECKED_CAST")
        navController.currentBackStack.value
            .mapNotNull { entry -> routes.firstOrNull { entry.destination.hasRoute(it as KClass<Any>) } }
    }

    @Test
    fun `a person added from People opens on their page, and Back returns to People`() {
        showNavHost()
        val anna = UUID.randomUUID()
        compose.runOnIdle {
            navController.navigate(PeopleRoute)
            navController.navigate(AddPersonRoute())
        }

        compose.runOnIdle { navController.openCreatedPerson(anna) }

        assertEquals(listOf(HomeRoute::class, PeopleRoute::class, PersonDetailRoute::class), stack())
        compose.runOnIdle { assertEquals(anna.toString(), navController.currentBackStackEntry!!.toRoute<PersonDetailRoute>().personId) }
    }

    @Test
    fun `a person added from Quick Add on Home opens over Home`() {
        showNavHost()
        compose.runOnIdle { navController.navigate(quickAddRoute(QuickAddAction.PERSON, null)) }

        compose.runOnIdle { navController.openCreatedPerson(UUID.randomUUID()) }

        assertEquals(listOf(HomeRoute::class, PersonDetailRoute::class), stack())
    }
}
