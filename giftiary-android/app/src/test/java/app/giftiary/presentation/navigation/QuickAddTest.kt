package app.giftiary.presentation.navigation

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.Locale
import java.util.UUID
import kotlin.reflect.KClass

/** The Global Quick Add sheet: its seven entries, and the existing flows they open. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private val titles = listOf("Gift I gave", "Gift I received", "Gift idea", "Wishlist item", "Person", "List", "Occasion")

    @Test
    fun `the sheet offers the seven drawn entries in order, as buttons`() {
        val chosen = mutableListOf<QuickAddAction>()
        compose.setContent {
            GiftiaryTheme { QuickAddContent(onAction = { chosen += it }) }
        }

        titles.forEach { title ->
            compose.onNodeWithText(title).performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
        }

        assertEquals(QuickAddAction.entries, chosen)
    }

    @Test
    fun `Occasion closes Organize, after Person and List`() {
        compose.setContent {
            GiftiaryTheme { QuickAddContent(onAction = {}) }
        }

        compose.onNodeWithText("Add an important date or event").performScrollTo().assertExists()
        assertEquals(
            listOf(QuickAddAction.PERSON, QuickAddAction.LIST, QuickAddAction.OCCASION),
            QuickAddAction.entries.filter { it.group == QuickAddGroup.ORGANIZE },
        )
    }

    @Test
    fun `the occasion entry is named in both languages`() {
        val en = context(Locale.ENGLISH)
        assertEquals("Occasion", en.getString(R.string.quick_add_occasion))
        assertEquals("Add an important date or event", en.getString(R.string.quick_add_occasion_description))

        val hu = context(Locale.forLanguageTag("hu"))
        assertEquals("Alkalom", hu.getString(R.string.quick_add_occasion))
        assertEquals("Adj hozzá egy fontos dátumot vagy eseményt", hu.getString(R.string.quick_add_occasion_description))
    }

    @Test
    fun `an entry closes the sheet and Back dismisses it without choosing`() {
        var open by mutableStateOf(true)
        val chosen = mutableListOf<QuickAddAction>()
        compose.setContent {
            GiftiaryTheme {
                Text("Home")
                if (open) {
                    QuickAddSheet(onDismiss = { open = false }, onAction = { open = false; chosen += it })
                }
            }
        }

        compose.onNodeWithText("Add something").assertExists()
        // The sheet is a dialog window of its own; Back goes to it.
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
        assertFalse(open)
        compose.onNodeWithText("Add something").assertDoesNotExist()

        compose.runOnIdle { open = true }
        compose.onNodeWithText("Person").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(open)
        assertEquals(listOf(QuickAddAction.PERSON), chosen)
    }

    @Test
    fun `gift entries preselect their direction, and the others open their add flows`() {
        assertEquals(AddGiftRoute(direction = "GIVEN"), quickAddRoute(QuickAddAction.GIFT_GIVEN, null))
        assertEquals(AddGiftRoute(direction = "RECEIVED"), quickAddRoute(QuickAddAction.GIFT_RECEIVED, null))
        assertEquals(AddGiftIdeaRoute(), quickAddRoute(QuickAddAction.GIFT_IDEA, null))
        assertEquals(AddPersonRoute(openWhenSaved = true), quickAddRoute(QuickAddAction.PERSON, null))
        assertEquals(AddWishlistRoute(), quickAddRoute(QuickAddAction.LIST, null))
        // The user's own occasion, the same editor Your occasions opens.
        assertEquals(AddOccasionRoute(personId = null), quickAddRoute(QuickAddAction.OCCASION, null))
        assertEquals(AddOccasionRoute(personId = null), quickAddRoute(QuickAddAction.OCCASION, UUID.randomUUID().toString()))
    }

    @Test
    fun `an occasion opens its editor over the current screen, so Back returns there`() {
        showNavHost()
        compose.runOnIdle { navController.navigateToSection(AppSection.GIFTS) }

        compose.runOnIdle { navController.openQuickAdd(QuickAddAction.OCCASION) }

        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class, AddOccasionRoute::class), stack())
        compose.runOnIdle { navController.popBackStack() }
        assertEquals(listOf(HomeRoute::class, GiftHistoryRoute::class), stack())
    }

    @Test
    fun `an entry opens its flow over the current screen, so Back returns there`() {
        showNavHost()
        compose.runOnIdle { navController.navigateToSection(AppSection.PEOPLE) }

        compose.runOnIdle { navController.openQuickAdd(QuickAddAction.GIFT_RECEIVED) }

        assertEquals(listOf(HomeRoute::class, PeopleRoute::class, AddGiftRoute::class), stack())
        assertEquals("RECEIVED", compose.runOnIdle { navController.currentBackStackEntry!!.toRoute<AddGiftRoute>().direction })
        compose.runOnIdle { navController.popBackStack() }
        assertEquals(listOf(HomeRoute::class, PeopleRoute::class), stack())
    }

    @Test
    fun `a wish goes into the wishlist open behind the sheet`() {
        val wishlistId = UUID.randomUUID().toString()
        showNavHost()
        compose.runOnIdle {
            navController.navigateToSection(AppSection.WISHLISTS)
            navController.navigate(WishlistDetailRoute(wishlistId))
        }

        compose.runOnIdle { navController.openQuickAdd(QuickAddAction.WISH) }

        assertEquals(wishlistId, compose.runOnIdle { navController.currentBackStackEntry!!.toRoute<AddWishRoute>().wishlistId })
    }

    @Test
    fun `without an open wishlist, a wish opens the Lists section to choose one`() {
        showNavHost()
        compose.runOnIdle { navController.navigateToSection(AppSection.GIFTS) }

        compose.runOnIdle { navController.openQuickAdd(QuickAddAction.WISH) }

        assertEquals(listOf(HomeRoute::class, WishlistsRoute::class), stack())
    }

    @Test
    fun `switching sections never stacks a second copy of one, and Back from a section returns Home`() {
        showNavHost()

        compose.runOnIdle {
            listOf(AppSection.PEOPLE, AppSection.GIFTS, AppSection.PEOPLE, AppSection.PEOPLE, AppSection.WISHLISTS, AppSection.PEOPLE)
                .forEach(navController::navigateToSection)
        }

        assertEquals(listOf(HomeRoute::class, PeopleRoute::class), stack())
        compose.runOnIdle { navController.popBackStack() }
        assertEquals(listOf(HomeRoute::class), stack())
    }

    @Test
    fun `a section keeps the screen it was left on`() {
        val personId = UUID.randomUUID().toString()
        showNavHost()
        compose.runOnIdle {
            navController.navigateToSection(AppSection.PEOPLE)
            navController.navigate(PersonDetailRoute(personId))
            navController.navigateToSection(AppSection.GIFTS)
            navController.navigateToSection(AppSection.PEOPLE)
        }

        assertEquals(listOf(HomeRoute::class, PeopleRoute::class, PersonDetailRoute::class), stack())
    }

    private fun showNavHost() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("Home") }
                composable<PeopleRoute> { Text("People") }
                composable<PersonDetailRoute> { Text("Person") }
                composable<GiftHistoryRoute> { Text("Gifts") }
                composable<WishlistsRoute> { Text("Lists") }
                composable<WishlistDetailRoute> { Text("Wishlist") }
                composable<AddGiftRoute> { Text("Add gift") }
                composable<AddWishRoute> { Text("Add wish") }
                composable<AddOccasionRoute> { Text("Add occasion") }
            }
        }
    }

    private val routes = listOf(
        HomeRoute::class, PeopleRoute::class, PersonDetailRoute::class, GiftHistoryRoute::class, WishlistsRoute::class,
        WishlistDetailRoute::class, AddGiftRoute::class, AddWishRoute::class, AddOccasionRoute::class,
    )

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }

    private fun stack(): List<KClass<*>> = compose.runOnIdle {
        @Suppress("UNCHECKED_CAST")
        navController.currentBackStack.value
            .mapNotNull { entry -> routes.firstOrNull { entry.destination.hasRoute(it as KClass<Any>) } }
    }
}
