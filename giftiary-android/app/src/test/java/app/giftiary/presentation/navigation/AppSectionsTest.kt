package app.giftiary.presentation.navigation

import app.giftiary.presentation.components.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.JarURLConnection

class AppSectionsTest {

    // The route classes compiled into the navigation package, found on disk so that a route added
    // later without a section fails here instead of leaving the rail without a selection.
    private val compiledRoutes: Set<String> by lazy {
        val packagePath = "app/giftiary/presentation/navigation/"
        val home = javaClass.classLoader!!.getResource(packagePath + "HomeRoute.class")!!
        // App classes come from a directory or, depending on the build, from a jar.
        val names = if (home.protocol == "jar") {
            val jar = (home.openConnection() as JarURLConnection).jarFile
            jar.entries().asSequence().map { it.name }
                .filter { it.startsWith(packagePath) && '/' !in it.removePrefix(packagePath) }
                .map { it.removePrefix(packagePath) }
                .toList()
        } else {
            File(home.toURI()).parentFile.listFiles()!!.map { it.name }
        }
        names.filter { it.endsWith("Route.class") && '$' !in it }.map { it.removeSuffix(".class") }.toSet()
    }

    private val authRoutes = setOf("SignInRoute", "CreateAccountRoute", "CheckEmailRoute", "ForgotPasswordRoute")

    @Test
    fun `every signed-in route belongs to a section, and sign-in routes to none`() {
        val mapped = routeSections.keys.map { it.simpleName!! }.toSet()

        assertTrue("route list looks incomplete: $compiledRoutes", compiledRoutes.size > 30)
        assertEquals(compiledRoutes - authRoutes, mapped)
    }

    @Test
    fun `each section's root route selects that section`() {
        AppSection.entries.forEach { section ->
            assertEquals(section, routeSections.getValue(section.root::class))
        }
    }

    @Test
    fun `details, editors, occasions and sharing select their parent section`() {
        assertEquals(AppSection.PEOPLE, routeSections[PersonDetailRoute::class])
        assertEquals(AppSection.PEOPLE, routeSections[EditPersonRoute::class])
        assertEquals(AppSection.GIFTS, routeSections[AddGiftRoute::class])
        assertEquals(AppSection.GIFTS, routeSections[MarkGiftIdeaGivenRoute::class])
        assertEquals(AppSection.HOME, routeSections[OccasionPlanRoute::class])
        assertEquals(AppSection.HOME, routeSections[AddOccasionRoute::class])
        assertEquals(AppSection.WISHLISTS, routeSections[SharedWishlistRoute::class])
        assertEquals(AppSection.WISHLISTS, routeSections[JoinWishlistRoute::class])
    }

    @Test
    fun `every signed-in route either shows the phone navigation unit or is a task, account or system screen`() {
        val hidden = setOf(
            AddPersonRoute::class, EditPersonRoute::class,
            AddOccasionRoute::class, EditOccasionRoute::class,
            AddGiftRoute::class, EditGiftRoute::class,
            AddGiftIdeaRoute::class, EditGiftIdeaRoute::class,
            AddWishlistRoute::class, EditWishlistRoute::class, AddWishRoute::class, EditWishRoute::class,
            WishlistAccessRoute::class, JoinWishlistRoute::class,
            SyncAttentionRoute::class, SettingsRoute::class,
        )
        val shown = routesWithNavigationBar

        assertEquals("a route is both shown and hidden", emptySet<Any>(), shown intersect hidden)
        assertEquals(compiledRoutes - authRoutes, (shown + hidden).map { it.simpleName!! }.toSet())
    }

    @Test
    fun `the four roots and the screens browsed from them show the unit, Person detail and Mark as given included`() {
        listOf(
            HomeRoute::class, PeopleRoute::class, GiftHistoryRoute::class, WishlistsRoute::class,
            PersonDetailRoute::class, GiftDetailRoute::class, GiftIdeaDetailRoute::class,
            MarkGiftIdeaGivenRoute::class, WishlistDetailRoute::class, SharedWishlistRoute::class,
        ).forEach { assertTrue("$it shows the unit", it in routesWithNavigationBar) }
        // With the Gifts tab selected, as drawn.
        assertEquals(AppSection.GIFTS, routeSections[MarkGiftIdeaGivenRoute::class])
    }

    @Test
    fun `a section is opened single-top, keeping and restoring the other sections' stacks`() {
        val options = sectionNavOptions(startDestinationId = 42)

        assertEquals(42, options.popUpToId)
        assertFalse("the start destination stays", options.isPopUpToInclusive())
        assertTrue(options.shouldPopUpToSaveState())
        assertTrue(options.shouldLaunchSingleTop())
        assertTrue(options.shouldRestoreState())
    }

    @Test
    fun `only compact windows go without the rail`() {
        assertFalse(WidthClass.COMPACT.hasNavigationRail)
        assertTrue(WidthClass.MEDIUM.hasNavigationRail)
        assertTrue(WidthClass.EXPANDED.hasNavigationRail)
    }
}
